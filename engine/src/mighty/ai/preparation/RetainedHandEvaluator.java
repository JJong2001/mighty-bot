package mighty.ai.preparation;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import mighty.ai.analysis.HoldingControl;
import mighty.ai.bidding.BiddingHand;
import mighty.ai.planning.NoTrumpPlan;
import mighty.ai.planning.NoTrumpPlanner;
import mighty.ai.planning.SuitFriendPlan;
import mighty.game.friend.CardFriend;
import mighty.game.rule.Rules;
import mighty.model.*;

/**
 * 남길 패의 기루 장수, 사이드 회수와 상위 연결을 평가합니다. 묻는 점수는 부차적입니다.
 */
public final class RetainedHandEvaluator {

  static final int TOP_RUN_VALUE = 12;

  /**
   * 동점에서는 프렌드가 버림패를 몰라도 알아볼 수 있는 일반 탑을 남깁니다.
   */
  public int visibleTopCount(List<Card> kept, Trump trump) {
    int count = 0;
    for (Suit suit : Suit.values()) {
      var top = java.util.Arrays.stream(Rank.values())
          .map(rank -> new StandardCard(suit, rank))
          .filter(card -> !card.equals(mighty.game.rule.Rules.mighty(trump)))
          .max(java.util.Comparator.comparingInt(card -> card.rank().value())).orElseThrow();
      if (kept.contains(top)) {
        count++;
      }
    }
    return count;
  }

  public double score(List<Card> kept, List<Card> discarded, Trump trump) {
    return assess(kept, discarded, trump).score();
  }

  record Assessment(double score, int topCards, int development, int trumps, boolean mighty,
                    boolean joker, boolean hasNoTrumpPlan) {

    boolean sameControls(Assessment other) {
      return trumps == other.trumps && mighty == other.mighty && joker == other.joker
          && hasNoTrumpPlan == other.hasNoTrumpPlan;
    }
  }

