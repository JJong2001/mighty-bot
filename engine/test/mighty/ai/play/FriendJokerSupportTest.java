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

class FriendJokerSupportTest {

  private final RulePlayPolicy policy = new RulePlayPolicy();

  @Test
  void friendDoesNotSweepAfterRequestedTrumpBlockerWasRemoved() {
    var h = List.of(row(1, Trump.HEART, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5")),
        row(2, Trump.HEART, p(0, "HK"), p(1, "HA"), p(2, "SA"), p(3, "H4"), p(4, "H5")));
    var v = view(2, 0, Trump.HEART, 16, "JK H2 H3 D2 D3 D4 S2 S3", "", new CardFriend(card("SA")),
        new RevealedFriend(new Seat(2)), h, List.of());
    assertFalse(policy.choose(v) instanceof JokerLead j && j.declaredSuit() == Suit.HEART);
  }

  @Test
  void friendKeepsPromotedSideTopOnDeclarerJoker() {
    var h = List.of(row(1, Trump.HEART, p(0, "CA"), p(1, "SA"), p(2, "C2"), p(3, "C3"), p(4, "C4")),
        row(2, Trump.HEART, p(1, "C5"), p(2, "C6"), p(3, "C7"), p(4, "C8"), p(0, "C10")));
    var v = view(1, 0, Trump.HEART, 16, "C9 CK H2 H3 H4 S2 S3 S4", "", new CardFriend(card("SA")),
        new RevealedFriend(new Seat(1)), h,
        List.of(new mighty.game.trick.PublicPlay(new Seat(0), new JokerLead(Suit.DIAMOND))));
    assertNotEquals(card("CK"), policy.choose(v).card());
  }

  @Test
  void loneTrumpAfterOpponentTopRuffDoesNotJustifyExchange() {
    var pre = row(6, Trump.SPADE, p(0, "S7"), p(1, "S6"), p(2, "H2"), p(3, "H3"), p(4, "H4"));
    var ruff = row(7, Trump.SPADE, p(0, "C2"), p(1, "SA"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var h = new java.util.ArrayList<>(
        countedHistory(Trump.SPADE, "JK D7 D8 D9", "", "DA SK SQ SJ S10 S9 S8 S4 S3 S2", "S5",
            java.util.stream.Stream.concat(ruff.cards().stream(), List.of(p(1, "D2")).stream())
                .toList(), pre));
    h.add(ruff);
    var v = view(2, 0, Trump.SPADE, 16, "JK D8 D9", "", new CardFriend(Joker.INSTANCE),
        UnrevealedFriend.INSTANCE, h, List.of(p(1, "D2")));
    var facts = new PlayFacts(v);
    assertFalse(facts.cards().trumps().enemiesMayHold(facts));
    assertFalse(new TrumpControl(v, facts).needsSpecialSweep());
  }
}
