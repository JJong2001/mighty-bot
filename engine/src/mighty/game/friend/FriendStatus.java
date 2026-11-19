package mighty.game.friend;

public sealed interface FriendStatus
    permits UnrevealedFriend, RevealedFriend, NoFriend {

}
