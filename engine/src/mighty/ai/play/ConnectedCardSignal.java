package mighty.ai.play;

import java.util.Comparator;
import java.util.List;
import mighty.game.Role;
import mighty.game.action.*;
import mighty.game.friend.CardFriend;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.*;

/**
 * 연결 승리와 프렌드의 기루 순회에서 높은 카드로 남은 서열을 알립니다.
 */
final class ConnectedCardSignal {

  private ConnectedCardSignal() {
  }

  static PlayDecision apply(PlayDecision decision, View view, PlayFacts facts, List<Action> legal) {
    if (!(decision.action() instanceof StandardPlay selected) || selected.call()
        || selected.card().equals(Rules.mighty(view.contract().trump()))) {
      return decision;
    }
    var descending = descendingFollow(decision, selected, view, facts, legal);
    if (descending != decision || selected.card().points() == 0) {
      return descending;
    }
    boolean leading = view.trick().isEmpty();
    if (!leading && (!facts.winningActions().contains(selected)
        || (view.contract().trump().matches(selected.card().suit())
        && !view.contract().trump().matches(facts.conditions().leadSuit())))) {
      return decision;
    }
    if (leading && preservesOpeningSignal(view, selected.card())) {
      return decision;
    }
    if (leading && view.role() == Role.FRIEND && view.contract().trump() == Trump.NO_TRUMP
        && !facts.isSuitTop(selected.card())) {
      return decision;
    }
    var higher = legal.stream().filter(a -> a instanceof StandardPlay p && !p.call())
        .filter(a -> a.card() instanceof StandardCard c && c.suit() == selected.card().suit()
            && !c.equals(Rules.mighty(view.contract().trump()))
            && c.rank().value() > selected.card().rank().value())
        .filter(a -> facts.cards().outsideCards().stream()
            .noneMatch(outside -> outside instanceof StandardCard c
                && !c.equals(Rules.mighty(view.contract().trump())) && c.suit() == selected.card()
                .suit()
                && c.rank().value() > selected.card().rank().value()
                && c.rank().value() <= ((StandardCard) a.card()).rank().value()))
        .max(Comparator.comparingInt(a -> ((StandardCard) a.card()).rank().value()));
    return higher.map(a -> new PlayDecision(a,
        decision.reason() + " 연결 카드 중 높은 카드를 보여 동료의 오해를 막습니다.")).orElse(decision);
  }

  /**
   * 프렌드는 주공의 리드에 지는 물패를 낼 때 문양과 야당 잔여에 관계없이 높은 것부터 보입니다.
   */
  private static PlayDecision descendingFollow(
      PlayDecision decision, StandardPlay selected, View view, PlayFacts facts, List<Action> legal
  ) {
    if (view.role() != Role.FRIEND || view.trick().isEmpty()
        || !view.trick().get(0).seat().equals(view.context().declarer())
        || selected.card().suit() != facts.conditions().leadSuit()) {
      return decision;
    }
    int winnerStrength = Rules.strength(facts.currentWinner().orElseThrow().action().card(),
        facts.conditions());
    if (Rules.strength(selected.card(), facts.conditions()) > winnerStrength) {
      return decision;
    }
    // 주공이 일반 기루 탑카로 이기는 순회에서는 점수 기루도 서열을 보여 줍니다.
    // 사이드의 독립 탑카를 단순 신호로 버리는 판단에는 사용하지 않습니다.
    var winner = facts.currentWinner().orElseThrow();
    boolean showPointTrump = view.contract().trump().matches(selected.card().suit())
        && winner.seat().equals(view.context().declarer())
        && facts.isSuitTop(winner.action().card());
    return PlaySupport.ordinary(legal, view).stream()
        .filter(action -> ((StandardCard) action.card()).suit() == selected.card().suit())
        .filter(action -> action.card().points() == 0 || showPointTrump)
        .filter(action -> ((StandardCard) action.card()).rank().value() > selected.card().rank()
            .value())
        .filter(action -> Rules.strength(action.card(), facts.conditions()) < winnerStrength)
        .max(Comparator.comparingInt(action -> ((StandardCard) action.card()).rank().value()))
        .map(action -> new PlayDecision(action,
            decision.reason() + " 프렌드는 같은 문양에 지는 카드를 높은 것부터 보여줍니다."))
        .orElse(decision);
  }

  private static boolean preservesOpeningSignal(View view, StandardCard card) {
    if (view.role() != Role.DECLARER || view.trickNumber().value() != 1
        || !(view.friendSelection() instanceof CardFriend friend)) {
      return false;
    }
    if (friend.card() instanceof Joker) {
      return true;
    }
    return friend.card() instanceof StandardCard requested
        && !requested.equals(Rules.mighty(view.contract().trump()))
        && requested.suit() != card.suit();
  }

}
