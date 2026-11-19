package mighty.ai.bidding;

import java.util.Arrays;
import java.util.List;
import mighty.model.Trump;

/**
 * 자기 패의 운영 자원을 후보별로 분석한 후 기루와 노기루를 독립 평가합니다.
 */
public final class BiddingHandEvaluator {

  public List<BidAssessment> assess(BiddingHand hand) {
    return assess(hand, List.of());
  }

  /**
   * 버리기 이후에는 주공만 아는 세 장도 후보 제거와 프렌드 계획에 반영합니다.
   */
  public List<BidAssessment> assess(BiddingHand hand,
      java.util.Collection<? extends mighty.model.Card> excluded) {
    return Arrays.stream(Trump.values()).map(trump -> {
      CandidateResources resources = CandidateResources.from(hand, trump);
      return trump == Trump.NO_TRUMP
          ? NoTrumpBidEvaluator.assess(resources, excluded) : SuitBidEvaluator.assess(resources);
    }).toList();
  }

}
