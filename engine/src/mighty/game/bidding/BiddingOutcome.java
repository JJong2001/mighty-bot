package mighty.game.bidding;

/**
 * 비딩이 종료된 결과입니다.
 */
public sealed interface BiddingOutcome
    permits BiddingResult, DealMissResult {

}
