package mighty.ai.play;

import static mighty.ai.play.PlayTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import mighty.ai.bidding.*;
import mighty.ai.planning.*;
import mighty.ai.preparation.*;
import mighty.data.CardParser;
import mighty.game.*;
import mighty.game.bidding.*;
import mighty.game.friend.*;
import mighty.game.observation.*;
import mighty.game.trick.PublicTrick;
import mighty.model.*;
import org.junit.jupiter.api.Test;

class FriendReturnTransitionTest {

  private static final Trump NT = Trump.NO_TRUMP;
  private static final CardFriend ACE = new CardFriend(card("DA"));

  private static List<PublicTrick> returnedPrimary(String discarded) {
    return List.of(
        row(1, NT, p(0, "C6"), p(1, "CA"), p(2, "C2"), p(3, "C4"), p(4, "C5")),
        row(2, NT, p(1, "D4"), p(2, "D5"), p(3, "D7"), p(4, "D8"), p(0, "DK")),
        row(3, NT, p(0, "D6"), p(1, "DA"), p(2, "D9"), p(3, "D10"), p(4, "H2")),
        row(4, NT, p(1, "DJ"), p(2, "H3"), p(3, "H4"), p(4, "H5"), p(0, discarded)));
  }

  @Test
  void friendStopsReturningSettledPrimaryAndUsesOpeningProbe() {
    var v = view(1, 0, NT, 15, "D2 C8 S8 H6 H8 H9", "", ACE,
        new RevealedFriend(new Seat(1)), returnedPrimary("D3"), List.of());
    assertEquals(card("C8"), new RulePlayPolicy().choose(v).card());
  }

  @Test
  void threeWinsDoNotProveThatALongQueenHoldingWasCleared() {
    var history = List.of(
        row(1, NT, p(0, "C6"), p(1, "CA"), p(2, "C2"), p(3, "C4"), p(4, "C5")),
        row(2, NT, p(1, "D4"), p(2, "D5"), p(3, "C7"), p(4, "H2"), p(0, "DK")),
        row(3, NT, p(0, "D6"), p(1, "DA"), p(2, "D7"), p(3, "H3"), p(4, "H4")),
        row(4, NT, p(1, "DJ"), p(2, "D8"), p(3, "H5"), p(4, "H6"), p(0, "D3")));
    var v = view(1, 0, NT, 15, "D2 C8 S8 H7 H8 H9", "", ACE,
        new RevealedFriend(new Seat(1)), history, List.of());
    assertEquals(card("D2"), new RulePlayPolicy().choose(v).card());
  }

  @Test
  void spadeReturnNeedsBothDiscardEvidenceAndLastPosition() {
    var favourable = view(1, 0, NT, 15, "D2 C8 S8 H6 H8 H9", "", ACE,
        new RevealedFriend(new Seat(1)), returnedPrimary("S4"), List.of());
    assertEquals(card("S8"), new RulePlayPolicy().choose(favourable).card());
    var history = List.of(
        row(1, NT, p(0, "C6"), p(1, "C2"), p(2, "CA"), p(3, "C4"), p(4, "C5")),
        row(2, NT, p(2, "D4"), p(3, "D5"), p(4, "D7"), p(0, "DK"), p(1, "D8")),
        row(3, NT, p(0, "D6"), p(1, "D9"), p(2, "DA"), p(3, "D10"), p(4, "H2")),
        row(4, NT, p(2, "DJ"), p(3, "H3"), p(4, "H4"), p(0, "S4"), p(1, "H5")));
    var unfavourable = view(2, 0, NT, 15, "D2 C8 S8 H6 H8 H9", "", ACE,
        new RevealedFriend(new Seat(2)), history, List.of());
    assertNotEquals(card("S8"), new RulePlayPolicy().choose(unfavourable).card());
  }

}
