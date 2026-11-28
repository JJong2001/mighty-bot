package mighty.ai.play;

import static mighty.ai.play.PlayTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import mighty.game.friend.*;
import mighty.model.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AceFriendOpeningJokerTest {
  @ParameterizedTest
  @CsvSource({"15,DA", "16,DA", "17,DA", "20,DA", "15,HA", "16,HA", "17,HA", "20,HA"})
  void otherAceFriendDoesNotDiscardJokerOnOpening(int points, String called) {
    var v = view(1, 0, Trump.NO_TRUMP, points,
        called + " D2 D3 H2 H3 S2 S3 C2 C4 JK", "",
        new CardFriend(card(called)), UnrevealedFriend.INSTANCE, List.of(), List.of(p(0, "C5")));
    assertNotEquals(Joker.INSTANCE, new RulePlayPolicy().choose(v).card());
  }

  @ParameterizedTest
  @CsvSource({"17", "20"})
  void clubAceFriendStillUnblocksGunResponse(int points) {
    var v = view(1, 0, Trump.NO_TRUMP, points, "CA CK CQ C4 JK D2 D3 H2 H3 S2", "",
        new CardFriend(card("CA")), UnrevealedFriend.INSTANCE, List.of(), List.of(p(0, "D4")));
    assertEquals(Joker.INSTANCE, new RulePlayPolicy().choose(v).card());
  }
}
