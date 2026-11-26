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

class DeclarerJokerInitiativeTest {

  private final RulePlayPolicy policy = new RulePlayPolicy();

  @Test
  void keepsOpeningLeadToUseExternalJokerCall() {
    var v = view(0, 0, Trump.HEART, 16, "CA C3 C6 D2 D4 DQ H2 H4 H7 HK", "S2 S3 D5",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(), List.of());
    assertEquals(card("CA"), policy.choose(v).card());
  }

  @ParameterizedTest
  @CsvSource({"HA,C3", "HK,HA"})
  void doesNotSpendOnlyControlForOnePointOnLastSeat(String top, String spent) {
    var h = List.of(
        row(1, Trump.HEART, p(0, "C2"), p(1, "SA"), p(2, spent), p(3, "C4"), p(4, "C5")));
    var v = view(0, 0, Trump.HEART, 16, "H2 H4 H6 " + top + " D3 D5 DK S2 S3", "C6 C7 C8",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(1)), h,
        List.of(p(1, "H8"), p(2, "HQ"), p(3, "H5"), p(4, "H7")));
    assertEquals(0, policy.choose(v).card().points());
  }

  @Test
  void usesAvailableRuffRatherThanJokerForPoints() {
    var h = List.of(
        row(1, Trump.HEART, p(0, "CA"), p(1, "SA"), p(2, "C2"), p(3, "C3"), p(4, "C4")));
    var v = view(0, 0, Trump.HEART, 16, "H2 H4 H6 HK JK S2 S3 C5 C6", "C7 C8 C9",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(1)), h,
        List.of(p(1, "D2"), p(2, "DA"), p(3, "DK"), p(4, "DQ")));
    assertTrue(policy.choose(v).card() instanceof StandardCard c && c.suit() == Suit.HEART);
  }
}
