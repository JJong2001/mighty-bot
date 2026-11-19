package mighty.ai.preparation;

import mighty.game.friend.FriendSelection;
import mighty.game.observation.FriendView;

/**
 * 주공의 자기 정보로 프렌드 지정 방식을 선택합니다.
 */
public interface FriendPolicy {

  FriendSelection choose(FriendView view);
}
