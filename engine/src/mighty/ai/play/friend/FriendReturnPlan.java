package mighty.ai.play.friend;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import mighty.ai.play.FriendSupportIntent;
import mighty.ai.play.JokerSuitPlan;
import mighty.ai.play.PlayDecision;
import mighty.ai.play.PlayFacts;
import mighty.ai.play.PlaySupport;
import mighty.ai.play.TrumpControl;
import mighty.game.action.Action;
import mighty.game.observation.View;
import mighty.model.StandardCard;
import mighty.model.Suit;
import mighty.model.Trump;

/**
 * 반환 문양과 카드 순서를 함께 결정합니다. 구 번호와 지정 카드의 숫자는 사용하지 않습니다.
 */
final class FriendReturnPlan {

  private FriendReturnPlan() {
  }

  static Optional<PlayDecision> choose(View view, PlayFacts facts, List<Action> ordinary) {
    var suits = new LinkedHashSet<Suit>();
    if (view.contract().trump() == Trump.NO_TRUMP) {
      FriendSupportIntent.returnSuit(view).ifPresent(suits::add);
      suits.addAll(PlaySupport.returnSuits(view));
    } else {
      mighty.game.bidding.PublicContractHistory.suitOf(view.contract().trump())
          .ifPresent(suits::add);
    }

    for (Suit suit : suits) {
      if (!FriendReturnSafety.allows(suit, view, facts)) {
        continue;
      }
      if (view.contract().trump().matches(suit) && !canReturnTrump(view, facts)) {
        continue;
      }
      var returning = ordinary.stream()
          .filter(action -> ((StandardCard) action.card()).suit() == suit)
          .toList();
      if (!returning.isEmpty()) {
        return Optional.of(new PlayDecision(PlaySupport.highest(returning),
            "공개된 운영·요청 문양을 높은 카드부터 반환해 주공에게 서열을 알립니다."));
      }
    }
    return Optional.empty();
  }

  private static boolean canReturnTrump(View view, PlayFacts facts) {
    var control = new TrumpControl(view, facts);
    if (control.outside().isEmpty() || !JokerSuitPlan.enemiesMayHoldTrump(view, facts)) {
      return false;
    }
    if (control.own().stream()
        .anyMatch(card -> facts.cards().isVoid(view.context().declarer(), card.suit()))) {
      return false;
    }
    long rounds = view.context().history().stream().filter(trick -> view.contract().trump().matches(
        mighty.game.rule.Rules.conditionsFor(view.contract().trump(), trick.number(),
            trick.cards().get(0).action()).leadSuit())).count();
    return rounds < 2 || !control.shouldPreserveLastTrump();
  }
}
