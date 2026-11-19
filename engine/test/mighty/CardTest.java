package mighty;

import static org.junit.jupiter.api.Assertions.*;

import mighty.data.ActionConverter;
import mighty.data.CardParser;
import mighty.game.action.JokerLead;
import mighty.game.action.StandardPlay;
import mighty.model.*;
import org.junit.jupiter.api.Test;

class CardTest {

  @Test
  void everyCardAndActionRoundTrips() {
    for (Suit suit : Suit.values()) {
      for (Rank rank : Rank.values()) {
        StandardCard card = new StandardCard(suit, rank);
        assertEquals(card, CardParser.parse(card.toCode()));
        assertEquals(card, CardParser.parse(card.displayName()));
        var action = new StandardPlay(card, false);
        assertEquals(action, ActionConverter.decode(ActionConverter.encode(action)));
      }
      var joker = new JokerLead(suit);
      assertEquals(joker, ActionConverter.decode(ActionConverter.encode(joker)));
    }
    assertEquals(Joker.INSTANCE, CardParser.parse("JK"));
    assertThrows(IllegalArgumentException.class, () -> CardParser.parseList("SA SA"));
  }

}
