package mighty.ai.play;

import static mighty.ai.play.PlayTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import mighty.game.Seat;
import mighty.game.friend.SpecialFriend;
import mighty.game.friend.NoFriend;
import mighty.game.friend.RevealedFriend;
import mighty.model.StandardCard;
import mighty.model.Suit;
import mighty.model.Trump;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TrumpCountingFeedbackTest {

  private final RulePlayPolicy policy = new RulePlayPolicy();

  @ParameterizedTest
  @CsvSource({"S3 S5 SJ,SQ,S3", "S3 S5 S10,SJ,S3", "S8 S9 SQ,SK,S8", "S2 S4 SK,SA,S2"})
  void lowRuffPreservesPointWithEnoughCover(String own, String blocker, String low) {
    String buried = "D3 D4 D5";
    var last = row(7, Trump.SPADE, p(0, "C2"), p(1, "CA"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var trick = List.of(p(1, "HA"), p(2, "HK"), p(3, "H4"), p(4, "H5"));
    var h = countedHistory(Trump.SPADE, own, buried,
        "DA JK SA SK SQ SJ S10 S9 S8 S7 S6 S5 S4 S3 S2", blocker,
        trick, last);
    var v = view(0, 0, Trump.SPADE, 16, own, buried, SpecialFriend.NONE,
        NoFriend.INSTANCE, h, trick);
    assertEquals(card(low), policy.choose(v).card());
  }

  @ParameterizedTest
  @CsvSource({"S3 S5 SQ SK,SA", "S3 S5 S10 SJ,SQ"})
  void probesSingleBlockerWithCoverBeforePoints(String own, String blocker) {
    String buried = "D3 D4 D5";
    var h = countedHistory(Trump.SPADE, own, buried,
        "DA JK SA SK SQ SJ S10 S9 S8 S7 S6 S5 S4 S3 S2", blocker,
        List.of(), null);
    var v = view(0, 0, Trump.SPADE, 16, own, buried, SpecialFriend.NONE,
        NoFriend.INSTANCE, h, List.of());
    assertEquals(0, policy.choose(v).card().points());
    assertEquals(Suit.SPADE, ((StandardCard) policy.choose(v).card()).suit());
  }

  @ParameterizedTest
  @CsvSource({"SA SK SJ D2,S5 S9,SA", "SK SQ SJ D2,S5 S9,SK", "S9 S8 S7 D2,S5 S6,S9"})
  void oppositionDrawsAllInferiorTrumpsWhenOnlyDeclarerCanHoldThem(String own, String outside,
      String highest) {
    var last = row(6, Trump.SPADE, p(0, "S2"), p(1, "S10"), p(2, "H2"), p(3, "H3"), p(4, "H4"));
    var h = countedHistory(Trump.SPADE, own, "", "DA JK SA SK SQ SJ S10 S9 S8 S7 S6 S5 S4 S3 S2",
        outside, List.of(),
        last);
    var v = view(1, 0, Trump.SPADE, 16, own, "", SpecialFriend.NONE,
        NoFriend.INSTANCE, h, List.of());
    assertEquals(card(highest), policy.choose(v).card());
  }
}
