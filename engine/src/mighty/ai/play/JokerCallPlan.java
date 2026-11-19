package mighty.ai.play;

import java.util.*;
import mighty.game.action.*;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.Joker;

/**
 * 자기 조커를 죽이지 않으며, 합법인 선에서 미소모 외부 조커의 시점을 강제합니다.
 */
public final class JokerCallPlan {

  private JokerCallPlan() {
  }

  public static Optional<Action> call(View view, PlayFacts facts, List<Action> legal) {
    if (!view.trick().isEmpty() || view.hand().contains(Joker.INSTANCE)
        || !facts.cards().outsideCards().contains(Joker.INSTANCE)) {
      return Optional.empty();
    }
    if (view.role() == mighty.game.Role.DECLARER
        && view.friendSelection() instanceof mighty.game.friend.CardFriend friend
        && friend.card() instanceof Joker) {
      return Optional.empty();
    }
    return legal.stream().filter(a -> a instanceof StandardPlay p && p.call()
        && a.card().equals(Rules.gun(view.contract().trump()))).findFirst();
  }

}
