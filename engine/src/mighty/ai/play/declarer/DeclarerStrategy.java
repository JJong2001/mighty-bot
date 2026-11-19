package mighty.ai.play.declarer;

import java.util.List;
import mighty.ai.play.PlayDecision;
import mighty.ai.play.PlayFacts;
import mighty.ai.play.RoleStrategy;
import mighty.game.Role;
import mighty.game.action.Action;
import mighty.game.observation.View;

/**
 * 주공 전용 진입점. 관측마다 공개된 진행을 다시 읽어 운영을 조정합니다.
 */
public final class DeclarerStrategy implements RoleStrategy {

  @Override
  public PlayDecision choose(View view, PlayFacts facts, List<Action> legal) {
    if (view.role() != Role.DECLARER) {
      throw new IllegalArgumentException("주공 전략에는 주공 관측이 필요합니다.");
    }
    DeclarerPosition position = new DeclarerPosition(view, facts, legal);
    if (view.trick().isEmpty()) {
      return DeclarerLeadStrategy.choose(position);
    }
    return DeclarerFollowStrategy.choose(position);
  }

}
