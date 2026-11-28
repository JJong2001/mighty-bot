package mighty.ai.play;

import static mighty.ai.play.PlayTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import mighty.ai.play.friend.FriendReturnSafety;
import mighty.game.Seat;
import mighty.game.friend.*;
import mighty.model.*;
import org.junit.jupiter.api.Test;

class RoleStrategyUpdateTest {
  @Test
  void noTrumpJokerRemovesLastPrimaryBlocker() {
    String own = "CK CQ C2 JK";
    String buried = "D3 D4 D5";
    var h = countedHistory(Trump.NO_TRUMP, own, buried,
        "SA CA CK CQ CJ C10 C9 C8 C7 C6 C5 C4 C3 C2", "CA", List.of(), null);
    var v = view(0, 0, Trump.NO_TRUMP, 17, own, buried, SpecialFriend.NONE,
        NoFriend.INSTANCE, h, List.of());
    assertEquals(new mighty.game.action.JokerLead(Suit.CLUB), new RulePlayPolicy().choose(v));
  }

  @Test
  void oppositionJokerStripsDeclarersKnownLastTrump() {
    String own = "JK D2 C2 H2";
    var last = row(6, Trump.SPADE, p(0, "S2"), p(1, "SA"), p(2, "H3"),
        p(3, "H4"), p(4, "H5"));
    var h = countedHistory(Trump.SPADE, own, "",
        "DA SA SQ SJ S10 S9 S8 S7 S6 S5 S4 S3 S2", "SK", List.of(), last);
    var v = view(1, 0, Trump.SPADE, 16, own, "", SpecialFriend.NONE,
        NoFriend.INSTANCE, h, List.of());
    assertEquals(new mighty.game.action.JokerLead(Suit.SPADE), new RulePlayPolicy().choose(v));
  }
  @Test
  void oppositionFeedsBlockedShortQueenToAlliedAce() {
    var v = view(2, 0, Trump.NO_TRUMP, 15, "DQ D4 H2 H3 H4 H5 S2 S3 C2 C3", "",
        new CardFriend(card("CA")), new RevealedFriend(new Seat(4)), List.of(),
        List.of(p(0, "D2"), p(1, "DA")));
    assertEquals(card("DQ"), new RulePlayPolicy().choose(v).card());
  }
  @Test
  void friendKeepsSideAceForDeclarersPublicOperatingSuit() {
    var h = List.of(row(1, Trump.HEART, p(0, "DQ"), p(1, "SA"), p(2, "D2"),
        p(3, "D3"), p(4, "D4")),
        row(2, Trump.HEART, p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"), p(0, "CA")));
    var v = view(1, 0, Trump.HEART, 16, "DA CQ C6 C7 S2 S3 S4 S5", "",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(1)), h, List.of(p(0, "HA")));
    assertNotEquals(card("DA"), new RulePlayPolicy().choose(v).card());
  }
  @Test
  void highNoTrumpUsesMightyOnReturnToSixCardAceTenPrimary() {
    var h = List.of(row(1, Trump.NO_TRUMP, p(0, "C6"), p(1, "CA"), p(2, "C2"),
        p(3, "C4"), p(4, "C5")));
    var v = view(0, 0, Trump.NO_TRUMP, 17, "DA D10 D3 D4 D5 D6 SA JK HA", "H2 H3 H4",
        new CardFriend(card("CA")), new RevealedFriend(new Seat(1)), h,
        List.of(p(1, "D2"), p(2, "D7"), p(3, "D8"), p(4, "D9")));
    assertEquals(card("SA"), new RulePlayPolicy().choose(v).card());
  }
  @Test
  void intermediatePointDeniesCheapFriendWinWithFourCardHolding() {
    var v = view(1, 0, Trump.NO_TRUMP, 15, "D4 D5 DJ DK C2 C3 H2 H3 S2 S3", "",
        new CardFriend(card("DA")), UnrevealedFriend.INSTANCE, List.of(), List.of(p(0, "D3")));
    assertEquals(card("DJ"), new RulePlayPolicy().choose(v).card());
  }

  @Test
  void calledAceSignalsPrimaryBeforeAnyLeadInThatSuit() {
    var h = List.of(row(1, Trump.NO_TRUMP, p(0, "C6"), p(1, "CA"), p(2, "C2"),
        p(3, "C4"), p(4, "C5")));
    var v = view(1, 0, Trump.NO_TRUMP, 15, "D4 DJ C8 H4 H8 S4 S8 H9 C9", "",
        new CardFriend(card("DA")), UnrevealedFriend.INSTANCE, h, List.of());
    assertEquals(Suit.DIAMOND, FriendSupportIntent.disclosedPrimary(v).orElseThrow());
    assertNotEquals(Suit.DIAMOND, ((StandardCard) new RulePlayPolicy().choose(v).card()).suit());
  }

  @Test
  void highNoTrumpAceFriendAloneDoesNotDeclarePrimary() {
    var v = view(1, 0, Trump.NO_TRUMP, 17, "D4 DJ C8 H4 H8 S4 S8 H9 C9 C7", "",
        new CardFriend(card("DA")), UnrevealedFriend.INSTANCE, List.of(), List.of(p(0, "C6")));
    assertTrue(FriendSupportIntent.disclosedPrimary(v).isEmpty());
  }

  @Test
  void repeatedPublicLeadsOverrideInitialAceFriendInference() {
    var h = List.of(row(1, Trump.NO_TRUMP, p(0, "HA"), p(1, "H2"), p(2, "H3"),
        p(3, "H4"), p(4, "H5")),
        row(2, Trump.NO_TRUMP, p(0, "HK"), p(1, "H6"), p(2, "H7"), p(3, "H8"), p(4, "H9")));
    var v = view(1, 0, Trump.NO_TRUMP, 15, "D4 DJ C8 C4 C7 S4 S8 C9", "",
        new CardFriend(card("DA")), UnrevealedFriend.INSTANCE, h, List.of(p(0, "C6")));
    assertEquals(Suit.HEART, FriendSupportIntent.disclosedPrimary(v).orElseThrow());
  }

  @Test
  void requestRuffedInSameTrickDoesNotAuthorizeReturningIt() {
    var h = List.of(row(1, Trump.HEART, p(0, "D2"), p(1, "H2"), p(2, "D3"),
        p(3, "D4"), p(4, "DA")));
    var v = view(4, 0, Trump.HEART, 16, "D5 DK C2 C3 C4 S2 S3 S4 H3", "",
        new CardFriend(card("DA")), new RevealedFriend(new Seat(4)), h,
        List.of(p(1, "C6"), p(2, "C7"), p(3, "C8")));
    assertFalse(FriendReturnSafety.allows(Suit.DIAMOND, v, new PlayFacts(v)));
    assertTrue(FriendReturnSafety.allows(Suit.CLUB, v, new PlayFacts(v)));
  }

  @Test
  void declarerCanRenewRequestAfterObservingRuff() {
    var h = List.of(row(1, Trump.HEART, p(0, "D2"), p(1, "H2"), p(2, "D3"),
        p(3, "D4"), p(4, "DA")),
        row(2, Trump.HEART, p(0, "D6"), p(1, "C6"), p(2, "D7"), p(3, "D8"), p(4, "DQ")));
    var v = view(4, 0, Trump.HEART, 16, "D5 DK C2 C3 C4 S2 S3 H3", "",
        new CardFriend(card("DA")), new RevealedFriend(new Seat(4)), h, List.of());
    assertTrue(FriendReturnSafety.allows(Suit.DIAMOND, v, new PlayFacts(v)));
  }

  @Test
  void ownJokerRemovesLastSuperiorTrumpBeforeLosingProbe() {
    String own = "S3 S5 SQ JK";
    String buried = "D3 D4 D5";
    var h = countedHistory(Trump.SPADE, own, buried,
        "DA SA SK SQ SJ S10 S9 S8 S7 S6 S5 S4 S3 S2", "SK", List.of(), null);
    var v = view(0, 0, Trump.SPADE, 16, own, buried, SpecialFriend.NONE,
        NoFriend.INSTANCE, h, List.of());
    var action = new RulePlayPolicy().choose(v);
    assertInstanceOf(mighty.game.action.JokerLead.class, action);
    assertEquals(Suit.SPADE, ((mighty.game.action.JokerLead) action).declaredSuit());
  }

  @Test
  void highNoTrumpClubAceFriendStillDiscardsJokerOnOpening() {
    var v = view(1, 0, Trump.NO_TRUMP, 17, "CA CK CQ C4 JK D2 D3 H2 H3 S2", "",
        new CardFriend(card("CA")), UnrevealedFriend.INSTANCE, List.of(), List.of(p(0, "D4")));
    assertEquals(Joker.INSTANCE, new RulePlayPolicy().choose(v).card());
  }

  @Test
  void oppositionKeepsOpeningJokerWithStrongClubs() {
    var v = view(1, 0, Trump.NO_TRUMP, 17, "CK CQ CJ C4 JK D2 D3 H2 H3 S2", "",
        new CardFriend(card("CA")), UnrevealedFriend.INSTANCE, List.of(), List.of(p(0, "D4")));
    assertNotEquals(Joker.INSTANCE, new RulePlayPolicy().choose(v).card());
  }
}
