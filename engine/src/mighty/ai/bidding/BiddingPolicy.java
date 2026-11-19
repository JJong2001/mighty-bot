package mighty.ai.bidding;

import mighty.game.bidding.BiddingAction;
import mighty.game.observation.BiddingView;

public interface BiddingPolicy {

  BiddingAction choose(BiddingView view);
}
