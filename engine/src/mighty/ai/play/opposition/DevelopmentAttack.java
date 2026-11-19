package mighty.ai.play.opposition;

import java.util.*;
import mighty.ai.play.*;
import mighty.game.action.Action;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.*;

/**
 * 주공 부기루를 돌려 동료의 간을 유도할 이익과 주공의 간을 아껴줄 손해를 비교합니다.
 */
final class DevelopmentAttack {

  private DevelopmentAttack() {
  }

  static Optional<PlayDecision> choose(View view, PlayFacts facts, List<Action> ordinary) {
    if (view.contract().trump() == Trump.NO_TRUMP) {
      return Optional.empty();
    }
    var ownership = facts.cards().trumps();
    double declarerTrump = ownership.expectedCount(view.context().declarer(), facts);
    double sideCards = ownership.remainingHand(view.context().declarer()) - declarerTrump;
    if (sideCards > 2 && (ownership.likelyShort(view.context().declarer())
        || declarerTrump < 1.5)) {
      return Optional.empty();
    }
    return ordinary.stream()
        .filter(a -> !view.contract().trump().matches(((StandardCard) a.card()).suit()))
        .filter(a -> DeclarerOperations.contains(view, facts, ((StandardCard) a.card()).suit()))
        .filter(a -> opportunity(view, facts, (StandardCard) a.card()))
        .min(Comparator.<Action>comparingInt(a -> a.card().points())
            .thenComparingInt(a -> ((StandardCard) a.card()).rank().value()))
        .map(a -> new PlayDecision(a, "부기루의 기존 순회와 동료 간 가능성을 보고 주공의 운영 문양을 공격합니다."));
  }

  private static boolean opportunity(View view, PlayFacts facts, StandardCard card) {
    boolean lastSide = facts.cards().trumps().likelyLastSide(view.context().declarer(), facts);
    if (facts.isSuitTop(card)) {
      return lastSide && facts.isMasterLead(new mighty.game.action.StandardPlay(card,
          false));
    }
    long rounds = view.context().history().stream()
        .filter(t -> Rules.conditionsFor(view.contract().trump(),
            t.number(), t.cards().get(0).action()).leadSuit() == card.suit()).count();
    if (rounds == 0) {
      return false;
    }
    var allies = view.context().playOrder().seats().stream()
        .filter(s -> !s.equals(view.self()) && facts.teams().isAlly(s)).toList();
    boolean mayRuff = allies.stream()
        .anyMatch(s -> facts.cards().trumps().expectedCount(s, facts) > 0.25
            && (facts.cards().isVoid(s, card.suit())
            || facts.cards().outsideCount(card.suit()) <= allies.size() * 3));
    return mayRuff || lastSide && card.points() > 0;
  }

}
