package mighty.ai.play;

import java.util.*;
import mighty.game.Seat;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.game.trick.PublicPlay;
import mighty.model.*;

/**
 * 공개 행동의 잔여 장수 추론입니다. 확정 무문양과 카드 제거에는 사용하지 않습니다.
 */
public final class TrumpOwnership {

  private final Set<Seat> likelyShort;
  private final View view;
  private final CardKnowledge cards;

  public TrumpOwnership(View view, CardKnowledge cards) {
    this.view = view;
    this.cards = cards;
    Set<Seat> shortSeats = new HashSet<>();
    Set<Card> spent = new HashSet<>();
    for (var trick : view.context().history()) {
      observe(trick.cards(), view, shortSeats, spent);
      trick.cards().forEach(p -> spent.add(p.action().card()));
    }
    observe(view.trick(), view, shortSeats, spent);
    likelyShort = Set.copyOf(shortSeats);
  }

  public boolean likelyShort(Seat seat) {
    return likelyShort.contains(seat);
  }

  public Set<Seat> likelyShortSeats() {
    return likelyShort;
  }

  /**
   * 강제로 나온 점수와 잔여 낮은 기루의 불확실성을 비교하는 운영 신호입니다. 확정 무기루가 아닙니다.
   */
  public boolean avoidExchange(PlayFacts facts) {
    var suit = Arrays.stream(Suit.values()).filter(view.contract().trump()::matches).findFirst();
    if (suit.isEmpty()) {
      return false;
    }
    var live = view.context().playOrder().seats().stream()
        .filter(s -> !facts.teams().isAlly(s))
        .filter(s -> !facts.cards().isVoid(s, suit.get())).toList();
    if (live.isEmpty()) {
      return false;
    }
    Map<Seat, Integer> possibleRemainder = new HashMap<>();
    Set<Card> spent = new HashSet<>();
    Card mighty = Rules.mighty(view.contract().trump());
    for (var trick : view.context().history()) {
      boolean mightyPlayed = trick.cards().stream().anyMatch(p -> p.action().card().equals(mighty));
      for (int index = 0; index < trick.cards().size(); index++) {
        var play = trick.cards().get(index);
        if (live.contains(play.seat()) && play.action().card() instanceof StandardCard card
            && card.suit() == suit.get()) {
          long higher = Arrays.stream(Rank.values())
              .filter(rank -> rank.value() > card.rank().value())
              .map(rank -> new StandardCard(card.suit(), rank))
              .filter(c -> !c.equals(mighty) && !spent.contains(c)).count();
          boolean ruffed = !view.contract().trump()
              .matches(Rules.conditionsFor(view.contract().trump(),
                  trick.number(), trick.cards().get(0).action()).leadSuit());
          if (ruffed && card.points() > 0 && higher == 0 && remainingCount() <= 1) {
            possibleRemainder.put(play.seat(), 0);
          } else if (card.points() == 0) {
            possibleRemainder.remove(play.seat());
          } else if (index > 0 && mightyPlayed && higher <= 1) {
            possibleRemainder.put(play.seat(), (int) higher);
          } else if (possibleRemainder.containsKey(play.seat()) && higher <= 1) {
            possibleRemainder.put(play.seat(), (int) higher);
          } else if (index > 0 && play.seat() != trick.leader()
              && trick.cards().get(0).action().card() instanceof StandardCard lead
              && view.contract().trump().matches(lead.suit())
              && lead.rank().value() > card.rank().value() && higher <= 2) {
            possibleRemainder.put(play.seat(), Math.max(0, (int) higher - 1));
          } else {
            possibleRemainder.remove(play.seat());
          }
        }
        spent.add(play.action().card());
      }
    }
    // 모든 야당 후보에 점수 제출 근거가 있고 불확실한 낮은 잔여가 한 장 정도이면 교환을 멈춥니다.
    return live.stream().allMatch(possibleRemainder::containsKey)
        && live.stream().mapToInt(possibleRemainder::get).sum() <= 1;
  }

  /**
   * 자기 기루와 미확인 기루는 관측자의 개인 정보 범위를 유지합니다.
   */
  public List<StandardCard> own() {
    return view.hand().stream().filter(c -> c instanceof StandardCard card
        && view.contract().trump().matches(card.suit())).map(StandardCard.class::cast).toList();
  }

  public List<StandardCard> outside() {
    return cards.outsideCards().stream().filter(c -> c instanceof StandardCard card
        && view.contract().trump().matches(card.suit())).map(StandardCard.class::cast).toList();
  }

  public int remainingCount() {
    return own().size() + outside().size();
  }

  public int remainingHand(Seat seat) {
    return 10 - view.context().history().size() - (
        view.trick().stream().anyMatch(p -> p.seat().equals(seat)) ? 1 : 0);
  }

