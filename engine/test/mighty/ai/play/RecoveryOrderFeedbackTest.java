package mighty.ai.play;

import static mighty.ai.play.PlayTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import mighty.game.*;
import mighty.game.friend.*;
import mighty.model.*;
import org.junit.jupiter.api.Test;

class RecoveryOrderFeedbackTest {

  private final RulePlayPolicy policy = new RulePlayPolicy();

  @Test
  void keepsPossibleSupportingFriendsTopWithoutDeclaringTheirTeam() {
    var first = row(1, Trump.HEART, p(0, "C2"), p(1, "CK"), p(2, "H2"), p(3, "C3"), p(4, "C4"));
    var observation = view(0, 0, Trump.HEART, 16, "SA JK HA HK HQ HJ H8 H9 S2", "D4 D5 D6",
        new CardFriend(card("CQ")), UnrevealedFriend.INSTANCE, List.of(first),
        List.of(p(2, "DA"), p(3, "DJ"), p(4, "DQ")));
    var facts = new PlayFacts(observation);
    assertFalse(facts.teams().isAlly(new Seat(2)));
    assertTrue(facts.teams().isLikelyAlly(new Seat(2)));
    assertEquals(card("S2"), policy.choose(observation).card());
  }

  @Test
  void preservesSideEntryWhenJokerFriendCannotWinOpening() {
    var observation = view(0, 0, Trump.NO_TRUMP, 15, "SA DJ DQ DK DA CA H2 H5 H10 HJ",
        "S2 SJ C2", new CardFriend(Joker.INSTANCE), UnrevealedFriend.INSTANCE, List.of(),
        List.of());
    assertEquals(card("DJ"), policy.choose(observation).card());
  }

  @Test
  void soloKeepsMightyForRecoveryAndDevelopsWithAceThenJoker() {
    var first = view(0, 0, Trump.NO_TRUMP, 20, "SA DA H3 H4 H8 HJ HQ HA CA JK", "D2 C2 C9",
        SpecialFriend.NONE, NoFriend.INSTANCE, List.of(), List.of());
    assertEquals(card("HA"), policy.choose(first).card());
    var history = List.of(
        row(1, Trump.NO_TRUMP, p(0, "HA"), p(1, "H2"), p(2, "H5"), p(3, "H6"), p(4, "H7")));
    var next = view(0, 0, Trump.NO_TRUMP, 20, "SA DA H3 H4 H8 HJ HQ CA JK", "D2 C2 C9",
        SpecialFriend.NONE, NoFriend.INSTANCE, history, List.of());
    var action = policy.choose(next);
    assertEquals(Joker.INSTANCE, action.card());
    assertEquals(Suit.HEART, ((mighty.game.action.JokerLead) action).declaredSuit());
  }

  @Test
  void fixedDiamondDiscardPreservesItsTrumpLength() {
    var own = mighty.data.CardParser.parseList("SA D2 D6 D9 D10 DA H3 H6 H8 HJ HK C2 C7");
    var discarded = new mighty.ai.preparation.RuleDiscardPolicy().choose(
        new mighty.game.observation.DiscardView(new Seat(0),
            new mighty.game.bidding.Bid(15, Trump.DIAMOND), own));
    assertFalse(discarded.contains(card("D2")));
    assertEquals(Set.of(card("HJ"), card("C2"), card("C7")), Set.copyOf(discarded));
  }

  @Test
  void keepsMightyAheadOfJokerWhenRecoveringPointHeavyTrumpReturn() {
    var first = row(1, Trump.HEART, p(0, "C2"), p(1, "CA"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var current = List.of(p(1, "H10"), p(2, "HK"), p(3, "HQ"), p(4, "H2"));
    var observation = view(0, 0, Trump.HEART, 16,
        "SA JK HA HJ H8 H9 D2 S2 C6", "S3 D3 D4", new CardFriend(card("CA")),
        UnrevealedFriend.INSTANCE, List.of(first), current);
    assertEquals(card("SA"), policy.choose(observation).card());
  }

  @Test
  void usesTrumpAceBeforeJokerWhenMightyIsNotAvailable() {
    var first = row(1, Trump.HEART, p(0, "C2"), p(1, "CA"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var observation = view(0, 0, Trump.HEART, 16, "JK HA HJ H8 H9 D2 S2 S4 C6", "S3 D3 D4",
        new CardFriend(card("CA")), UnrevealedFriend.INSTANCE, List.of(first),
        List.of(p(1, "H10"), p(2, "HK"), p(3, "HQ"), p(4, "H2")));
    assertEquals(card("HA"), policy.choose(observation).card());
  }

  @Test
  void usesOrdinarySideTopInsteadOfJokerForOnlyPossibleOverruff() {
    var first = row(1, Trump.SPADE, p(0, "D2"), p(1, "DA"), p(2, "D3"), p(3, "D4"), p(4, "D5"));
    var observation = view(3, 3, Trump.SPADE, 17, "DK JK S2 S3 S4 S5 S6 S7 C2", "H2 H3 H4",
        new CardFriend(card("DA")), UnrevealedFriend.INSTANCE, List.of(first),
        List.of(p(1, "D10"), p(2, "DQ")));
    assertEquals(card("DK"), policy.choose(observation).card());
  }

  @Test
  void pressuresWithPointTrumpBeforeShortCoverForcesItIntoMighty() {
    var first = row(1, Trump.HEART, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var observation = view(2, 4, Trump.HEART, 16, "H5 HK D2 D3 D4 S2 S3 S4 C6", "",
        new CardFriend(card("CA")), UnrevealedFriend.INSTANCE, List.of(first),
        List.of(p(0, "H2"), p(1, "H9")));
    assertEquals(card("HK"), policy.choose(observation).card(),
        () -> policy.recommend(observation).reason());
  }
}
