package mighty.game.bidding;

import java.util.Objects;
import mighty.model.Trump;

public final class BiddingRules {

  private BiddingRules() {
  }

  public static boolean isHigher(Bid offered, Bid current) {
    Objects.requireNonNull(offered, "제시한 공약이 필요합니다.");
    Objects.requireNonNull(current, "현재 공약이 필요합니다.");
    if (current.points() == Bid.MAX_POINTS) {
      return false;
    }
    if (offered.points() != current.points()) {
      return offered.points() > current.points();
    }
    return offered.trump() == Trump.NO_TRUMP
        && current.trump() != Trump.NO_TRUMP;
  }

}
