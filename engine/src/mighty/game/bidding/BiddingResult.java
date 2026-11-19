package mighty.game.bidding;

import java.util.Objects;
import mighty.game.Seat;

public record BiddingResult(
    Seat declarer,
    Bid contract
) implements BiddingOutcome {

  public BiddingResult {
    Objects.requireNonNull(declarer, "주공 자리가 필요합니다.");
    Objects.requireNonNull(contract, "낙찰 공약이 필요합니다.");
  }

}
