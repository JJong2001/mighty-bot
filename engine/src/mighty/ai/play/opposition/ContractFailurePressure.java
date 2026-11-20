package mighty.ai.play.opposition;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import mighty.ai.play.PlayDecision;
import mighty.ai.play.PlayFacts;
import mighty.ai.play.PlaySupport;
import mighty.game.Seat;
import mighty.game.action.Action;
import mighty.game.action.StandardPlay;
import mighty.game.friend.NoFriend;
import mighty.game.friend.RevealedFriend;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.game.rule.TrickConditions;
import mighty.model.Card;
import mighty.model.StandardCard;
import mighty.model.Trump;

/**
 * 공약 실패에 필요한 점수와 주공 뒤 야당의 추가 득점 가능성을 보고 압박을 나눕니다.
 */
final class ContractFailurePressure {

  private ContractFailurePressure() {}

  static Optional<PlayDecision> lead(View view, PlayFacts facts, List<Action> ordinary) {
    int needed = pointsToDefeat(view, facts);
    if (needed < 1 || needed > 2 || !declarerMayRuff(view, facts)) {
      return Optional.empty();
    }

    return ordinary.stream().filter(action -> action.card().points() == 1)
        .map(action -> (StandardPlay) action)
        .filter(action -> facts.cards().isVoid(view.context().declarer(), action.card().suit()))
        .filter(action -> needed == 1 || mayAddPointBehindDeclarer(view, facts,
                Rules.conditionsFor(view.contract().trump(), view.trickNumber(), action),
                action.card()))
        .min(Comparator.comparingInt(action -> action.card().rank().value()))
        .map(action -> new PlayDecision(action, needed == 1
            ? "공개 점수와 남은 점수의 보수적 상한상 이 점수를 받지 않으면 공약에 못 미칩니다. "
                + "주공의 무기루 문양을 점수로 열어 기루 응수를 압박합니다."
            : "주공 뒤 야당이 점수를 더하면 공약을 막을 수 있어 한 점으로 먼저 압박합니다."));
  }

  static Optional<PlayDecision> follow(View view, PlayFacts facts, List<Action> legal) {
    var declarer = view.context().declarer();
    var conditions = facts.conditions();
    if (!facts.allyWinning() || !facts.remainingSeats().contains(declarer)
        || view.contract().trump().matches(conditions.leadSuit())
        || !facts.cards().isVoid(declarer, conditions.leadSuit())
        || facts.trickPoints() > 1 || pointsToDefeat(view, facts) != 2
        || !declarerMayRuff(view, facts)) {
      return Optional.empty();
    }

    Card winner = facts.currentWinner().orElseThrow().action().card();
    var defense = new DeclarerPressure(view, facts);
    List<Action> ordinary = PlaySupport.ordinary(legal, view);
    if (!mayAddPointBehindDeclarer(view, facts, conditions, winner)) {
      return Optional.empty();
    }
    if (facts.trickPoints() == 0) {
      var points = ordinary.stream().filter(action -> action.card().points() == 1)
          .filter(action -> !view.contract().trump().matches(((StandardCard) action.card()).suit()))
          .filter(action -> Rules.strength(action.card(), conditions)
              <= Rules.strength(winner, conditions)
              || mayAddPointBehindDeclarer(view, facts, conditions, action.card()))
          .toList();
      if (points.isEmpty()) {
        return Optional.empty();
      }
      var point = points.stream().filter(action -> !defense.defense((StandardCard) action.card()))
          .min(Comparator.comparingInt(action -> PlaySupport.cost(action, view)));
      if (point.isPresent()) {
        return point.map(action -> new PlayDecision(action,
            "부기루 수비에 필요 없는 한 점을 먼저 싣고 주공 뒤의 추가 점수로 압박합니다."));
      }
    }

    // 이미 위협이 있거나 점수 카드가 부기루 수비에 필요하면 가능한 물패를 냅니다.
    var low = facts.passingActions(ordinary).stream()
        .filter(action -> action.card().points() == 0)
        .filter(action -> !defense.defense((StandardCard) action.card()))
        .toList();
    return low.isEmpty() ? Optional.empty()
        : Optional.of(new PlayDecision(PlaySupport.cheapest(low, view), facts.trickPoints() == 1
            ? "뒤 야당의 추가 점수 가능성이 남아 주공 앞에서 점수를 겹치지 않습니다."
            : "지금 낼 점수 카드가 부기루 수비에 필요해 남깁니다."));
  }

