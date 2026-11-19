package mighty.game.observation;

import java.util.List;
import mighty.model.Card;

public record NonDeclarerInfo(
    List<Card> hand
) implements PrivateInfo {

  public NonDeclarerInfo {
    hand = List.copyOf(hand);
  }

}
