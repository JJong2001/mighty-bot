package mighty.ai.play;

import java.util.Objects;
import mighty.game.action.Action;

/**
 * 선택한 행동과 당시 관측에 대한 설명입니다.
 */
public record PlayDecision(Action action, String reason) {

  public PlayDecision {
    Objects.requireNonNull(action);
    Objects.requireNonNull(reason);
  }

}
