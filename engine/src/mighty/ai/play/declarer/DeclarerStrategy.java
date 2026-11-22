package mighty.ai.play.declarer;

import java.util.List;
import java.util.Optional;
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

  public Optional<PlayDecision> spreadTopLead(View view, PlayFacts facts, List<Action> legal) {
    if (view.role() != Role.DECLARER || !view.trick().isEmpty()
        || (view.contract().points() != 16 && view.contract().points() != 17)
        || view.contract().trump() != mighty.model.Trump.NO_TRUMP) {
      return Optional.empty();
    }
    var position = new DeclarerPosition(view, facts, legal);
    if (HighNoTrumpPlay.leadToBidder(position).isPresent()) {
      return Optional.empty();
    }
    return NoTrumpOperations.cashSideTopsBeforeFriend(position);
  }

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
