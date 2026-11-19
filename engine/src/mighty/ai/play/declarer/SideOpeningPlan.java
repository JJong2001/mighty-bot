package mighty.ai.play.declarer;

import java.util.*;
import mighty.ai.play.*;
import mighty.game.action.Action;
import mighty.game.observation.View;
import mighty.model.*;

/**
 * 부기루 중간 점수를 프렌드에게 처리하고 높은 카드·낮은 탐색 카드로 전개를 이어갑니다.
 */
final class SideOpeningPlan {

  private SideOpeningPlan() {
  }

  static Optional<Action> feed(DeclarerPosition position) {
    if (position.sides().stream().anyMatch(a -> ((StandardCard) a.card()).rank() == Rank.ACE)) {
      return Optional.empty();
    }
    for (Suit suit : Suit.values()) {
      var actions = position.sides().stream().filter(a -> ((StandardCard) a.card()).suit() == suit)
          .toList();
      var points = actions.stream()
          .filter(a -> a.card().points() > 0 && !position.facts().isSuitTop(a.card()))
          .sorted(Comparator.comparingInt(a -> ((StandardCard) a.card()).rank().value())).toList();
      if (points.size() >= 2 && actions.stream().anyMatch(a -> a.card().points() == 0)) {
        return Optional.of(points.get(0));
      }
    }
    return Optional.empty();
  }

  static boolean continuing(DeclarerPosition position, SuitDevelopment plan) {
    if (plan.held().size() < 2 || plan.blockers().size() > plan.held().size()) {
      return false;
    }
    return position.view().context().history().stream().filter(t -> t.number().value() == 1
            && t.leader().equals(position.view().self()) && position.facts().teams().isAlly(t.winner()))
        .anyMatch(t -> t.cards().get(0).action().card() instanceof StandardCard card
            && card.suit() == plan.suit()
            && card.points() > 0 && card.rank().value() < plan.held().get(0).rank().value());
  }

}
