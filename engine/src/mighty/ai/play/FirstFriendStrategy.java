package mighty.ai.play;

import java.util.*;
import mighty.game.action.Action;
import mighty.game.friend.SpecialFriend;
import mighty.game.observation.View;
import mighty.game.trick.PublicPlay;
import mighty.game.rule.Rules;
import mighty.model.StandardCard;

/**
 * 초구프에서는 미확정 역할의 일반 수비보다 프렌드에 타는 승리를 우선합니다.
 */
final class FirstFriendStrategy {

  private FirstFriendStrategy() {
  }

  static Optional<PlayDecision> choose(View view, PlayFacts facts, List<Action> legal) {
    if (view.trickNumber().value() != 1 || view.trick().isEmpty()
        || view.self().equals(view.context().declarer())
        || view.friendSelection() != SpecialFriend.FIRST_TRICK) {
      return Optional.empty();
    }
    List<Action> winners = facts.winningActions().stream().filter(legal::contains).toList();
    if (winners.isEmpty()) {
      return Optional.empty();
    }
    List<Action> ordinary = PlaySupport.ordinary(winners, view);
    var ruffs = ordinary.stream().filter(action -> action.card() instanceof StandardCard card
        && view.contract().trump().matches(card.suit())
        && !view.contract().trump().matches(facts.conditions().leadSuit())).toList();
    if (!ruffs.isEmpty()) {
      return Optional.of(new PlayDecision(PlaySupport.cheapest(ruffs, view),
          "초구프에 탈 때는 낮은 승리 기루로 간치고 높은 기루를 이후 운영에 남깁니다."));
    }
    boolean secureWinner = ordinary.stream().anyMatch(action -> facts.isSuitTop(action.card())
        || facts.isSecured(new PublicPlay(view.self(), action)));
    if (!facts.remainingSeats().isEmpty() && !ordinary.isEmpty() && !secureWinner
        && winners.size() == ordinary.size()) {
      var low = PlaySupport.ordinary(legal, view).stream()
          .filter(action -> action.card().points() == 0).toList();
      if (!low.isEmpty()) {
        Action high = PlaySupport.highest(ordinary);
        var card = (StandardCard) high.card();
        var holding = PlaySupport.ordinary(legal, view).stream()
            .filter(action -> ((StandardCard) action.card()).suit() == card.suit()).toList();
        long higher = facts.cards().outsideCards().stream()
            .filter(other -> Rules.strength(other, facts.conditions())
                > Rules.strength(card, facts.conditions())).count();
        long points = holding.stream().filter(action -> action.card().points() > 0).count();
        // 상위가 나올 때 숫자로 버틸 수 있으면 높은 수비 카드를 남깁니다.
        boolean canGuard = holding.size() > higher + 1;
        if (high.card().points() > 0 && points >= 2 && !canGuard) {
          return Optional.of(new PlayDecision(high,
              "남기기 어려운 높은 점수로 초구를 경쟁하고 뒤의 낮은 승리를 막습니다."));
        }
        var lowWinners = low.stream().filter(winners::contains).toList();
        Action choice = lowWinners.isEmpty() ? PlaySupport.cheapest(low, view)
            : PlaySupport.highest(lowWinners);
        return Optional.of(new PlayDecision(choice,
            "낮은 카드로 초구를 경쟁하거나 이후에 쓸 높은 수비를 남깁니다."));
      }
    }
    return Optional.of(new PlayDecision(ordinary.isEmpty() ? PlaySupport.cheapest(winners, view)
        : view.trick().size() == 4 ? PlaySupport.cheapest(ordinary, view)
            : PlaySupport.highest(ordinary),
        "초구를 이겨 프렌드에 탑니다. 입증되지 않은 백런 기대 때문에 양보하지 않습니다."));
  }

}
