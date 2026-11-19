package mighty.ai.play.friend;

import java.util.List;
import java.util.Optional;
import mighty.ai.play.PlayDecision;
import mighty.ai.play.PlayFacts;
import mighty.game.action.Action;
import mighty.game.friend.CardFriend;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.Rank;
import mighty.model.StandardCard;
import mighty.model.Trump;

/**
 * 노기루 지정 A로 요청에 응답하고 후속 탑카를 사용할 기회를 확보합니다.
 */
final class FriendAceRun {

  private FriendAceRun() {
  }

  static Optional<PlayDecision> answer(View view, PlayFacts facts, List<Action> legal) {
    var ace = designatedAce(view);
    if (ace.isEmpty() || !view.trick().get(0).seat().equals(view.context().declarer())
        || facts.conditions().leadSuit() != ace.get().suit()) {
      return Optional.empty();
    }
    boolean hasNextTop = view.hand().stream()
        .filter(StandardCard.class::isInstance).map(StandardCard.class::cast)
        .anyMatch(card -> card.suit() == ace.get().suit() && !card.equals(ace.get())
            && card.points() > 0 && facts.isSuitTop(card));
    if (!hasNextTop) {
      return Optional.empty();
    }
    return legal.stream().filter(action -> action.card().equals(ace.get()))
        .filter(facts.winningActions()::contains).findFirst()
        .map(action -> new PlayDecision(action, "지정 A로 주공의 요청을 받고 연속 탑카를 먼저 처리합니다."));
  }

  private static Optional<StandardCard> designatedAce(View view) {
    if (view.contract().trump() == Trump.NO_TRUMP
        && view.friendSelection() instanceof CardFriend friend
        && friend.card() instanceof StandardCard ace && ace.rank() == Rank.ACE
        && !ace.equals(Rules.mighty(Trump.NO_TRUMP))) {
      return Optional.of(ace);
    }
    return Optional.empty();
  }

}
