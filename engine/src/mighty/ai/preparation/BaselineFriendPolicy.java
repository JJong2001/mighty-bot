package mighty.ai.preparation;

import java.util.Objects;
import mighty.game.friend.CardFriend;
import mighty.game.friend.FriendSelection;
import mighty.game.friend.SpecialFriend;
import mighty.game.observation.FriendView;
import mighty.game.rule.Rules;
import mighty.model.Card;
import mighty.model.Joker;

/**
 * 자기 손에 없는 마이티, 조커, 초구 프렌드 순서로 선택하는 기준 정책입니다.
 */
public final class BaselineFriendPolicy implements FriendPolicy {

  @Override
  public FriendSelection choose(FriendView view) {
    Objects.requireNonNull(view, "프렌드 관측이 필요합니다.");
    Card mighty = Rules.mighty(view.contract().trump());
    if (!ownsOrDiscarded(view, mighty)) {
      return new CardFriend(mighty);
    }
    if (!ownsOrDiscarded(view, Joker.INSTANCE)) {
      return new CardFriend(Joker.INSTANCE);
    }
    return SpecialFriend.FIRST_TRICK;
  }

  /**
   * 다른 참가자의 실제 패를 확인하지 않고 자기 정보만 조회합니다.
   */
  private static boolean ownsOrDiscarded(FriendView view, Card card) {
    return view.hand().contains(card) || view.discardedCards().contains(card);
  }

}
