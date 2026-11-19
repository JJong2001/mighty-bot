package mighty.game.rule;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import mighty.game.action.Action;
import mighty.game.action.JokerFollow;
import mighty.game.action.JokerLead;
import mighty.game.action.StandardPlay;
import mighty.model.Card;
import mighty.model.Joker;
import mighty.model.StandardCard;
import mighty.model.Suit;
import mighty.model.Trump;

public final class LegalActions {

  private LegalActions() {
  }

  public static List<Action> legalActions(LegalActionContext context) {
    Objects.requireNonNull(context, "관측 정보가 필요합니다.");
    return context.trick().isEmpty()
        ? leading(context)
        : following(context);
  }

  private static List<Action> leading(LegalActionContext context) {
    Trump trump = context.trump();
    boolean firstTrick = context.trickNumber().isFirst();
    boolean onlyTrumpAndJoker = context.hand().stream()
        .allMatch(card -> card instanceof Joker
            || isTrump(card, trump));
    List<Action> actions = new ArrayList<>();
    for (Card card : context.hand()) {
      if (card instanceof Joker) {
        for (Suit suit : Suit.values()) {
          actions.add(new JokerLead(suit));
        }
        continue;
      }
      // 손 패 전체가 기루거나, 조커와 기루로만 이루어져 있으면 초구에 기루를 낼 수 있다.
      if (card instanceof StandardCard ordinary) {
        if (firstTrick
            && trump.matches(ordinary.suit())
            && !onlyTrumpAndJoker) {
          continue;
        }
        actions.add(new StandardPlay(ordinary, false));
        if (!firstTrick && ordinary.equals(Rules.gun(trump))) {
          actions.add(new StandardPlay(ordinary, true));
        }
      }
    }
    return List.copyOf(actions);
  }

  private static List<Action> following(LegalActionContext context) {
    Trump trump = context.trump();
    TrickConditions conditions = Rules.conditionsFor(
        trump,
        context.trickNumber(),
        context.trick().get(0).action()
    );
    Suit leadSuit = conditions.leadSuit();
    StandardCard mighty = Rules.mighty(trump);
    // 리드 문양을 보유하고 있다면, 일반 카드는 리드 슈트를 따라야 함.
    boolean mustFollowSuit = context.hand().stream()
        .anyMatch(card -> card instanceof StandardCard ordinary
            && ordinary.suit() == leadSuit);
    // 콜을 받았고 조커가 있다면, 조커 or 마이티만 낼 수 있음.
    boolean forcedJoker = conditions.jokerCalled()
        && context.hand().contains(Joker.INSTANCE);
    List<Action> actions = new ArrayList<>();
    for (Card card : context.hand()) {
      if (card instanceof Joker) {
        actions.add(new JokerFollow());
        continue;
      }
      if (card instanceof StandardCard ordinary) {
        boolean isMighty = ordinary.equals(mighty);
        if (forcedJoker && !isMighty) {
          continue;
        }
        if (mustFollowSuit
            && !isMighty
            && ordinary.suit() != leadSuit) {
          continue;
        }
        actions.add(new StandardPlay(ordinary, false));
      }
    }
    return List.copyOf(actions);
  }

  private static boolean isTrump(Card card, Trump trump) {
    return card instanceof StandardCard ordinary
        && trump.matches(ordinary.suit());
  }

}
