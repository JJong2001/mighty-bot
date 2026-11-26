package mighty.ai.play;

import static mighty.ai.play.PlayTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import mighty.game.Seat;
import mighty.game.action.*;
import mighty.game.friend.*;
import mighty.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class OppositionJokerPressureFeedbackTest {

  private final RulePlayPolicy policy = new RulePlayPolicy();

  @Test
  void oppositionJokerPromotesRemainingTrump() {
    String own = "SQ JK D2";
    var trick = List.of(p(0, "SK"));
    var h = countedHistory(Trump.SPADE, own, "", "DA SA SJ S10", "S2 S3 S4 S5 S6 S7 S8 S9", trick,
        null);
    var v = view(1, 0, Trump.SPADE, 16, own, "", SpecialFriend.NONE, NoFriend.INSTANCE, h, trick);
    assertEquals(Joker.INSTANCE, policy.choose(v).card());
  }

  @Test
  void oppositionPressuresWithConnectedTopBeforeLastDeclarer() {
    var h = List.of(
        row(1, Trump.HEART, p(0, "CA"), p(1, "SA"), p(2, "C2"), p(3, "C3"), p(4, "C4")));
    var v = view(3, 0, Trump.HEART, 16, "HA HK H2 D2 D3 D4 S2 S3 S4", "",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(1)), h,
        List.of(p(1, "H7"), p(2, "HJ")));
    assertTrue(List.of(card("HK"), card("HA")).contains(policy.choose(v).card()),
        policy.recommend(v).reason());
  }
}
