package mighty.ai.bidding;

import java.util.*;
import mighty.ai.planning.HighNoTrumpPlan;
import mighty.ai.planning.NoTrumpPlanner;
import mighty.ai.planning.distribution.NoTrumpDistributionEvaluator;
import mighty.model.*;

/**
 * 문양 통제 경로는 지정 프렌드의 조건부 배분으로 평가합니다.
 */
final class NoTrumpBidEvaluator {

  private NoTrumpBidEvaluator() {
  }

  static BidAssessment assess(CandidateResources resources, Collection<? extends Card> excluded) {
    var hand = resources.hand();
    if (excluded.isEmpty() && Arrays.stream(Suit.values()).mapToInt(s -> hand.holding(s).size())
        .sum() + (hand.hasJoker() ? 1 : 0) == 10) {
      var high = HighNoTrumpPlan.find(hand).filter(p -> p.competitiveShape(hand));
      if (high.isPresent()) {
        return new BidAssessment(Trump.NO_TRUMP, true, 16, high.get().competitiveCeiling(), 185,
            List.of("주력의 상위 연결과 별도 초구로 엿노에 진입해 공개 입찰에 따라 경쟁합니다."));
      }
    }
    var plan = NoTrumpPlanner.analyze(hand, excluded);
    boolean ownMighty = hand.hasMighty(Trump.NO_TRUMP);
    boolean ownJoker = hand.hasJoker();
    boolean gun = hand.hasGun(Trump.NO_TRUMP);
    if (!ownMighty && !ownJoker && !gun) {
      return unavailable(resources);
    }
    // 총으로 조커를 소모해도 첫 주력 상위에 선을 내주면 별도 특수 회수가 없습니다.
    if (!ownMighty && !ownJoker && hand.ordinaryTopRunLength(plan.primary(), Trump.NO_TRUMP) == 0) {
      return unavailable(resources);
    }
    int longest = Arrays.stream(Suit.values()).mapToInt(s -> hand.holding(s).size()).max()
        .orElse(0);
    int tops = Arrays.stream(Suit.values())
        .mapToInt(s -> hand.ordinaryTopRunLength(s, Trump.NO_TRUMP)).sum();
    if (longest >= 5 || tops >= 6 || ownMighty && tops >= 5
        || NoTrumpPlanner.splitControl(hand, plan.friend())) {
      var support = NoTrumpDistributionEvaluator.bestSupport(hand, excluded);
      var distribution = support.distribution();
      int ceiling = NoTrumpDistributionEvaluator.qualifyingContract(hand, support);
      if (ownMighty && (ownJoker || longest >= 6 && tops >= 6)) {
        var solo = NoTrumpDistributionEvaluator.evaluateSolo(hand, excluded);
        int soloCeiling = NoTrumpDistributionEvaluator.soloCeiling(solo);
        if (soloCeiling > ceiling || ownJoker && soloCeiling == 20
            || gun && soloCeiling >= 18 && ceiling < 20) {
          return new BidAssessment(resources.trump(), true, soloCeiling, soloCeiling,
              soloCeiling * 10 + 25, List.of("자력 노기루 운영을 가상 배분에서 평가했습니다."));
        }
      }
      if (ceiling > 0) {
        return new BidAssessment(resources.trump(), true, ceiling, ceiling, ceiling * 10 + 25,
            List.of(
                "프렌드 " + support.friend().toCode() + ", 주력 " + plan.primary() + ", 조건부 배분 "
                    + distribution.samples() + "개",
                "엿노 통제 경로 " + distribution.sixteenWins() + "/" + distribution.samples()
                    + ", 곱노 통제 경로 " + distribution.seventeenWins() + "/" + distribution.samples()
                    + ", 풀노 통제 경로 " + distribution.twentyWins() + "/" + distribution.samples(),
                "프렌드의 미지정 카드도 협력 승리에 포함합니다. 전체 최적 대국의 성공 보장과는 구분합니다."));
      }
    }
    if (FlexibleNoTrumpOpening.available(hand)) {
      return FlexibleNoTrumpOpening.assess(hand);
    }
    if (ownMighty && resources.length() >= 5
        && hand.ordinaryTopRunLength(resources.primary(), resources.trump()) > 0) {
      return new BidAssessment(resources.trump(), true, 15, 15, 175, List.of(
          "마이티와 주력의 자력 승리로 바닥 전 닷노 진입만 허용합니다. 바닥 뒤에는 다시 평가합니다."));
    }
    // 이 경로는 바닥을 보기 전 닷노 입찰용입니다. 바닥을 본 뒤에는 실제 패로 다시 비교합니다.
    if (excluded.isEmpty()) {
      var route = FlexibleNoTrumpOpening.fifteenPointRoute(hand, plan);
      if (route.isPresent()) {
        return new BidAssessment(Trump.NO_TRUMP, true, 15, 15, 165, List.of(route.get()));
      }
      if (NoTrumpPlanner.jokerSweepSuit(hand).isPresent()
          && Arrays.stream(Suit.values()).mapToInt(s -> hand.holding(s).size()).sum() == 9) {
        // 조커와 일반 카드 아홉 장으로 바닥을 보기 전의 오마 입찰만 평가합니다.
        return new BidAssessment(Trump.NO_TRUMP, true, 15, 15, 165,
            List.of("조커로 주력을 살리는 닷노와 기루의 평균 정산점을 비교합니다."), true);
      }
    }
    return unavailable(resources);
  }

  private static BidAssessment unavailable(CandidateResources resources) {
    return BidAssessment.unavailable(resources.trump(), "특수카드 시점·주력 연결·반환을 포함한 노기루 운영 경로가 부족합니다.");
  }

}
