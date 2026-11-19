package mighty.ai.bidding;

import java.util.List;
import mighty.game.rule.Rules;
import mighty.model.Rank;

/**
 * 간 회수와 프렌드 연결을 사용할 기루 후보를 평가합니다.
 */
final class SuitBidEvaluator {

  private SuitBidEvaluator() {
  }

  static BidAssessment assess(CandidateResources resources) {
    int length = resources.length();
    boolean specials = resources.mighty() || resources.joker();
    boolean sideEntry = resources.sideAces() > 0;
    boolean usableTrump = resources.has(Rank.ACE)
        || (resources.hasSideOpening()
        && (resources.has(Rank.KING) || resources.has(Rank.QUEEN) || resources.has(Rank.JACK)))
        || (length >= 5 && resources.pointsAtTop() >= 2);
    if (length < 4 || (!specials && !usableTrump)) {
      return BidAssessment.unavailable(resources.trump(),
          "기루 길이 또는 초구·회수·기루 통제가 부족해 자발적으로 경쟁하지 않습니다.");
    }
    int ceiling = 15;
    if (length == 4) {
      if (specials || resources.sideTopRun() >= 2
          || (resources.has(Rank.KING) && resources.has(Rank.JACK) && sideEntry)) {
        ceiling = 16;
      }
      // 긴 부기루의 QJ를 마이티 프렌드 연결 후 발전시킬 여지가 있습니다.
      SuitHolding friendSuit = resources.hand().holding(Rules.mighty(resources.trump()).suit());
      if (resources.has(Rank.ACE) && !resources.mighty() && friendSuit.size() >= 3
          && friendSuit.contains(Rank.QUEEN) && friendSuit.contains(Rank.JACK)) {
        ceiling = 16;
      }
    } else {
      ceiling = 16 + Math.max(0, length - 6);
      if (specials || (resources.has(Rank.KING) && resources.sideTopRun() >= 2)) {
        ceiling++;
      }
      if (resources.mighty() && resources.joker()) {
        ceiling++;
      }
      if (resources.holding().consecutiveFrom(Rank.ACE) >= 3 && resources.sideAces() >= 2) {
        ceiling++;
      }
    }
    // 특수카드 없는 긴 KQJ는 A에 맞은 뒤 간으로 회수하는 곱 운영입니다.
    if (length >= 6 && !specials && !resources.has(Rank.ACE)) {
      ceiling = Math.min(17, ceiling);
    }
    ceiling = Math.min(19, ceiling);
    boolean longControl = length >= 8 && resources.mighty() && resources.sideWater() == 0;
    boolean independentRuns = length >= 4 && resources.mighty()
        && resources.has(Rank.KING) && resources.has(Rank.JACK)
        && resources.sideAces() >= 2 && resources.sideWater() == 0;
    if (longControl || independentRuns) {
      ceiling = 20;
    }
    int opening = length >= 6 || (ceiling >= 17 && resources.sideAces() >= 2) ? 16 : 15;
    int preference = ceiling * 10 + (resources.mighty() ? 3 : 0) + (resources.joker() ? 2 : 0);
    return new BidAssessment(resources.trump(), true, opening, ceiling, preference, List.of(
        "기루 " + length + "장, 초구 지원 " + resources.hasSideOpening()
            + ", 독립 사이드 A " + resources.sideAces() + "장, 사이드 상위 연결 " + resources.sideTopRun() + "장",
        "마이티 " + resources.mighty() + ", 조커 " + resources.joker()
            + "; 프렌드 호출과 간 회수를 전제로 한 조건부 운영 한도",
        "초구와 반환 지원은 필요 조건이며 상대 분포에 대한 성공 보장은 아닙니다."));
  }

}
