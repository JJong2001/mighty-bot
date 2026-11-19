package mighty.game.friend;

import java.util.Objects;
import mighty.game.Seat;

public record RevealedFriend(Seat seat) implements FriendStatus {

  public RevealedFriend {
    Objects.requireNonNull(seat, "프렌드 자리가 필요합니다.");
  }

}
