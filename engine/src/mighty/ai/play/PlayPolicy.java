package mighty.ai.play;

import mighty.game.action.Action;
import mighty.game.observation.View;

/**
 * 현재 행동자의 자기 손패와 공개 정보로 제출할 행동을 결정합니다.
 */
public interface PlayPolicy {

  Action choose(View view);
}
