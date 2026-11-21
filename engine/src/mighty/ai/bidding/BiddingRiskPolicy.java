package mighty.ai.bidding;

import mighty.game.GameMode;
import mighty.game.bidding.Bid;
import mighty.game.observation.BiddingView;

/**
 * 배분 모드별 위험 평가의 경계입니다. 공약의 합법성과 후보 선택 규칙은 공유합니다.
 */
enum BiddingRiskPolicy {
  FIVE_PLAYER {
    @Override
    BidExpectedScoreEvaluator.ScoreEstimate estimate(BiddingView view, BiddingHand hand,
        Bid contract, BidExpectedScoreEvaluator evaluator) {
      return evaluator.estimateDeclarerScore(hand, contract, view.playerScore());
    }
  },
  SIX_PLAYER {
    @Override
    BidExpectedScoreEvaluator.ScoreEstimate estimate(BiddingView view, BiddingHand hand,
        Bid contract, BidExpectedScoreEvaluator evaluator) {
      // 육마 최초 8장에는 오마의 가상 배분을 적용하지 않습니다. 기존 후보 평가를 유지합니다.
      if (view.hand().size() != 10) {
        return BidExpectedScoreEvaluator.ScoreEstimate.unavailable();
      }
      // 휴식 자리의 10장 배분은 기존 평가를 유지하며, 육마 전용 모델은 이 경계에서 확장합니다.
      return evaluator.estimateDeclarerScore(hand, contract, view.playerScore());
    }
  };

  static BiddingRiskPolicy forMode(GameMode mode) {
    return switch (mode) {
      case FIVE_PLAYER -> FIVE_PLAYER;
      case SIX_PLAYER -> SIX_PLAYER;
    };
  }

  abstract BidExpectedScoreEvaluator.ScoreEstimate estimate(BiddingView view, BiddingHand hand,
      Bid contract, BidExpectedScoreEvaluator evaluator);
}
