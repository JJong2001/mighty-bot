package mighty.application.bidding;

import java.util.Objects;
import mighty.ai.bidding.BiddingPolicy;
import mighty.game.bidding.BiddingAction;
import mighty.game.bidding.BiddingState;
import mighty.game.bidding.PublicBid;
import mighty.game.observation.BiddingView;

/**
 * AI의 비딩 판단을 요청하고, 선택한 행동을 게임에 제출합니다.
 */
public final class BiddingService {

  private final BiddingPolicy policy;

  public BiddingService(BiddingPolicy policy) {
    this.policy = Objects.requireNonNull(
        policy,
        "비딩 판단 정책이 필요합니다."
    );
  }

  /**
   * 현재 관측으로 한 차례를 진행한 새 비딩 상태를 반환합니다.
   */
  public BiddingState playTurn(BiddingView view) {
    Objects.requireNonNull(view, "비딩 관측이 필요합니다.");
    BiddingAction action = Objects.requireNonNull(
        policy.choose(view),
        "비딩 판단 정책이 행동을 반환하지 않았습니다."
    );
    PublicBid submission = new PublicBid(view.self(), action);
    return view.bidding().submit(
        submission,
        view.hand(),
        view.playerScore()
    );
  }

}
