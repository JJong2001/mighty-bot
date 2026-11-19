package mighty.ai.bidding;

import java.util.Objects;
import mighty.game.bidding.Bid;
import mighty.game.bidding.BidOffer;
import mighty.game.bidding.BidPass;
import mighty.game.bidding.BiddingAction;
import mighty.game.observation.BiddingView;
import mighty.model.Trump;

/**
 * 한 판의 진행을 연결하기 위한 최소 비딩 정책입니다. 첫 입찰에서는 가장 긴 기루 문양으로 13을 제시하고, 이미 입찰이 있으면 패스합니다.
 */
public final class BaselineBiddingPolicy implements BiddingPolicy {

  @Override
  public BiddingAction choose(BiddingView view) {
    Objects.requireNonNull(view, "비딩 관측이 필요합니다.");
    if (view.bidding().highestBid().isPresent()) {
      return BidPass.INSTANCE;
    }
    BiddingHand hand = BiddingHand.from(view.hand());
    Trump trump = chooseTrump(hand);
    return new BidOffer(new Bid(Bid.MIN_POINTS, trump));
  }

  /**
   * 가장 많이 보유한 문양을 선택합니다. 장수가 같으면 enum 선언 순서에서 앞선 문양을 유지합니다.
   */
  private static Trump chooseTrump(BiddingHand hand) {
    Trump selected = Trump.SPADE;
    int longestCount = hand.trumpCount(selected);
    for (Trump candidate : Trump.values()) {
      if (candidate == Trump.NO_TRUMP) {
        continue;
      }
      int count = hand.trumpCount(candidate);
      if (count > longestCount) {
        selected = candidate;
        longestCount = count;
      }
    }
    return selected;
  }

}
