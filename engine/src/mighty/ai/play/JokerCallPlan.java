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
    if (view.role() == mighty.game.Role.DECLARER && view.trickNumber().isFirst()
        && view.friendSelection() instanceof mighty.game.friend.CardFriend friend
        && friend.card().equals(Rules.mighty(view.contract().trump()))) {
      return Optional.empty();
    }
    if (view.role() == mighty.game.Role.OPPOSITION && facts.joker().likelyHeldByOpposition()) {
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

  /**
   * 총을 쓸 수 있도록 초구의 자력 승리를 먼저 확보합니다. 조커프·초구프 계획은 별도입니다.
   */
  public static Optional<Action> openingWinner(View view, PlayFacts facts, List<Action> legal) {
    if (view.role() != mighty.game.Role.DECLARER || !view.trickNumber().isFirst()
        || view.hand().contains(Joker.INSTANCE) || !view.hand()
        .contains(Rules.gun(view.contract().trump()))
        || !facts.cards().outsideCards().contains(Joker.INSTANCE)
        || view.friendSelection() == mighty.game.friend.SpecialFriend.FIRST_TRICK
        || view.friendSelection() instanceof mighty.game.friend.CardFriend friend
        && friend.card() instanceof Joker) {
      return Optional.empty();
    }
    return PlaySupport.ordinary(legal, view).stream()
        .filter(a -> a instanceof StandardPlay play && !play.call())
        .filter(
            a -> !view.contract().trump().matches(((mighty.model.StandardCard) a.card()).suit()))
        .filter(a -> !(view.friendSelection() instanceof mighty.game.friend.CardFriend friend
            && friend.card().equals(Rules.mighty(view.contract().trump())))
            || ((mighty.model.StandardCard) a.card()).rank() == mighty.model.Rank.ACE)
        .filter(
            a -> !a.card().equals(Rules.gun(view.contract().trump())) && facts.isSuitTop(a.card()))
        .min(Comparator.comparingInt(a -> ((mighty.model.StandardCard) a.card()).rank().value()));
  }

}
