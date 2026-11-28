package mighty.ai.play;

import static mighty.ai.play.PlayTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import mighty.game.Seat;
import mighty.game.friend.*;
import mighty.model.*;
import org.junit.jupiter.api.Test;

class PublicInferencePressureTest {
  @Test void leadsQueenFromAceQueenWhenThoseAreOnlyPoints() {
    var history = List.of(row(1,Trump.NO_TRUMP,p(0,"C2"),p(1,"CA"),p(2,"C4"),p(3,"C5"),p(4,"C6")));
    var v = view(1,0,Trump.NO_TRUMP,15,"HA HQ H2 H3 D2 D3 S2 S3 C7","",
        new CardFriend(card("DA")),UnrevealedFriend.INSTANCE,history,List.of());
    assertEquals(card("HQ"),new RulePlayPolicy().choose(v).card());
  }

  @Test void feedsOnlyPointFromLongSuitWhenDeclarerAlreadyLoses() {
    var history = List.of(row(1,Trump.NO_TRUMP,p(0,"CA"),p(1,"C2"),p(2,"C3"),p(3,"C4"),p(4,"C5")));
    var v = view(2,0,Trump.NO_TRUMP,15,"H10 H3 H4 H5 S2 S3 D2 D3 D4","",
        new CardFriend(card("SA")),new RevealedFriend(new Seat(4)),history,
        List.of(p(0,"H2"),p(1,"HK")));
    assertEquals(card("H10"),new RulePlayPolicy().choose(v).card());
  }

  @Test void doesNotFeedPointToKnownFriendWinner() {
    var history = List.of(row(1,Trump.NO_TRUMP,p(0,"CA"),p(1,"C2"),p(2,"C3"),p(3,"C4"),p(4,"C5")));
    var v = view(2,0,Trump.NO_TRUMP,15,"H10 H3 H4 H5 S2 S3 D2 D3 D4","",
        new CardFriend(card("SA")),new RevealedFriend(new Seat(1)),history,
        List.of(p(0,"H2"),p(1,"HK")));
    assertNotEquals(card("H10"),new RulePlayPolicy().choose(v).card());
  }
}
