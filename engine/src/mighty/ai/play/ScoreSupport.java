package mighty.ai.play;

import java.util.*;
import mighty.game.observation.View;
import mighty.game.action.Action;

/**
 * 현재 점수 지원의 필요성을 계산합니다. 미공개 프렌드의 실제 소유자는 조회하지 않습니다.
 */
public final class ScoreSupport {

  private ScoreSupport() {
  }

  /**
   * 여당의 보존 전략도 이번 공개 점수를 내주면 공약이 실패하는 경우에는 회수해야 합니다.
   */
  public static boolean lossWouldDefeatContract(View view, PlayFacts facts) {
    if (view.role() != mighty.game.Role.DECLARER && view.role() != mighty.game.Role.FRIEND) {
      return false;
    }
    int opposition = view.context().history().stream()
        .filter(trick -> !facts.teams().isAlly(trick.winner()))
        .mapToInt(trick -> trick.points()).sum();
    return opposition + facts.trickPoints() >= 21 - view.contract().points();
  }

  public static Optional<Action> decisive(View view, PlayFacts facts, List<Action> legal) {
    if (!facts.allyWinning() || !facts.isSecured(facts.currentWinner().orElseThrow())) {
      return Optional.empty();
    }
    boolean opposition = view.role() == mighty.game.Role.OPPOSITION;
    int captured = view.context().history().stream()
        .filter(t -> facts.teams().isAlly(t.winner())).mapToInt(t -> t.points()).sum();
    int current = facts.trickPoints();
    int target = opposition ? 21 - view.contract().points() : view.contract().points();
    // 야당은 바닥 점수를 모릅니다. 여당도 이 경로에서는 공개 확보분만 사용합니다.
    if (captured + current >= target) {
      return Optional.empty();
    }
    if (captured + current + 1 < target && !mustFeedNow(view, facts, captured, current, target)) {
      return Optional.empty();
    }
    var resources = new CardResources(view, facts);
    return PlaySupport.ordinary(legal, view).stream().filter(a -> a.card().points() == 1)
        .min(Comparator.comparingInt(a -> resources.preservationCost(a.card())));
  }

  /**
   * 이번 한 점으로 끝나지 않아도 마지막 공격 기회를 놓치면 실패 점수가 부족해질 수 있습니다.
   */
  private static boolean mustFeedNow(View view, PlayFacts facts, int captured, int current,
      int target) {
    if (view.role() != mighty.game.Role.OPPOSITION) {
      return false;
    }
    boolean lastSide = facts.cards().trumps().likelyLastSide(view.context().declarer(), facts);
    int needed = target - captured - current;
    // 다음 한 차례에서 주공·프렌드의 추가 점수까지 당연히 받을 것으로 가정하지 않습니다.
    return lastSide && needed > 3;
  }

}
