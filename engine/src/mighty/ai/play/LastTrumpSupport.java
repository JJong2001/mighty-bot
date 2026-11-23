package mighty.ai.play;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import mighty.game.Role;
import mighty.game.action.Action;
import mighty.game.action.StandardPlay;
import mighty.game.friend.RevealedFriend;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.StandardCard;
import mighty.model.Suit;

/** 마지막 외부 기루가 프렌드에게 있을 때만 살릴 수 있는 부기루 선출을 찾습니다. */
final class LastTrumpSupport {

  private LastTrumpSupport() {}

  static List<Action> candidates(View view, PlayFacts facts, List<Action> legal, Action usual) {
    var trump = view.contract().trump();
    var mighty = Rules.mighty(trump);
    if (view.role() != Role.DECLARER || !view.trick().isEmpty()
        || !(view.context().friendStatus() instanceof RevealedFriend friend)
        || !(usual instanceof StandardPlay draw) || draw.call() || draw.card().equals(mighty)
        || !trump.matches(draw.card().suit()) || !facts.isMasterLead(draw)) {
      return List.of();
    }
    var outside = facts.cards().trumps().outside().stream().filter(card -> !card.equals(mighty))
        .toList();
    var trumpSuit = draw.card().suit();
    if (outside.size() != 1 || facts.cards().isVoid(friend.seat(), trumpSuit)
        || view.context().playOrder().seats().stream().filter(seat -> !facts.teams().isAlly(seat))
            .allMatch(seat -> facts.cards().isVoid(seat, trumpSuit))) {
      return List.of();
    }
    // 다른 문양에도 외부 상위가 있어 한 번의 간 지원으로 손실을 줄일 수 있는 형태를 봅니다.
    var blocked = Arrays.stream(Suit.values()).filter(suit -> !trump.matches(suit))
        .map(suit -> SuitDevelopment.analyze(view, facts, suit))
        .filter(side -> !side.held().isEmpty() && !side.blockers().isEmpty()).toList();
    if (blocked.size() < 2) {
      return List.of();
    }
    return blocked.stream().flatMap(side -> legal.stream()
        .filter(action -> action instanceof StandardPlay play && !play.call()
            && play.card().suit() == side.suit() && !play.card().equals(mighty))
        .min(Comparator.comparingInt((Action action) -> action.card().points())
            .thenComparingInt(action -> ((StandardCard) action.card()).rank().value())).stream())
        .toList();
  }
}