  /**
   * 소유 추정은 확정 제거에 쓰지 않고 가능한 자리의 가중치로만 사용합니다.
   */
  public Map<Seat, Double> ownerProbabilities(StandardCard card, PlayFacts facts) {
    if (own().contains(card)) {
      return Map.of(view.self(), 1.0);
    }
    if (!outside().contains(card)) {
      return Map.of();
    }
    Map<Seat, Double> weights = new LinkedHashMap<>();
    for (Seat seat : view.context().playOrder().seats()) {
      if (seat.equals(view.self()) || cards.isVoid(seat, card.suit()) || remainingHand(seat) == 0) {
        continue;
      }
      double weight = likelyShort(seat) ? 0.08 : seat.equals(view.context().declarer()) ? 2.0 : 1.0;
      weights.put(seat, weight);
    }
    long rounds = view.context().history().stream().filter(t -> view.contract().trump().matches(
        Rules.conditionsFor(view.contract().trump(), t.number(), t.cards().get(0).action())
            .leadSuit())).count();
    boolean seekingLoss = view.context().history().stream().anyMatch(t ->
        t.leader().equals(view.context().declarer()) && !view.contract().trump().matches(
            Rules.conditionsFor(view.contract().trump(), t.number(), t.cards().get(0).action())
                .leadSuit()))
        || view.trick().stream().anyMatch(p -> p.seat().equals(view.context().declarer())
        && p.action().card() instanceof StandardCard c && !view.contract().trump().matches(c.suit())
        && !c.equals(Rules.mighty(view.contract().trump())));
    if (rounds >= 2 && seekingLoss) {
      weights.computeIfPresent(view.context().declarer(), (seat, w) -> w * 12);
    }
    // 주공의 낮은 기루를 비탑으로 잡은 자리에는 미소모 상위도 있을 가능성을 높입니다.
    for (var trick : view.context().history()) {
      var lead = trick.cards().get(0);
      if (!lead.seat().equals(view.context().declarer()) || !(lead.action()
          .card() instanceof StandardCard low)
          || !view.contract().trump().matches(low.suit()) || trick.winner()
          .equals(view.context().declarer())) {
        continue;
      }
      var winning = trick.cards().stream().filter(p -> p.seat().equals(trick.winner())).findFirst()
          .orElseThrow();
      if (winning.action().card() instanceof StandardCard winner && winner.suit() == card.suit()
          && winner.rank().value() < card.rank().value()) {
        weights.computeIfPresent(trick.winner(), (seat, w) -> w * 12);
        weights.computeIfPresent(view.context().declarer(), (seat, w) -> w * 0.2);
      }
    }
    // 비주공에게는 버림패도 미확인이므로 모든 잔여 카드를 특정 자리에 강제로 배정하지 않습니다.
    double buried = view.privateInfo() instanceof mighty.game.observation.DeclarerInfo ? 0 : 0.3;
    double total = weights.values().stream().mapToDouble(Double::doubleValue).sum() + buried;
    if (total == 0) {
      return Map.of();
    }
    weights.replaceAll((seat, w) -> w / total);
    return Map.copyOf(weights);
  }

  public double expectedCount(Seat seat, PlayFacts facts) {
    if (seat.equals(view.self())) {
      return own().size();
    }
    return Math.min(remainingHand(seat), outside().stream().mapToDouble(c ->
        ownerProbabilities(c, facts).getOrDefault(seat, 0.0)).sum());
  }

  /**
   * 이번 사이드를 놓치면 공격 기회가 사라질 수 있다는 소프트 추정입니다.
   */
  public boolean likelyLastSide(Seat seat, PlayFacts facts) {
    if (view.contract().trump() == Trump.NO_TRUMP || outside().isEmpty()) {
      return false;
    }
    int hand = remainingHand(seat);
    if (hand > 4) {
      return false;
    }
    double trumps = expectedCount(seat, facts);
    return trumps >= Math.max(1, hand - 0.75);
  }

  /**
   * 모든 역할이 같은 공개 무문양과 짧은 기루 신호를 참조합니다.
   */
  public boolean enemiesMayHold(PlayFacts facts) {
    if (avoidExchange(facts)) {
      return false;
    }
    return outside().stream().anyMatch(card -> view.context().playOrder().seats().stream()
        .filter(seat -> !facts.teams().isAlly(seat))
        .anyMatch(seat -> !cards.isVoid(seat, card.suit())));
  }

  private static void observe(List<PublicPlay> plays, View view, Set<Seat> seats, Set<Card> spent) {
    if (plays.isEmpty() || !(plays.get(0).action().card() instanceof StandardCard lead)
        || !view.contract().trump().matches(lead.suit())) {
      return;
    }
    boolean topLead = Arrays.stream(Rank.values())
        .filter(rank -> rank.value() > lead.rank().value())
        .map(rank -> new StandardCard(lead.suit(), rank))
        .noneMatch(c -> !c.equals(Rules.mighty(view.contract().trump())) && !spent.contains(c));
    for (PublicPlay play : plays.subList(1, plays.size())) {
      if (!(play.action().card() instanceof StandardCard card) || card.suit() != lead.suit()
          || card.equals(Rules.mighty(view.contract().trump()))) {
        continue;
      }
      if (topLead && card.points() > 0) {
        seats.add(play.seat());
      } else if (card.points() == 0) {
        seats.remove(play.seat());
      }
    }
  }

}
