package mighty.game.observation;

import java.util.List;
import mighty.game.bidding.Bid;
import mighty.game.Role;
import mighty.game.Seat;
import mighty.game.friend.FriendSelection;
import mighty.game.trick.PublicPlay;

public final class ObservationFactory {

  private ObservationFactory() {
  }

  public static View create(
      Seat self,
      PrivateInfo privateInfo,
      Bid contract,
      FriendSelection selection,
      Context context,
      List<PublicPlay> trick
  ) {
    Role role = RoleResolver.resolve(
        self,
        privateInfo,
        selection,
        context
    );
    return new View(
        self,
        privateInfo,
        role,
        contract,
        selection,
        context,
        trick
    );
  }

}
