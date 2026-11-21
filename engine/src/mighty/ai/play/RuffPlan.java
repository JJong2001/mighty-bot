package mighty.ai.play;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import mighty.game.action.Action;
import mighty.game.observation.View;
import mighty.model.StandardCard;

/**
 * 여야 공통: 버티지 못할 점수 기루는 간에 쓰고, 살아남을 높은 기루는 보존합니다.
 */
public final class RuffPlan {

  private RuffPlan() {
  }

  public static Optional<Action> choose(View view, PlayFacts facts, List<Action> actions) {
    if (view.trick().isEmpty() || view.contract().trump().matches(facts.conditions().leadSuit())) {
      return Optional.empty();
    }
    if (facts.allyWinning()) {
      var winner = facts.currentWinner().orElseThrow();
      boolean needsFriendSupport = view.role() == mighty.game.Role.FRIEND
          && winner.seat().equals(view.context().declarer())
          && !facts.isSecured(winner) && !facts.isSuitTop(winner.action().card())
          && winner.action().card() instanceof StandardCard c
          && !view.contract().trump().matches(c.suit());
      if (!needsFriendSupport) {
        return Optional.empty();
      }
    }
    var ruffs = PlaySupport.ordinary(actions, view).stream()
        .filter(facts.winningActions()::contains)
        .filter(a -> view.contract().trump().matches(((StandardCard) a.card()).suit())).toList();
    if (ruffs.isEmpty()) {
      return Optional.empty();
    }
    TrumpSurvival survival = new TrumpSurvival(view, facts);
    var exposedPoints = ruffs.stream().filter(a -> a.card().points() > 0)
        .filter(a -> !survival.canPreserveWhenRuffing((StandardCard) a.card())).toList();
    if (!exposedPoints.isEmpty()) {
      // 외부의 같은 상위에 막힌 연결 점수는 높은 것으로 간쳐 서열을 보입니다.
      var anchor = (StandardCard) exposedPoints.get(0).card();
      return ruffs.stream().filter(a -> a.card().points() > 0)
          .filter(a -> exposedPoints.contains(a)
              || survival.threatsAbove((StandardCard) a.card()) == survival.threatsAbove(anchor))
          .max(Comparator.comparingInt(a -> ((StandardCard) a.card()).rank().value()));
    }
    // 미확인 뒤자리의 모든 상위 카드를 이긴다는 이유로 A간을 강제하지 않습니다.
    return Optional.of(PlaySupport.cheapest(ruffs, view));
  }

}
