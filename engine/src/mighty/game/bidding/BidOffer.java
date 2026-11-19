package mighty.game.bidding;

import java.util.Objects;

public record BidOffer(Bid bid) implements BiddingAction {

  public BidOffer {
    Objects.requireNonNull(bid, "제시할 공약이 필요합니다.");
  }

}
