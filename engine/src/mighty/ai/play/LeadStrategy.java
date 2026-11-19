package mighty.ai.play;

import java.util.List;
import mighty.game.action.Action;
import mighty.game.observation.View;

/**
 * 역할이 아직 정해지지 않은 경우의 기본 선 선택입니다. 역할별 운영은 각 전략이 담당합니다.
 */
final class LeadStrategy {

  private LeadStrategy() {
  }

  static PlayDecision choose(View view, PlayFacts facts, List<Action> legal) {
    List<Action> ordinary = PlaySupport.ordinary(legal, view);
    List<Action> masters = ordinary.stream().filter(facts::isMasterLead).toList();
    return new PlayDecision(PlaySupport.cheapest(!masters.isEmpty() ? masters
        : !ordinary.isEmpty() ? ordinary : legal, view), "미확정 역할에서는 확인된 승리와 낮은 일반 카드를 우선합니다.");
  }

}
