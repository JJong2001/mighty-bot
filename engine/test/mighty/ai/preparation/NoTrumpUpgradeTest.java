package mighty.ai.preparation;

import static org.junit.jupiter.api.Assertions.*;

import mighty.data.CardParser;
import mighty.game.Seat;
import mighty.game.bidding.Bid;
import mighty.game.observation.FriendView;
import mighty.model.Trump;
import org.junit.jupiter.api.Test;

class NoTrumpUpgradeTest {
  @Test
  void comparesSeventeenNoTrumpAfterFifteenAwardWithFixedDiscards() {
    var award = new Bid(15, Trump.NO_TRUMP);
    var v = new FriendView(new Seat(0), award,
        CardParser.parseList("SA SK SQ SJ S6 S3 D2 D3 C2 JK"),
        CardParser.parseList("H2 H3 H4"));
    assertEquals(new Bid(17, Trump.NO_TRUMP), new PreparationPlanner().finalContract(v, award));
  }
}
