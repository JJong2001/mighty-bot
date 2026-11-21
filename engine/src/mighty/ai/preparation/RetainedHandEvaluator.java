package mighty.ai.preparation;

import java.util.List;
import java.util.Optional;
import mighty.ai.planning.NoTrumpPlan;
import mighty.ai.planning.NoTrumpPlanner;
import mighty.ai.bidding.BiddingHand;
import mighty.model.*;

/**
 * 남길 패의 기루 장수, 사이드 회수와 상위 연결을 평가합니다. 묻는 점수는 부차적입니다.
 */
public final class RetainedHandEvaluator {

  public double score(List<Card> kept, List<Card> discarded, Trump trump) {
    BiddingHand hand = BiddingHand.from(kept);
    Optional<NoTrumpPlan> noTrump = trump == Trump.NO_TRUMP
        ? Optional.of(NoTrumpPlanner.analyze(hand, discarded)) : Optional.empty();
    double score = (hand.hasMighty(trump) ? 1000 : 0) + (hand.hasJoker() ? 900 : 0)
        + hand.trumpCount(trump) * 30;
    if (trump == Trump.NO_TRUMP) {
      int mainLength = java.util.Arrays.stream(Suit.values()).mapToInt(s -> hand.holding(s).size())
          .max().orElse(0);
      score += mainLength * 20;
      // 노기루 후보의 버리기 자체도 같은 운영 가능성 검사를 통과해야 합니다.
      if (noTrump.filter(NoTrumpPlan::available).isPresent()) {
        score += 120;
      }
    }
    for (Suit suit : Suit.values()) {
      var holding = hand.ordinaryHolding(suit, trump);
      var control = mighty.ai.analysis.HoldingControl.analyze(kept, discarded, trump, suit);
      int run = control.topRun();
      score += run * 12;
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
      if (run == 0 && !holding.ranks().isEmpty()) {
        // 단독 물패의 비용은 water에 이미 포함됩니다. 별도 벌점으로 긴 사이드를 자르지 않습니다.
        if (control.connected() > 1) {
          score += control.connected() * 5 + control.possibleWinners() * 12;
        } else if (control.length() >= 3) {
          score += control.possibleWinners() * 7;
        }
      }
    }
    // 총은 노기루 조커 처리 경로이며, 단순 비점수 물패로 버리지 않습니다.
    if (hand.hasGun(trump)) {
      score += trump == Trump.NO_TRUMP ? 16 : 1;
    }
    return score + discarded.stream().mapToInt(Card::points).sum();
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