  private static boolean declarerMayRuff(View view, PlayFacts facts) {
    return view.contract().trump() != Trump.NO_TRUMP
        && facts.cards().trumps().expectedCount(view.context().declarer(), facts) >= 0.5;
  }

  /**
   * 주공이 흘렸을 때 뒤 동료가 점수로 잡거나 야당이 이기는 구에 점수를 실을 근거를 찾습니다.
   * 미확인 카드는 바닥패나 다른 자리에 있을 수 있으므로 가능성 판단에만 씁니다.
   */
  private static boolean mayAddPointBehindDeclarer(
      View view, PlayFacts facts, TrickConditions conditions, Card winner) {
    List<Seat> remaining = facts.remainingSeats();
    Seat declarer = view.context().declarer();
    int afterDeclarer = remaining.indexOf(declarer) + 1;
    if (afterDeclarer == 0 || afterDeclarer == remaining.size()) {
      return false;
    }
    List<Seat> alliesBehind = remaining.subList(afterDeclarer, remaining.size()).stream()
        .filter(facts.teams()::isAlly)
        .toList();
    if (alliesBehind.isEmpty()) {
      return false;
    }
    boolean onlyAlliesIfDeclarerPasses = remaining.stream()
        .filter(seat -> !seat.equals(declarer)).allMatch(facts.teams()::isAlly);
    int strength = Rules.strength(winner, conditions);

    return facts.cards().outsideCards().stream()
        .filter(StandardCard.class::isInstance)
        .map(StandardCard.class::cast)
        .filter(card -> card.points() == 1 && !facts.cards().isDesignatedFriendCard(card)
            && !card.equals(Rules.mighty(view.contract().trump())))
        .anyMatch(card -> alliesBehind.stream().anyMatch(seat -> {
          if (facts.cards().isVoid(seat, card.suit())) {
            return false;
          }
          boolean followsSuit = card.suit() == conditions.leadSuit();
          boolean voidInLead = facts.cards().isVoid(seat, conditions.leadSuit());
          return (followsSuit || voidInLead)
              && (onlyAlliesIfDeclarerPasses || Rules.strength(card, conditions) > strength);
        }));
  }

  private static int pointsToDefeat(View view, PlayFacts facts) {
    // 야당에게 미공개인 바닥패의 점수도 주공이 가져갈 수 있는 상한에 남깁니다.
    int available = view.hand().stream().mapToInt(Card::points).sum() + facts.trickPoints()
        + facts.cards().outsideCards().stream().mapToInt(Card::points).sum();
    return capturedDeclarerTeamUpperBound(view) + available - view.contract().points() + 1;
  }

  private static int capturedDeclarerTeamUpperBound(View view) {
    Seat declarer = view.context().declarer();
    int declarerPoints = view.context().captured(declarer);
    if (view.context().friendStatus() instanceof NoFriend) {
      return declarerPoints;
    }
    if (view.context().friendStatus() instanceof RevealedFriend friend) {
      return declarerPoints + view.context().captured(friend.seat());
    }
    // 프렌드가 숨은 동안 가장 많이 먹은 비주공이 프렌드일 가능성을 남깁니다.
    int possibleHiddenFriendCapture = view.context().playOrder().seats().stream()
        .filter(seat -> !seat.equals(declarer))
        .mapToInt(view.context()::captured)
        .max()
        .orElse(0);
    return declarerPoints + possibleHiddenFriendCapture;
  }
}
