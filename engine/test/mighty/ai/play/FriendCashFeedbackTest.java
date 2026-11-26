package mighty.ai.play;

import static mighty.ai.play.PlayTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import mighty.game.Seat;
import mighty.game.bidding.Bid;
import mighty.game.friend.CardFriend;
import mighty.game.friend.RevealedFriend;
import mighty.game.friend.SpecialFriend;
import mighty.model.StandardCard;
import mighty.model.Suit;
import mighty.model.Trump;
import org.junit.jupiter.api.Test;

class FriendCashFeedbackTest {

  private final RulePlayPolicy policy = new RulePlayPolicy();

  @Test
  void keepsCashingIndependentNoTrumpTopsAfterFirstSupport() {
    var first = row(1, Trump.NO_TRUMP, p(0, "D2"), p(1, "SA"), p(2, "D3"), p(3, "D4"), p(4, "D5"));
    var second = row(2, Trump.NO_TRUMP, p(1, "CA"), p(2, "C2"), p(3, "C3"), p(4, "C4"), p(0, "C5"));
    var v = view(1, 0, Trump.NO_TRUMP, 17, "CK HA D6 D7 S2 S3 C6 H2", "",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(1)), List.of(first, second),
        List.of());
    v = withPriorContract(v, new Bid(16, Trump.DIAMOND));
    assertTrue(Set.of(card("CK"), card("HA")).contains(policy.choose(v).card()));
  }

  @Test
  void avoidsRepeatingOpeningFriendSuitWhileOppositionCanRuff() {
    var first = row(1, Trump.HEART, p(0, "D2"), p(1, "DA"), p(2, "D3"), p(3, "D4"), p(4, "D5"));
    var v = view(1, 0, Trump.HEART, 15, "DK H3 H5 C2 C4 S2 S3 C6 H2", "", SpecialFriend.FIRST_TRICK,
        new RevealedFriend(new Seat(1)), List.of(first), List.of());
    assertNotEquals(Suit.DIAMOND, ((StandardCard) policy.choose(v).card()).suit());
  }

  @Test
  void freshIndependentSideTopIsStillAllowedBeforeTrumpReturn() {
    var first = row(1, Trump.HEART, p(0, "D2"), p(1, "SA"), p(2, "D3"), p(3, "D4"), p(4, "D5"));
    var v = view(1, 0, Trump.HEART, 15, "SK H2 D8 D9 C6 C5 S4 S3 H3", "",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(1)), List.of(first), List.of());
    assertEquals(card("SK"), policy.choose(v).card());
  }
}
