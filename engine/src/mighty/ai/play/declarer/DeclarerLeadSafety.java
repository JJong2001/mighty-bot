package mighty.ai.play.declarer;

import java.util.*;
import mighty.ai.play.*;
import mighty.game.action.*;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.*;

/**
 * 이미 간 위험이 공개된 문양을 기루 소모보다 먼저 반복하지 않도록 합니다.
 */
public final class DeclarerLeadSafety {

  private DeclarerLeadSafety() {
  }

  public static PlayDecision apply(PlayDecision decision, View view, PlayFacts facts,
      List<Action> legal) {
    if (!view.trick().isEmpty() || !(decision.action() instanceof StandardPlay play) || play.call()
        || play.card().equals(Rules.mighty(view.contract().trump())) || !dangerous(
        play.card().suit(), view, facts)) {
      return decision;
    }
    TrumpControl control = new TrumpControl(view, facts);
    var trumps = PlaySupport.ordinary(legal, view).stream()
        .filter(a -> a instanceof StandardPlay p && !p.call())
        .filter(a -> view.contract().trump().matches(((StandardCard) a.card()).suit())).toList();
    if (!trumps.isEmpty() && !control.shouldPreserveLastTrump()) {
      var tops = trumps.stream().filter(a -> facts.isSuitTop(a.card())).toList();
      if (!tops.isEmpty()) {
        return new PlayDecision(PlaySupport.highest(tops), "야당의 간 위험이 공개된 문양을 반복하기 전에 기루를 소모시킵니다.");
      }
      var middle = trumps.stream().filter(a -> a.card().points() == 0).toList();
      if (!middle.isEmpty()) {
        return new PlayDecision(PlaySupport.highest(middle),
            "이미 간 당한 문양을 반복하지 않고 중간 기루로 상위를 유도합니다.");
      }
    }
    var sides = PlaySupport.ordinary(legal, view).stream()
        .filter(a -> a instanceof StandardPlay p && !p.call())
        .filter(a -> !view.contract().trump().matches(((StandardCard) a.card()).suit()))
        .filter(a -> !dangerous(((StandardCard) a.card()).suit(), view, facts)).toList();
    if (sides.isEmpty()) {
      return decision;
    }
    var tops = sides.stream().filter(a -> facts.isSuitTop(a.card())).toList();
    return new PlayDecision(PlaySupport.cheapest(tops.isEmpty() ? sides : tops, view),
        "공개된 간 위험을 피해 다른 문양에서 운영을 이어갑니다.");
  }

  private static boolean dangerous(Suit suit, View view, PlayFacts facts) {
    if (view.contract().trump().matches(suit)) {
      return false;
    }
    return Arrays.stream(Suit.values()).filter(view.contract().trump()::matches).anyMatch(trump ->
        facts.cards().outsideCount(trump) > 0 && view.context().playOrder().seats().stream()
            .filter(s -> !facts.teams().isAlly(s))
            .anyMatch(s -> facts.cards().isVoid(s, suit) && !facts.cards().isVoid(s,
                trump)));
  }

}
