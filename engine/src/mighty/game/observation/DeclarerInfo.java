package mighty.game.observation;

import java.util.List;
import mighty.model.Card;

public record DeclarerInfo(
    List<Card> hand,
    List<Card> discardedCards
) implements PrivateInfo {

  public DeclarerInfo {
    hand = List.copyOf(hand);
    discardedCards = List.copyOf(discardedCards);
    if (discardedCards.size() != 3) {
      throw new IllegalArgumentException(
          "주공이 버린 카드는 3장이어야 합니다."
      );
    }
  }

}
