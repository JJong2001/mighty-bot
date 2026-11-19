package mighty.ai.play;

import java.util.*;
import mighty.game.action.*;
import mighty.game.observation.View;
import mighty.model.*;

/**
 * 조커 선언은 공개 반환 의도와 실제로 기루를 빼야 하는 상대가 있는지를 함께 봅니다.
 */
public final class JokerSuitPlan {

  private JokerSuitPlan() {
  }

  public static Action choose(List<Action> jokers, View view) {
    return choose(jokers, view, new PlayFacts(view));
  }

  public static Action choose(List<Action> jokers, View view, PlayFacts facts) {
    if (view.role() == mighty.game.Role.FRIEND) {
      if (FriendSupportIntent.mightyRemovalRoute(view)) {
        var otherSuits = jokers.stream().filter(action -> ((JokerLead) action).declaredSuit()
            != mighty.game.rule.Rules.mighty(view.contract().trump()).suit()).toList();
        if (!otherSuits.isEmpty()) {
          jokers = otherSuits;
        }
      }
      var safe = jokers.stream().filter(a -> mighty.ai.play.friend.FriendReturnSafety.allows(
          ((JokerLead) a).declaredSuit(), view, facts)).toList();
      if (!safe.isEmpty()) {
        jokers = safe;
      }
    }
    final List<Action> candidates = jokers;
    List<Suit> preferred = new ArrayList<>();
    if (view.role() == mighty.game.Role.FRIEND) {
      FriendSupportIntent.returnSuit(view).ifPresent(preferred::add);
    }
    preferred.addAll(PlaySupport.returnSuits(view));
    for (Suit suit : preferred) {
      boolean held = view.role() != mighty.game.Role.DECLARER || view.hand().stream()
          .anyMatch(c -> c instanceof StandardCard s && s.suit() == suit);
      if (held && candidates.stream().anyMatch(a -> ((JokerLead) a).declaredSuit() == suit)
          && (!view.contract().trump().matches(suit) || enemiesMayHoldTrump(view, facts))) {
        return inSuit(jokers, suit);
      }
    }
    Suit best = Arrays.stream(Suit.values())
        .filter(s -> candidates.stream().anyMatch(a -> ((JokerLead) a).declaredSuit() == s))
        .filter(s -> !view.contract().trump().matches(s) || enemiesMayHoldTrump(view, facts))
        .max(Comparator.<Suit>comparingLong(s -> view.hand().stream()
                .filter(c -> c instanceof StandardCard card && card.suit() == s).count())
            .thenComparingInt(s -> -s.ordinal()))
        .orElse(((JokerLead) jokers.get(0)).declaredSuit());
    return inSuit(jokers, best);
  }

  public static boolean enemiesMayHoldTrump(View view, PlayFacts facts) {
    return facts.cards().trumps().enemiesMayHold(facts);
  }

  private static Action inSuit(List<Action> jokers, Suit suit) {
    return jokers.stream().filter(a -> ((JokerLead) a).declaredSuit() == suit).findFirst()
        .orElseThrow();
  }

}
