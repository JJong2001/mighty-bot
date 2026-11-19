package mighty.ai.play.opposition;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import mighty.ai.play.PlayDecision;
import mighty.ai.play.PlayFacts;
import mighty.game.Seat;
import mighty.game.action.Action;
import mighty.game.friend.NoFriend;
import mighty.game.friend.RevealedFriend;
import mighty.game.observation.View;
import mighty.model.Card;
import mighty.model.StandardCard;
import mighty.model.Trump;

/**
 * 주공이 점수 카드를 받지 않으면 공약에 실패하는 경계에서 부기루 무기루를 점수 리드로 압박합니다.
 */
final class ContractFailureLead {

  private ContractFailureLead() {
  }

  static Optional<PlayDecision> choose(View view, PlayFacts facts, List<Action> ordinary) {
    if (view.contract().trump() == Trump.NO_TRUMP) {
      return Optional.empty();
    }

    int target = view.contract().points();
    int capturedUpperBound = capturedDeclarerTeamUpperBound(view);
    int unplayedPoints = view.hand().stream().mapToInt(Card::points).sum()
        + facts.cards().outsideCards().stream().mapToInt(Card::points).sum();
    if (unplayedPoints == 0
        || capturedUpperBound + unplayedPoints < target
        || capturedUpperBound + unplayedPoints - 1 >= target) {
      return Optional.empty();
    }

    Seat declarer = view.context().declarer();
    double expectedDeclarerTrumps = facts.cards().trumps().expectedCount(declarer, facts);
    if (expectedDeclarerTrumps < 0.5) {
      return Optional.empty();
    }

    return ordinary.stream()
        .filter(action -> action.card() instanceof StandardCard card && card.points() == 1)
        .filter(action -> {
          StandardCard card = (StandardCard) action.card();
          return facts.cards().isVoid(declarer, card.suit());
        })
        .min(Comparator.comparingInt(action -> ((StandardCard) action.card()).rank().value()))
        .map(action -> new PlayDecision(action,
            "공개 점수와 남은 점수의 보수적 상한상 이 점수를 받지 않으면 공약에 못 미칩니다. "
                + "주공의 무기루 문양을 점수로 열어 기루 응수를 압박합니다."));
  }

  /**
   * 숨은 프렌드의 획득 점수는 확인된 수치로 단정하지 않고 가능한 가장 큰 포획을 더합니다.
   */
  private static int capturedDeclarerTeamUpperBound(View view) {
    Seat declarer = view.context().declarer();
    int declarerPoints = view.context().captured(declarer);
    if (view.context().friendStatus() instanceof NoFriend) {
      return declarerPoints;
    }
    if (view.context().friendStatus() instanceof RevealedFriend friend) {
      return declarerPoints + view.context().captured(friend.seat());
    }
    int possibleHiddenFriendCapture = view.context().playOrder().seats().stream()
        .filter(seat -> !seat.equals(declarer))
        .mapToInt(view.context()::captured)
        .max()
        .orElse(0);
    return declarerPoints + possibleHiddenFriendCapture;
  }
}
