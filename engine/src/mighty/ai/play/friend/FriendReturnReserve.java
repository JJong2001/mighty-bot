package mighty.ai.play.friend;

import mighty.game.Role;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.*;

/**
 * 노기루에서만, 호출 대기 중 특수카드 사용 후 돌려줄 일반 카드를 남깁니다.
 */
public final class FriendReturnReserve {

  private FriendReturnReserve() {
  }

  public static boolean protects(View view, Card card) {
    if (view.role() != Role.FRIEND || view.contract().trump() != Trump.NO_TRUMP
        || view.trickNumber().value() >= 7 || !(card instanceof StandardCard standard)
        || card.equals(Rules.mighty(Trump.NO_TRUMP))) {
      return false;
    }
    boolean waiting =
        view.hand().contains(Rules.mighty(Trump.NO_TRUMP)) || view.hand().contains(Joker.INSTANCE);
    if (!waiting) {
      return false;
    }
    long ordinary = view.hand().stream()
        .filter(c -> c instanceof StandardCard s && s.suit() == standard.suit()
            && !c.equals(Rules.mighty(Trump.NO_TRUMP))).count();
    return ordinary == 1;
  }

}
