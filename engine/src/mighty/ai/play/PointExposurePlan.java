package mighty.ai.play;

import java.util.HashSet;
import mighty.game.Role;
import mighty.game.action.Action;
import mighty.game.observation.View;
import mighty.model.StandardCard;

/**
 * 조커 공격 가능성과 뒤자리의 점수 여유를 같은 기준으로 점수 지원에 반영합니다.
 */
public final class PointExposurePlan {

  private PointExposurePlan() {
  }

  public static boolean allows(View view, PlayFacts facts, Action candidate) {
    if (view.role() != Role.FRIEND || candidate.card().points() == 0
        || !facts.joker().likelyHeldByOpposition() || !facts.allyWinning()
        || !facts.currentWinner().orElseThrow().seat().equals(view.context().declarer())) {
      return true;
    }
    var behind = new HashSet<>(facts.remainingSeats().stream()
        .filter(seat -> !facts.teams().isAlly(seat)).toList());
    int remainingPoints = behind.size();
    if (facts.joker().likelyAt(behind)) {
      remainingPoints = Math.max(0, remainingPoints - 1);
    }
    if (facts.trickPoints() + candidate.card().points() + remainingPoints <= 3) {
      return true;
    }
    // 물패로 따르거나 버릴 수 없으면 점수 상한을 강제로 지키지는 않습니다.
    return facts.passingActions(PlaySupport.ordinary(facts.legalActions(), view)).stream()
        .noneMatch(a -> a.card() instanceof StandardCard && a.card().points() == 0);
  }
}
