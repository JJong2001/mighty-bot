package mighty.ai.play;

import static mighty.ai.play.PlayTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import mighty.game.action.StandardPlay;
import mighty.game.friend.CardFriend;
import mighty.game.friend.UnrevealedFriend;
import mighty.model.StandardCard;
import mighty.model.Suit;
import mighty.model.Trump;
import org.junit.jupiter.api.Test;

class DeclarerSupportFeedbackTest {

  private final RulePlayPolicy policy = new RulePlayPolicy();

  @Test
  void opensBlockedSideRatherThanIsolatedWater() {
    var v = view(0, 0, Trump.CLUB, 16, "S8 D2 D6 DQ C4 C9 C10 CQ CK CA", "H2 H3 H4",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(), List.of());
    assertEquals(Suit.DIAMOND, ((StandardCard) policy.choose(v).card()).suit());
  }

  @Test
  void doesNotUseOpeningGunToFindMightyWithoutOwnJoker() {
    var v = view(0, 0, Trump.HEART, 15, "S2 S3 D2 D4 H3 H4 H6 HJ HK C3", "D5 D6 C2",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(), List.of());
    assertFalse(policy.choose(v) instanceof StandardPlay p && p.call());
    assertNotEquals(card("C3"), policy.choose(v).card());
  }
}
