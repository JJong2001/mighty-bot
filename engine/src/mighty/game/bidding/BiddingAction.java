package mighty.game.bidding;

public sealed interface BiddingAction
    permits BidOffer, BidPass, DealMiss {

}