  Assessment assess(List<Card> kept, List<Card> discarded, Trump trump) {
    BiddingHand hand = BiddingHand.from(kept);
    Optional<NoTrumpPlan> noTrump = trump == Trump.NO_TRUMP
        ? Optional.of(NoTrumpPlanner.analyze(hand, discarded)) : Optional.empty();
    Map<Suit, HoldingControl> controls = new EnumMap<>(Suit.class);
    for (Suit suit : Suit.values()) {
      controls.put(suit, HoldingControl.analyze(kept, discarded, trump, suit));
    }
    var sideFriend = SuitFriendPlan.choose(hand, discarded, trump);
    int trumps = hand.trumpCount(trump);
    boolean mighty = hand.hasMighty(trump);
    boolean joker = hand.hasJoker();
    boolean hasNoTrumpPlan = noTrump.filter(NoTrumpPlan::available).isPresent();
    int topCards = 0;
    int development = 0;
    double score = (mighty ? 1000 : 0) + (joker ? 900 : 0) + trumps * 30;
    if (trump == Trump.NO_TRUMP) {
      // 주력의 현재 탑과 운영으로 살릴 수 있는 나머지 장수의 값을 구분합니다.
      int mainRun = controls.values().stream().mapToInt(HoldingControl::topRun).max().orElse(0);
      score += mainRun * 20;
      if (hasNoTrumpPlan && noTrump.orElseThrow().jokerBreaks() <= 1) {
        var primary = controls.get(noTrump.orElseThrow().primary());
        score += primary.topRun() > 0 ? (primary.length() - primary.topRun()) * 6 : 0;
      }
      // 노기루 후보의 버리기 자체도 같은 운영 가능성 검사를 통과해야 합니다.
      if (hasNoTrumpPlan) {
        score += 120;
      }
    }
    for (Suit suit : Suit.values()) {
      var holding = hand.ordinaryHolding(suit, trump);
      var control = controls.get(suit);
      int run = control.topRun();
      topCards += run;
      score += run * TOP_RUN_VALUE;
      if (trump.matches(suit)) {
        score += holding.ranks().stream().filter(r -> r.value() >= Rank.TEN.value()).count() * 3;
        continue;
      }
      // A가 없는 사이드는 장수를 유지해야 상위 탐색 뒤에도 활용할 카드가 남습니다.
      // A 아래 숫자는 별도의 개발 경로가 아니므로 회수 카드 평가와 중복 가산하지 않습니다.
      var ace = new StandardCard(suit, Rank.ACE);
      if (trump != Trump.NO_TRUMP && !kept.contains(ace) && !discarded.contains(ace)) {
        score += holding.size() * (holding.size() - 1) * 2;
      }
      // 사이드 탑카의 선 회수는 막힌 문양의 예상 승리보다 먼저 평가합니다.
      if (run > 0) {
        score += 24;
        score += supportedPoints(holding.ranks(), discarded, trump, suit, run) * 2;
      }
      if (run == 0 && control.blockers() > 0) {
        long points = holding.ranks().stream().filter(rank -> rank.value() >= Rank.TEN.value())
            .count();
        long cover = holding.size() - points;
        // 막힌 점수만 남기면 상대 상위에 강제로 따라내게 됩니다.
        score -= Math.min(points, Math.max(0, control.blockers() - cover)) * 10;
        if (trump != Trump.NO_TRUMP && points > 0 && cover >= control.blockers()
            && control.blockers() <= 2) {
          // 상대 상위를 따를 물패가 있으면 사이드 점수 보호를 평가합니다. 승리 장수로 세지는 않습니다.
          score += 6;
        }
      }
      int water = holding.size() - run;
      boolean primaryRun = noTrump.filter(plan -> plan.available() && plan.primary() == suit)
          .isPresent();
      if (!primaryRun) {
        score -= water * 3;
      }
      if (holding.size() == 0 && trump != Trump.NO_TRUMP) {
        score += 15;
      }
      if (run == 0 && control.blockers() > 0 && control.connected() >= 2) {
        boolean sideEntry = mighty || Arrays.stream(Suit.values())
            .anyMatch(other -> other != suit && controls.get(other).topRun() > 0);
        boolean friendRemovesBlocker = noTrump.filter(NoTrumpPlan::available)
            .map(NoTrumpPlan::friend)
            .filter(card -> card instanceof StandardCard target && target.suit() == suit
                && !target.equals(Rules.mighty(trump))
                && target.rank().value() > holding.ranks().get(0).value())
            .isPresent() || sideFriend.filter(plan -> plan.openingSuit() == suit
                && plan.selection() instanceof CardFriend).isPresent();
        // 장벽을 뺄 수단, 남은 연결, 별도의 회수 수단이 모두 있는 경우에만 조건부 가치를 줍니다.
        int removalOptions = (joker ? 1 : 0) + (friendRemovesBlocker ? 1 : 0);
        if (sideEntry && control.blockers() <= removalOptions) {
          development += control.connected();
          score += control.connected() * 6;
        }
      }
    }
    // 총은 노기루 조커 처리 경로이며, 단순 비점수 물패로 버리지 않습니다.
    if (hand.hasGun(trump)) {
      score += trump == Trump.NO_TRUMP ? 16 : 1;
    }
    return new Assessment(score + discarded.stream().mapToInt(Card::points).sum(),
        topCards, development, trumps, mighty, joker, hasNoTrumpPlan);
  }

  /**
   * 탑카 뒤의 점수는 실제 연결이나 받침이 있을 때만 지원 가치를 더합니다.
   */
  private static long supportedPoints(List<Rank> held, List<Card> removed, Trump trump,
      Suit suit, int topRun) {
    var tail = new java.util.HashSet<>(held.subList(topRun, held.size()));
    var available = java.util.Arrays.stream(Rank.values())
        .filter(rank -> !new StandardCard(suit, rank).equals(mighty.game.rule.Rules.mighty(trump)))
        .filter(rank -> !removed.contains(new StandardCard(suit, rank)))
        .sorted(java.util.Comparator.comparingInt(Rank::value)).toList();
    long topPoints = held.stream().limit(topRun).filter(rank -> rank.value() >= Rank.TEN.value())
        .count();
    long connectedPoints = tail.stream().filter(rank -> rank.value() >= Rank.TEN.value())
        .filter(rank -> {
          int index = available.indexOf(rank);
          return index > 0 && tail.contains(available.get(index - 1))
              || index + 1 < available.size() && tail.contains(available.get(index + 1));
        }).count();
    return topPoints + connectedPoints;
  }

}
