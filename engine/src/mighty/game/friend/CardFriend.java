package mighty.game.friend;

import java.util.Objects;
import mighty.model.Card;

public record CardFriend(Card card) implements FriendSelection {

  public CardFriend {
    Objects.requireNonNull(card, "프렌드 지정 카드가 필요합니다.");
  }

}
