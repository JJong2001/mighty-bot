package mighty.game.setting;

import java.util.*;
import mighty.game.Role;
import mighty.game.TrickNumber;
import mighty.game.action.StandardPlay;
import mighty.game.deal.DeckFactory;
import mighty.game.observation.*;
import mighty.game.rule.Rules;
import mighty.model.*;

/**
 * 자기·공개 정보의 모든 외부 가능 카드에 대해 남은 모든 순서의 승리를 보수적으로 증명합니다.
 */
public final class SettingProof {

  private SettingProof() {
  }

  public static boolean canDeclare(View view) {
    if (view.role() != Role.DECLARER || !view.trick().isEmpty() || view.hand()
        .contains(Joker.INSTANCE)) {
      return false;
    }
    Set<Card> known = new HashSet<>(view.hand());
    known.addAll(view.publicCardHistory().cards());
    if (view.privateInfo() instanceof DeclarerInfo info) {
      known.addAll(info.discardedCards());
    }
    List<Card> outside = DeckFactory.create().stream().filter(c -> !known.contains(c)).toList();
    for (Card owned : view.hand()) {
      if (!(owned instanceof StandardCard card)) {
        return false;
      }
      for (int number = view.trickNumber().value(); number <= 10; number++) {
        var conditions = Rules.conditionsFor(view.contract().trump(), new TrickNumber(number),
            new StandardPlay(card,
                false));
        int strength = Rules.strength(card, conditions);
        if (outside.stream().anyMatch(c -> Rules.strength(c, conditions) >= strength)) {
          return false;
        }
      }
    }
    return true;
  }

}
