package mighty.ai.play;

import java.util.List;
import java.util.Optional;
import mighty.game.action.Action;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.StandardCard;

/**
 * 동료 카드와 자기 높은 카드 사이의 외부 카드가 없는지 카운팅으로 비교합니다.
 */
public final class EquivalentPressure {

  private EquivalentPressure() {
  }

  public static Optional<PlayDecision> pass(View view, PlayFacts facts, List<Action> legal) {
    var winner = facts.currentWinner().orElseThrow();
    if (!facts.allyWinning() || !(winner.action().card() instanceof StandardCard ally)
        || ally.suit() != facts.conditions().leadSuit()
        || ally.equals(Rules.mighty(view.contract().trump()))) {
      return Optional.empty();
    }
    var higher = PlaySupport.ordinary(legal, view).stream()
        .map(a -> (StandardCard) a.card()).filter(c -> c.suit() == ally.suit())
        .filter(c -> c.rank().value() > ally.rank().value()).toList();
    if (higher.isEmpty()) {
      return Optional.empty();
    }
    int highest = higher.stream().mapToInt(c -> c.rank().value()).max().orElseThrow();
    boolean gainsPressure = facts.cards().outsideCards().stream()
        .filter(StandardCard.class::isInstance).map(StandardCard.class::cast)
        .filter(c -> !c.equals(Rules.mighty(view.contract().trump())))
        .anyMatch(c -> c.suit() == ally.suit() && c.rank().value() > ally.rank().value()
            && c.rank().value() <= highest);
    if (gainsPressure) {
      return Optional.empty();
    }
    var passing = PlaySupport.ordinary(legal, view).stream()
        .filter(a -> Rules.strength(a.card(), facts.conditions())
            <= Rules.strength(ally, facts.conditions())).toList();
    return passing.isEmpty() ? Optional.empty() : Optional.of(new PlayDecision(
        PlaySupport.cheapest(passing, view), "동료가 동일한 외부 상위를 이미 압박하므로 높은 카드를 겹치지 않습니다."));
  }

}
