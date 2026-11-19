package mighty.ai.play.opposition;

import java.util.List;
import mighty.ai.play.PlayDecision;
import mighty.ai.play.PlayFacts;
import mighty.ai.play.RoleStrategy;
import mighty.game.action.Action;
import mighty.game.observation.View;

/**
 * 야당의 선은 공격 문양, 후수는 압박·점수 확보·수비 보존을 책임집니다.
 */
public final class OppositionStrategy implements RoleStrategy {

  @Override
  public PlayDecision choose(View view, PlayFacts facts, List<Action> legal) {
    return view.trick().isEmpty()
        ? OppositionLeadStrategy.choose(view, facts, legal)
        : OppositionFollowStrategy.choose(view, facts, legal);
  }
}
