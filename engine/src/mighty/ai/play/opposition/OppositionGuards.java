package mighty.ai.play.opposition;

import java.util.*;
import mighty.ai.play.PlayFacts;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.*;

/**
 * 동료가 버리는 문양은 확정 무문양이 아닌 수비 분담 신호로만 사용합니다.
 */
public final class OppositionGuards {

  private OppositionGuards() {
  }

  /**
   * 공개된 운영 문양은 높은 카드와 받쳐 줄 장수를 함께 남깁니다.
   */
  public static boolean protectsDevelopment(View view, PlayFacts facts, Suit suit) {
    var inference = facts.declarerSuits().assess(suit);
    if (view.role() != mighty.game.Role.OPPOSITION
        || inference.shownVoid()
        || (!inference.operating() && inference.ordinaryLeads() == 0)) {
      return false;
    }
    return retention(view, facts, suit) > 0;
  }

  public static int retention(View view, PlayFacts facts, Suit suit) {
    List<StandardCard> held = view.hand().stream().filter(c -> c instanceof StandardCard s
            && s.suit() == suit && !c.equals(Rules.mighty(view.contract().trump())))
        .map(StandardCard.class::cast).toList();
    if (held.isEmpty()) {
      return 0;
    }
    int highest = held.stream().mapToInt(c -> c.rank().value()).max().orElseThrow();
    long threats = facts.cards().outsideCards().stream().filter(c ->
            c.equals(Rules.mighty(view.contract().trump())) || c instanceof Joker
                || c instanceof StandardCard s && s.suit() == suit && s.rank().value() > highest)
        .count();
    if (held.size() <= threats) {
      return 0;
    }
    int emptied = 0;
    for (var trick : view.context().history()) {
      Suit leadSuit = Rules.conditionsFor(view.contract().trump(), trick.number(),
          trick.cards().get(0).action()).leadSuit();
      for (var play : trick.cards()) {
        if (!play.seat().equals(view.self()) && facts.teams().isAlly(play.seat())
            && play.action().card() instanceof StandardCard c && c.suit() == suit
            && c.suit() != leadSuit && !c.equals(Rules.mighty(view.contract().trump()))) {
          emptied++;
        }
      }
    }
    return held.size() * 10 + emptied * 20;
  }

}
