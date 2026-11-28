package mighty.ai.play;

import java.util.*;
import mighty.game.Seat;
import mighty.game.friend.*;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.*;

/** 자기 패와 공개 제약으로 가능한 보유자를 구합니다. 미확인 묻은 패도 있으므로 존재를 확정하지 않습니다. */
public final class CardLocationInference {
  private final View view;
  private final CardKnowledge cards;
  private final PublicBehaviorInference behavior;

  public CardLocationInference(View view, CardKnowledge cards) {
    this(view, cards, new PublicBehaviorInference(view));
  }

  CardLocationInference(View view, CardKnowledge cards, PublicBehaviorInference behavior) {
    this.view = view;
    this.cards = cards;
    this.behavior = behavior;
  }

  /** 공개 행동이 지지하는 자리만 반환합니다. 비어 있으면 위치에 대한 행동 근거가 없습니다. */
  public Set<Seat> suggestedHolders(Card card) {
    Set<Seat> possible = possibleHolders(card);
    Set<Seat> suggested = new HashSet<>();
    behavior.holdingHints().stream().filter(h -> h.card().equals(card))
        .map(PublicBehaviorInference.HoldingHint::seat).filter(possible::contains)
        .forEach(suggested::add);
    return Set.copyOf(suggested);
  }

  /** 확률이 아닌 공개 행동 보정 계수입니다. 무문양 등 확정 제약을 먼저 적용합니다. */
  public double behavioralWeight(Seat seat, Card card) {
    if (!possibleHolders(card).contains(seat)) return 0;
    double support = suggestedHolders(card).contains(seat) ? 4.0 : 1.0;
    int missed = card instanceof StandardCard ordinary ? behavior.missedCaptures(seat, ordinary) : 0;
    return support * Math.pow(0.25, Math.min(3, missed));
  }

  /** 가능한 위치 중 행동 반증이 상대적으로 적은 자리입니다. 확정 제거와 분리합니다. */
  public boolean supportedHolder(Seat seat, Card card) {
    double best = possibleHolders(card).stream().mapToDouble(s -> behavioralWeight(s, card)).max().orElse(0);
    return best > 0 && behavioralWeight(seat, card) >= best * 0.5;
  }

  public record CountRange(int minimum, int maximum) {}

  /** 문양별 잔여 장수의 보수적 범위. 비주공에게는 묻힌 세 장의 가능성을 남깁니다. */
  public CountRange countRange(Seat seat, Suit suit) {
    var mighty = Rules.mighty(view.contract().trump());
    if (seat.equals(view.self())) {
      int count = (int)view.hand().stream().filter(c -> c instanceof StandardCard s
          && s.suit() == suit && !c.equals(mighty)).count();
      return new CountRange(count, count);
    }
    var unseen = cards.outsideCards(suit).stream().filter(c -> !c.equals(mighty)).toList();
    int maximum = Math.min(remaining(seat), (int)unseen.stream()
        .filter(c -> possibleHolders(c).contains(seat)).count());
    int elsewhere = view.privateInfo() instanceof mighty.game.observation.DeclarerInfo ? 0 : 3;
    for (Seat other : view.context().playOrder().seats()) {
      if (other.equals(seat) || other.equals(view.self())) continue;
      elsewhere += Math.min(remaining(other), (int)unseen.stream()
          .filter(c -> possibleHolders(c).contains(other)).count());
    }
    return new CountRange(Math.min(maximum, Math.max(0, unseen.size() - elsewhere)), maximum);
  }

  private int remaining(Seat seat) {
    return Math.max(0, 10 - view.context().history().size()
        - (int)view.trick().stream().filter(p -> p.seat().equals(seat)).count());
  }

  public Set<Seat> possibleHolders(Card card) {
    if (view.hand().contains(card)) return Set.of(view.self());
    if (!cards.outsideCards().contains(card)) return Set.of();
    Set<Seat> result = new HashSet<>();
    for (Seat seat : view.context().playOrder().seats()) {
      if (seat.equals(view.self())) continue;
      long submitted = view.trick().stream().filter(p -> p.seat().equals(seat)).count();
      if (10 - view.context().history().size() - submitted <= 0) continue;
      // 마이티는 문양을 따르지 않아도 되므로 일반 무문양 증거로 배제하지 않습니다.
      if (card instanceof StandardCard ordinary && !card.equals(Rules.mighty(view.contract().trump()))
          && cards.isVoid(seat, ordinary.suit())) continue;
      if (view.friendSelection() instanceof CardFriend called && called.card().equals(card)) {
        if (seat.equals(view.context().declarer())) continue;
        if (view.context().friendStatus() instanceof RevealedFriend friend
            && !seat.equals(friend.seat())) continue;
      }
      result.add(seat);
    }
    return Set.copyOf(result);
  }

  /** 해당 자리의 미소모 점수 위협 후보입니다. 실제 보유 장수나 승리 횟수는 아닙니다. */
  public List<StandardCard> possiblePointCards(Seat seat, Suit suit) {
    return cards.outsideCards(suit).stream()
        .filter(c -> c.points() > 0 && !c.equals(Rules.mighty(view.contract().trump())))
        .filter(c -> possibleHolders(c).contains(seat)).toList();
  }
}
