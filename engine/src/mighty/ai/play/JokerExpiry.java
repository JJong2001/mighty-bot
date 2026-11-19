package mighty.ai.play;

import java.util.*;
import mighty.game.action.*;
import mighty.game.observation.View;
import mighty.model.Joker;

/**
 * 역할별 운영과 동료 보호가 조커의 마지막 활성 차례를 우회하지 않도록 공통 진입점에서 처리합니다.
 */
final class JokerExpiry {

  private JokerExpiry() {
  }

  static Optional<PlayDecision> choose(View view, PlayFacts facts, List<Action> legal) {
    if (view.trickNumber().value() != 9) {
      return Optional.empty();
    }
    if (FriendSupportIntent.preserveJoker(view, facts)) {
      return Optional.empty();
    }
    var jokers = legal.stream().filter(a -> a.card() instanceof Joker).toList();
    if (jokers.isEmpty()) {
      return Optional.empty();
    }
    if (view.trick().isEmpty()) {
      return Optional.of(new PlayDecision(PlaySupport.jokerLead(jokers, view, facts),
          "9구 조커 사용 기한을 역할별 운영보다 먼저 처리합니다."));
    }
    boolean wins = facts.winningActions().stream().anyMatch(a -> a.card() instanceof Joker);
    // 콜 등으로 조커가 죽었고 다른 카드로 이길 수 있다면 기한만으로 승리를 포기하지 않습니다.
    if (wins || facts.allyWinning() || facts.winningActions().isEmpty()) {
      return Optional.of(
          new PlayDecision(jokers.get(0), "조커를 10구에 남기지 않고 마지막 활성 기회에 사용합니다."));
    }
    return Optional.empty();
  }

}
