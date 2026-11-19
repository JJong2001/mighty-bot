package mighty.game.action;

import java.util.Objects;
import mighty.model.Card;
import mighty.model.Joker;
import mighty.model.Suit;

public record JokerLead(Suit declaredSuit) implements Action {

  public JokerLead {
    Objects.requireNonNull(declaredSuit, "조커 지정 문양이 필요합니다.");
  }

  @Override
  public Card card() {
    return Joker.INSTANCE;
  }

}
