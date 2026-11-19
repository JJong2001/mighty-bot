package mighty.game.bidding;

import java.util.Objects;
import mighty.game.Seat;

/**
 * 딜미스 선언으로 비딩이 종료된 결과입니다. 선언자는 재배분 후 첫 비딩자가 됩니다.
 */
public record DealMissResult(
    Seat declaredBy
) implements BiddingOutcome {

  public DealMissResult {
    Objects.requireNonNull(
        declaredBy,
        "딜미스 선언자 자리가 필요합니다."
    );
  }

}
