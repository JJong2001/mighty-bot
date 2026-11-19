package mighty.ai.play;

import java.util.List;
import mighty.game.action.Action;
import mighty.game.observation.View;

/**
 * 자기 관측과 공통 사실로 역할별 행동 및 이유를 결정하는 계약입니다.
 */
public interface RoleStrategy {

  PlayDecision choose(View view, PlayFacts facts, List<Action> legal);
}
