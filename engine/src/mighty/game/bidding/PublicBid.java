package mighty.game.bidding;

import java.util.Objects;
import mighty.game.Seat;

public record PublicBid(
    Seat seat,
    BiddingAction action
) {

  public PublicBid {
    Objects.requireNonNull(seat, "비딩한 자리가 필요합니다.");
    Objects.requireNonNull(action, "비딩 행동이 필요합니다.");
  }

}
