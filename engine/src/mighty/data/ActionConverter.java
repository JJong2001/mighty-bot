package mighty.data;

import java.util.Locale;
import java.util.Objects;
import mighty.game.action.Action;
import mighty.game.action.JokerFollow;
import mighty.game.action.JokerLead;
import mighty.game.action.StandardPlay;
import mighty.model.Card;
import mighty.model.StandardCard;
import mighty.model.Suit;

public final class ActionConverter {

  private ActionConverter() {
  }

  public static Action decode(String input) {
    Objects.requireNonNull(input, "행동 입력이 필요합니다.");
    String text = input.strip().toUpperCase(Locale.ROOT);
    String[] parts = text.split(":", -1);
    if (parts.length > 2) {
      throw new IllegalArgumentException(
          "행동 형식: SA, C3:CALL, JK:H, JK"
      );
    }
    Card card = CardParser.parse(parts[0]);
    if (card instanceof StandardCard ordinary) {
      if (parts.length == 1) {
        return new StandardPlay(ordinary, false);
      }
      if (parts[1].equals("CALL")) {
        return new StandardPlay(ordinary, true);
      }
      throw new IllegalArgumentException(
          "일반 카드에는 CALL만 지정할 수 있습니다."
      );
    }
    if (parts.length == 1) {
      return new JokerFollow();
    }
    if (parts[1].length() != 1) {
      throw new IllegalArgumentException(
          "조커를 선으로 사용할 때는 문양 하나를 지정하세요. 예: JK:H"
      );
    }
    return new JokerLead(Suit.parse(parts[1].charAt(0)));
  }

  public static String encode(Action action) {
    Objects.requireNonNull(action, "행동이 필요합니다.");
    if (action instanceof StandardPlay play) {
      return play.card().toCode()
          + (play.call() ? ":CALL" : "");
    }
    if (action instanceof JokerLead lead) {
      return "JK:" + lead.declaredSuit().code();
    }
    if (action instanceof JokerFollow) {
      return "JK";
    }
    throw new IllegalArgumentException(
        "지원하지 않는 행동 타입: " + action.getClass().getName()
    );
  }

}
