package mighty.game.action;

import mighty.model.Card;
import mighty.model.Joker;

public record JokerFollow() implements Action {

  @Override
  public Card card() {
    return Joker.INSTANCE;
  }

}
