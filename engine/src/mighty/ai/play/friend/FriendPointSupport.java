package mighty.ai.play.friend;

import java.util.*;
import mighty.ai.play.*;
import mighty.game.action.Action;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.*;

/**
 * 강제 점수 제출 위험을 줄이되 주공 운영 문양의 회수 연결은 남깁니다.
 */
final class FriendPointSupport {

  private FriendPointSupport() {
  }

  static Optional<PlayDecision> choose(View view, PlayFacts facts, List<Action> legal) {
    var winner = facts.currentWinner().orElseThrow();
    boolean ruff = winner.action().card() instanceof StandardCard c
        && view.contract().trump().matches(c.suit()) && c.suit() != facts.conditions().leadSuit();
    if (!facts.allyWinning() || !(facts.isSecured(winner) || facts.isSuitTop(winner.action().card())
        || ruff || winner.action().card().equals(Rules.mighty(view.contract().trump()))
        || winner.action().card() instanceof Joker)) {
      return Optional.empty();
    }
    FriendResources resources = new FriendResources(view, facts);
    int strength = Rules.strength(winner.action().card(), facts.conditions());
    return PlaySupport.ordinary(legal, view).stream().filter(a -> a.card().points() > 0)
        .filter(a -> PointExposurePlan.allows(view, facts, a))
        .filter(a -> !view.contract().trump().matches(((StandardCard) a.card()).suit()))
        .filter(a -> Rules.strength(a.card(), facts.conditions()) <= strength)
        .filter(a -> !resources.preserve((StandardCard) a.card()))
        .min(Comparator.comparingInt(a -> resources.supportCost((StandardCard) a.card())))
        .map(a -> new PlayDecision(a, "물패가 적어 강제로 점수가 실릴 문양부터 지원하고 주공의 연결 문양은 보존합니다."));
  }

}
