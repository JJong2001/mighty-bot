package mighty.ai.play.friend;

import java.util.List;
import mighty.ai.play.PlayDecision;
import mighty.ai.play.PlayFacts;
import mighty.ai.play.RoleStrategy;
import mighty.game.action.Action;
import mighty.game.observation.View;

/**
 * 프렌드의 선은 반환, 후수는 주공 요청과 승리 지원을 책임집니다.
 */
public final class FriendStrategy implements RoleStrategy {

  @Override
  public PlayDecision choose(View view, PlayFacts facts, List<Action> legal) {
    return view.trick().isEmpty()
        ? FriendLeadStrategy.choose(view, facts, legal)
        : FriendFollowStrategy.choose(view, facts, legal);
  }
}