package mighty.ai.play;

import static mighty.ai.play.PlayTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import mighty.game.bidding.Bid;
import mighty.game.friend.SpecialFriend;
import mighty.game.friend.NoFriend;
import mighty.model.StandardCard;
import mighty.model.Suit;
import mighty.model.Trump;
import org.junit.jupiter.api.Test;

class OppositionNoTrumpFeedbackTest {

  private final RulePlayPolicy policy = new RulePlayPolicy();

  @Test
  void oppositionDoesNotLeadBlockedDeclaredNoTrumpPrimaryForPointPressure() {
    String own = "C7 C10 CJ DQ";
    var last = row(6, Trump.NO_TRUMP, p(0, "D2"), p(1, "DA"), p(2, "D3"), p(3, "D4"), p(4, "D5"));
    var h = countedHistory(Trump.NO_TRUMP, own, "", "SA JK", "CA CK CQ C2 C3 C4 C5 C6 C8 C9",
        List.of(), last);
    var v = withPriorContract(view(1, 0, Trump.NO_TRUMP, 17, own, "", SpecialFriend.NONE,
        NoFriend.INSTANCE, h, List.of()), new Bid(16, Trump.CLUB));
    assertNotEquals(Suit.CLUB, ((StandardCard) policy.choose(v).card()).suit());
  }

  @Test
  void doesNotSpendJokerJustToAvoidTheOnlyOrdinarySuit() {
    String own = "C7 C10 CJ JK";
    var last = row(6, Trump.NO_TRUMP, p(0, "D2"), p(1, "DA"), p(2, "D3"), p(3, "D4"), p(4, "D5"));
    var h = countedHistory(Trump.NO_TRUMP, own, "", "SA", "CA CK CQ C2 C3 C4 C5 C6 C8 C9",
        List.of(), last);
    var v = withPriorContract(view(1, 0, Trump.NO_TRUMP, 17, own, "", SpecialFriend.NONE,
        NoFriend.INSTANCE, h, List.of()), new Bid(16, Trump.CLUB));
    assertTrue(policy.choose(v).card() instanceof StandardCard);
  }
}
