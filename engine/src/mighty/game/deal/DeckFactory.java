package mighty.game.deal;

import java.util.ArrayList;
import java.util.List;
import mighty.model.Card;
import mighty.model.Joker;
import mighty.model.Rank;
import mighty.model.StandardCard;
import mighty.model.Suit;

public class DeckFactory {

  private DeckFactory() {
  }

  private static final List<Card> DECK = build();

  public static List<Card> create() {
    return DECK;
  }

  private static List<Card> build() {
    List<Card> cards = new ArrayList<>();
    for (Suit suit : Suit.values()) {
      for (Rank rank : Rank.values()) {
        cards.add(new StandardCard(suit, rank));
      }
    }
    cards.add(Joker.INSTANCE);
    return List.copyOf(cards);
  }

}
