package mighty.game.trick;

import java.util.Objects;
import mighty.game.Seat;
import mighty.game.action.Action;

public record PublicPlay(
    Seat seat,
    Action action
) {

  public PublicPlay {
    Objects.requireNonNull(seat, "제출한 자리가 필요합니다.");
    Objects.requireNonNull(action, "제출 행동이 필요합니다.");
  }

}
