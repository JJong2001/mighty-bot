package mighty.game.observation;

import mighty.game.Role;
import mighty.game.Seat;
import mighty.game.friend.CardFriend;
import mighty.game.friend.FriendSelection;
import mighty.game.friend.NoFriend;
import mighty.game.friend.RevealedFriend;
import mighty.game.friend.SpecialFriend;

final class RoleResolver {

  private RoleResolver() {
  }

  static Role resolve(
      Seat self,
      PrivateInfo privateInfo,
      FriendSelection selection,
      Context context
  ) {
    if (self.equals(context.declarer())) {
      return Role.DECLARER;
    }
    if (context.friendStatus() instanceof RevealedFriend revealed) {
      return self.equals(revealed.seat())
          ? Role.FRIEND
          : Role.OPPOSITION;
    }
    if (context.friendStatus() instanceof NoFriend) {
      return Role.OPPOSITION;
    }
    if (selection == SpecialFriend.FIRST_TRICK) {
      if (!context.history().isEmpty()) {
        throw new IllegalStateException(
            "초구가 끝났는데 프렌드 상태가 확정되지 않았습니다."
        );
      }
      return Role.UNDETERMINED;
    }
    if (selection instanceof CardFriend cardFriend) {
      boolean heldOrPlayed = privateInfo.hand().contains(cardFriend.card())
          || context.history().stream().flatMap(trick -> trick.cards().stream())
          .anyMatch(play -> play.seat().equals(self)
              && play.action().card().equals(cardFriend.card()));
      return heldOrPlayed ? Role.FRIEND : Role.OPPOSITION;
    }
    throw new IllegalStateException(
        "프렌드 지정 방식과 공개 상태가 일치하지 않습니다."
    );
  }

}
