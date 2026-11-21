package mighty.ai.play;

import static mighty.ai.play.PlayTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import mighty.game.Role;
import mighty.game.Seat;
import mighty.game.bidding.Bid;
import mighty.game.action.JokerLead;
import mighty.game.observation.Context;
import mighty.game.observation.ObservationFactory;
import mighty.game.action.StandardPlay;
import mighty.game.friend.*;
import mighty.game.observation.View;
import mighty.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 이번 피드백과 기존 보존 원칙을 같은 입력에서 함께 검사합니다.
 */
class PlayFeedbackRegressionTest {

  private final RulePlayPolicy policy = new RulePlayPolicy();

  @ParameterizedTest
  @CsvSource({"C, Q, J", "C, 4, JK", "H, Q, J", "H, 4, JK"})
  void cashesPrimaryBeforeCallingMighty(String suit, String lastRank, String next) {
    var trump = Trump.NO_TRUMP;
    var friend = new CardFriend(card("SA"));
    var hidden = UnrevealedFriend.INSTANCE;
    String side = suit.equals("C") ? "H" : "C";
    String buried = side + "J " + side + "10 " + side + "2";
    String rest = suit + "J " + suit + "6 " + suit + "2 SK S3 D4 " + side + "9 JK";
    var first = row(1, trump, p(0, suit + "A"), p(1, suit + "3"), p(2, suit + "5"),
        p(3, suit + "7"), p(4, suit + "8"));
    var second = row(2, trump, p(0, suit + "K"), p(1, suit + "9"), p(2, suit + "10"),
        p(3, "D3"), p(4, suit + lastRank));
    assertEquals(card(suit + "A"), policy.choose(view(0, 0, trump, 15,
        suit + "A " + suit + "K " + rest, buried, friend, hidden, List.of(), List.of())).card());
    assertEquals(card(suit + "K"), policy.choose(view(0, 0, trump, 15,
        suit + "K " + rest, buried, friend, hidden, List.of(first), List.of())).card());
    var third = policy.choose(view(0, 0, trump, 15, rest, buried, friend, hidden,
        List.of(first, second), List.of()));
    if (next.equals("JK")) {
      assertEquals(new JokerLead(((StandardCard) card(suit + "J")).suit()), third);
    } else {
      assertEquals(card(suit + next), third.card());
    }
  }

  @ParameterizedTest
  @CsvSource({
      "HJ, HQ, 15, true",
      "C3, HQ, 15, false",
      "H10, HJ, 15, false",
      "HJ, HQ, 16, false"
  })
  void sweepsOneBlockerBeforeOppositionCanCallJoker(String earlyDiscard, String top,
      int bid, boolean sweeps) {
    var first = row(1, Trump.NO_TRUMP, p(0, "D2"), p(1, "DA"), p(2, "D6"), p(3, "D4"),
        p(4, "DJ"));
    var second = row(2, Trump.NO_TRUMP, p(1, "D3"), p(2, "D7"), p(3, "D5"),
        p(4, earlyDiscard), p(0, "DK"));
    var third = row(3, Trump.NO_TRUMP, p(0, "SA"), p(1, "SJ"), p(2, "S7"), p(3, "S2"),
        p(4, "S3"));
    var fourth = row(4, Trump.NO_TRUMP, p(0, "HA"), p(1, "H8"), p(2, "H9"), p(3, "H7"),
        p(4, "H5"));
    View view = view(0, 0, Trump.NO_TRUMP, bid, "JK " + top + " H3 H2 C6 C5", "S6 C10 S5",
        new CardFriend(card("DA")), new RevealedFriend(new Seat(1)),
        List.of(first, second, third, fourth), List.of());

    if (sweeps) {
      assertEquals(new JokerLead(Suit.HEART), policy.choose(view));
    } else {
      assertEquals(card("H2"), policy.choose(view).card());
    }
  }

  @Test
  void playsTopThenJokerBeforeSpendingMightyAfterFriendReturns() {
    var first = row(1, Trump.NO_TRUMP, p(0, "D2"), p(1, "DA"), p(2, "D6"), p(3, "D4"),
        p(4, "DJ"));
    var second = row(2, Trump.NO_TRUMP, p(1, "D3"), p(2, "D7"), p(3, "D5"), p(4, "HJ"),
        p(0, "DK"));
    var third = row(3, Trump.NO_TRUMP, p(0, "HA"), p(1, "H8"), p(2, "H9"), p(3, "H7"),
        p(4, "H5"));
    var selection = new CardFriend(card("DA"));
    var friend = new RevealedFriend(new Seat(1));
    View afterReturn = view(0, 0, Trump.NO_TRUMP, 15, "SA HA HQ H3 H2 C6 C5 JK",
        "S6 C10 S5", selection, friend, List.of(first, second), List.of());
    View afterTop = view(0, 0, Trump.NO_TRUMP, 15, "SA HQ H3 H2 C6 C5 JK",
        "S6 C10 S5", selection, friend, List.of(first, second, third), List.of());

    assertEquals(card("HA"), policy.choose(afterReturn).card());
    assertEquals(new JokerLead(Suit.HEART), policy.choose(afterTop));
  }

  @Test
  void preservesJokerAfterOrdinarySideSupportOnLowTrumpLead() {
    var first = row(1, Trump.DIAMOND, p(0, "HA"), p(1, "H2"), p(2, "H7"), p(3, "H4"),
        p(4, "H9"));
    var second = row(2, Trump.DIAMOND, p(0, "DK"), p(1, "D5"), p(2, "D10"), p(3, "DJ"),
        p(4, "D4"));
    var third = row(3, Trump.DIAMOND, p(0, "S7"), p(1, "S5"), p(2, "SK"), p(3, "SQ"),
        p(4, "S6"));
    var fourth = row(4, Trump.DIAMOND, p(2, "D2"), p(3, "D6"), p(4, "D8"), p(0, "DQ"),
        p(1, "H5"));
    View view = view(2, 0, Trump.DIAMOND, 16, "JK S3 H3 C4 C5 C6", "",
        new CardFriend(Joker.INSTANCE), UnrevealedFriend.INSTANCE,
        List.of(first, second, third, fourth), List.of(p(0, "D7"), p(1, "C8")));
    var facts = new PlayFacts(view);

    assertTrue(FriendSupportIntent.preserveJoker(view, facts));
    assertTrue(facts.winningActions().stream().anyMatch(a -> a.card() instanceof Joker));
    assertNotEquals(Joker.INSTANCE, policy.choose(view).card());
  }

  @Test
  void preservesJokerWhenNoTrumpDeclarerContinuesDisclosedPrimary() {
    var first = row(1, Trump.NO_TRUMP, p(0, "CA"), p(1, "C10"), p(2, "CJ"), p(3, "C7"),
        p(4, "C2"));
    var second = row(2, Trump.NO_TRUMP, p(0, "H7"), p(1, "HK"), p(2, "HA"), p(3, "H4"),
        p(4, "H9"));
    var third = row(3, Trump.NO_TRUMP, p(2, "H8"), p(3, "HJ"), p(4, "D7"), p(0, "SA"),
        p(1, "H2"));
    var original = view(2, 0, Trump.NO_TRUMP, 20, "S5 S6 D2 S10 D4 JK H6", "",
        new CardFriend(Joker.INSTANCE), UnrevealedFriend.INSTANCE,
        List.of(first, second, third), List.of(p(0, "C3"), p(1, "SJ")));
    var context = new Context(original.context().playOrder(), original.context().declarer(),
        original.context().friendStatus(), original.context().history(),
        List.of(new Bid(15, Trump.CLUB), original.contract()));
    View view = ObservationFactory.create(original.self(), original.privateInfo(),
        original.contract(), original.friendSelection(), context, original.trick());
    var facts = new PlayFacts(view);

    assertTrue(FriendSupportIntent.preserveJoker(view, facts));
    assertTrue(FriendSupportIntent.requestedSpecial(view, facts).isPresent());
    assertNotEquals(Joker.INSTANCE, policy.choose(view).card());
  }

  @Test
  void disclosedNoTrumpPrimaryStillUsesRequestedMightyBeforeAce() {
    var first = row(1, Trump.NO_TRUMP, p(0, "C2"), p(1, "C3"), p(2, "CA"), p(3, "C4"), p(4, "C5"));
    View original = view(3, 0, Trump.NO_TRUMP, 15, "SA HA H2 D2 D3 C6 S2 S3 S4", "",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(first), List.of(p(2, "H3")));
    var context = new Context(original.context().playOrder(),
        original.context().declarer(), original.context().friendStatus(),
        original.context().history(),
        List.of(new Bid(13, Trump.HEART), original.contract()));
    View disclosed = ObservationFactory.create(original.self(),
        original.privateInfo(), original.contract(), original.friendSelection(), context,
        original.trick());
    assertEquals(card("SA"), policy.choose(disclosed).card());
  }

  @Test
  void waitsForBehindDeclarerOnOppositionSideLead() {
    View view = view(3, 0, Trump.DIAMOND, 15, "SA D2 D3 D4 D5 D6 H2 H3 H4", "",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(row(1, Trump.DIAMOND,
            p(0, "S2"), p(1, "S3"), p(2, "SK"), p(3, "S4"), p(4, "S5"))), List.of(p(2, "C4")));
    assertNotEquals(card("SA"), policy.choose(view).card());
  }

  @Test
  void stillTakesOrdinarySideMasterBeforeBehindDeclarer() {
    View view = view(3, 0, Trump.DIAMOND, 15, "SA CA C3 D2 D3 D4 H2 H3 H4", "",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(row(1, Trump.DIAMOND,
            p(0, "S2"), p(1, "S3"), p(2, "SK"), p(3, "S4"), p(4, "S5"))), List.of(p(2, "C4")));
    assertEquals(card("CA"), policy.choose(view).card());
  }

  @Test
  void usesMightyRatherThanGivingForcedPointsWhileWaiting() {
    View view = view(3, 0, Trump.DIAMOND, 15, "SA C10 DJ DQ DK HJ HQ HK HA", "",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(row(1, Trump.DIAMOND,
            p(0, "S2"), p(1, "S3"), p(2, "SK"), p(3, "S4"), p(4, "S5"))), List.of(p(2, "C4")));
    assertEquals(card("SA"), policy.choose(view).card());
  }

  @Test
  void continuesDrawingScatteredTrumpsWhenLengthIsSufficient() {
    var first = row(1, Trump.SPADE, p(0, "CA"), p(1, "S7"), p(2, "S8"), p(3, "S10"), p(4, "SK"));
    var second = row(2, Trump.SPADE, p(4, "HA"), p(0, "SQ"), p(1, "SJ"), p(2, "DA"), p(3, "JK"));
    View view = view(2, 2, Trump.SPADE, 15, "S2 S3 S4 S5 S6 C2 C3 H2", "D2 D3 D4",
        new CardFriend(Joker.INSTANCE), new RevealedFriend(new Seat(3)),
        List.of(first, second), List.of());
    var action = policy.choose(view);
    assertEquals(Suit.SPADE, ((StandardCard) action.card()).suit());
    assertEquals(0, action.card().points());
  }

  @Test
  void remainingFriendMightySupportsDrawingEvenWithShortOwnTrump() {
    String owned = "S8 S9 C2 C3";
    var history = countedHistory(owned, "D2 D3 D4", "SA SQ S10 JK", "SK SJ S4 DA", List.of(), null);
    View view = view(0, 0, Trump.SPADE, 15, owned, "D2 D3 D4", new CardFriend(card("DA")),
        new RevealedFriend(new Seat(1)), history, List.of());
    var facts = new PlayFacts(view);
    assertTrue(new TrumpControl(view, facts).pressureLead(facts.legalActions()).isPresent());
    var unsupported = view(0, 0, Trump.SPADE, 15, owned, "D2 D3 D4", new CardFriend(Joker.INSTANCE),
        new RevealedFriend(new Seat(1)), history, List.of());
    var unsupportedFacts = new PlayFacts(unsupported);
    assertTrue(new TrumpControl(unsupported, unsupportedFacts)
        .pressureLead(unsupportedFacts.legalActions()).isEmpty());
  }

  @Test
  void jokerSweepCanEstablishOwnLowTrumpAfterMasterRun() {
    String owned = "SA S7 JK";
    var history = countedHistory(owned, "C2 C3 C4", "DA S2 S3 S4 S5 S6 S8 S9 S10 SK",
        "SJ SQ", List.of(), null);
    View view = view(0, 1, Trump.SPADE, 16, owned, "", new CardFriend(card("D2")),
        new RevealedFriend(new Seat(2)), history, List.of());
    assertTrue(new TrumpControl(view, new PlayFacts(view)).canEstablishTailAfterSweep());
    assertInstanceOf(JokerLead.class, policy.choose(view));
  }

  @Test
  void spendsConnectedPointRuffInsteadOfSacrificingItsOnlyLowCover() {
    var first = row(1, Trump.SPADE, p(0, "CA"), p(1, "DA"), p(2, "JK"), p(3, "S10"), p(4, "SK"));
    View view = view(2, 1, Trump.SPADE, 16, "S7 SJ SQ C2 C3 C4 C5 D3 D4", "",
        new CardFriend(card("C6")), new RevealedFriend(new Seat(0)),
        List.of(first), List.of(p(1, "HA")));
    var facts = new PlayFacts(view);
    assertEquals(card("SQ"),
        RuffPlan.choose(view, facts, facts.legalActions()).orElseThrow().card());
  }

  @Test
  void supportsOpeningWithWaterRuffBeforeMighty() {
    View view = view(1, 0, Trump.DIAMOND, 16, "SA D2 D4 D5 S3 S4 H2 H3 H4 H5", "",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(),
        List.of(p(0, "C8")));
    var chosen = policy.choose(view).card();
    assertInstanceOf(StandardCard.class, chosen);
    assertEquals(Suit.DIAMOND, ((StandardCard) chosen).suit());
    assertEquals(0, chosen.points());
  }

  @Test
  void doesNotFeedConnectedPointsToDeclarerOpeningKing() {
    View view = view(1, 0, Trump.NO_TRUMP, 17, "SJ S10 S4 H2 H3 H4 D2 D3 D4 C2", "",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(), List.of(p(0, "SK")));
    assertEquals(card("S4"), policy.choose(view).card());
  }

  @Test
  void doesNotStackPointTrumpsWhenAnotherOpponentAlreadyPresses() {
    var first = row(1, Trump.HEART, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    View view = view(2, 0, Trump.HEART, 17, "H5 HJ HK S2 S3 D2 D3 C6 C7", "",
        new CardFriend(card("C5")), new RevealedFriend(new Seat(4)), List.of(first),
        List.of(p(0, "H2"), p(1, "H10")));
    assertEquals(card("H5"), policy.choose(view).card());
  }

  @Test
  void stillPressesBelowOwnAnchorAfterAnInterveningRankWasSpent() {
    var first = row(1, Trump.HEART, p(4, "C2"), p(0, "C3"), p(1, "HQ"), p(2, "C4"), p(3, "C5"));
    View view = view(2, 4, Trump.HEART, 17, "H5 HJ HK S2 S3 D2 D3 C6 C7", "",
        new CardFriend(card("C3")), new RevealedFriend(new Seat(0)), List.of(first),
        List.of(p(1, "H2")));
    // Q가 이미 빠졌으므로 J와 K는 연결 신호로 바뀔 수 있지만 물패로 돌아가면 안 됩니다.
    assertEquals(1, policy.choose(view).card().points());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void pressesWithJackFromThreeTrumpsRegardlessOfSpecialFriend(boolean jokerFriend) {
    var first = row(1, Trump.HEART, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var selection = new CardFriend(jokerFriend ? Joker.INSTANCE : card("SA"));
    View view = view(1, 0, Trump.HEART, 17, "H5 HJ HK S2 S3 D2 D3 C6 C7", "", selection,
        UnrevealedFriend.INSTANCE, List.of(first), List.of(p(0, "H2")));
    assertEquals(card("HJ"), policy.choose(view).card());
  }

  @Test
  void takesFriendQueenWithPointTrumpWhileJokerIsStillOutside() {
    var first = row(1, Trump.HEART, p(4, "C2"), p(0, "SA"), p(1, "C3"), p(2, "C4"), p(3, "C5"));
    View view = view(1, 4, Trump.HEART, 17, "HA HK H5 S2 S3 D2 D3 C6 C7", "",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(0)), List.of(first),
        List.of(p(0, "HQ")));
    var action = policy.choose(view);
    assertEquals(1, action.card().points());
    assertTrue(new PlayFacts(view).winningActions().contains(action));
  }

  @ParameterizedTest
  @EnumSource(value = Trump.class, names = {"SPADE", "NO_TRUMP"})
  void leadsPointsWhenFourPointsRemainEvenWithoutKnownWinningAlly(Trump trump) {
    var first = row(1, trump, p(0, "C2"), p(1, "CA"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var second = row(2, trump, p(1, "H9"), p(2, "H2"), p(3, "H3"), p(4, "H4"), p(0, "D2"));
    View view = view(1, 0, trump, 17, "H5 H6 H7 H8 H10 HJ HQ HK", "",
        new CardFriend(Joker.INSTANCE), UnrevealedFriend.INSTANCE, List.of(first, second),
        List.of());
    assertTrue(new PlayFacts(view).cards().isVoid(view.context().declarer(), Suit.HEART));
    assertEquals(1, policy.choose(view).card().points());
  }

  @ParameterizedTest
  @EnumSource(value = Trump.class, names = {"HEART", "NO_TRUMP"})
  void retainsLowCardsSupportingDeclaredDiamondDefenseAfterOneLead(Trump trump) {
    var first = row(1, trump, p(0, "D10"), p(1, "D2"), p(2, "D3"), p(3, "D4"), p(4, "D5"));
    View view = view(1, 0, trump, 17, "DA D6 D7 HA HK HQ HJ SK SQ", "",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(first), List.of(p(0, "CA")));
    var decision = policy.recommend(view);
    assertNotEquals(Suit.DIAMOND, ((StandardCard) decision.action().card()).suit(),
        decision.toString()
            + " / 받침 보존=" + new CardResources(view, new PlayFacts(view)).reserved(card("D6")));
  }

  @ParameterizedTest
  @EnumSource(value = Role.class, names = {"DECLARER", "FRIEND", "OPPOSITION"})
  void spendsBlockedPointBeforeLastTrickForEveryRole(Role role) {
    var current = List.of(p(0, "CA"));
    int declarer = role == Role.DECLARER ? 1 : 4;
    FriendSelection selected =
        role == Role.FRIEND ? new CardFriend(Joker.INSTANCE) : new CardFriend(card("DA"));
    FriendStatus friend = new RevealedFriend(new Seat(role == Role.FRIEND ? 1 : 0));
    for (String[] sample : new String[][]{{"SJ", "S8", "SA"}, {"SQ", "S7", "SK"},
        {"S10", "S6", "SJ"}}) {
      String owned = sample[0] + " " + sample[1];
      var history = countedHistory(owned, "C2 C3 C4", "DA JK", sample[2] + " S2 S3", current, null);
      View view = view(1, declarer, Trump.SPADE, 17, owned,
          role == Role.DECLARER ? "C2 C3 C4" : "", selected, friend, history, current);
      assertEquals(role, view.role());
      assertEquals(card(sample[0]), policy.choose(view).card(), owned);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"D2 H2", "D4 H6"})
  void keepsMightyAvailableForWaterEvenWithFourConnectedOrdinarySpades(String water) {
    View view = view(0, 0, Trump.NO_TRUMP, 17, "SK SQ SJ S10 S9 S8 S7 JK " + water, "C2 C3 C4",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(), List.of());
    assertNotEquals(Suit.SPADE, ((StandardPlay) policy.choose(view)).card().suit());
  }

  @ParameterizedTest
  @ValueSource(strings = {"D2", "H2", "S2"})
  void callsMightyWithWaterWhenEightCardPrimaryNeedsNoEarlyHelp(String water) {
    View view = view(0, 0, Trump.NO_TRUMP, 17, "CA CK CQ C5 C6 C7 C8 C9 JK " + water, "C2 C3 C4",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(), List.of());
    assertEquals(card(water), policy.choose(view).card());
  }

  @Test
  void ownJokerAndGunStillAllowMightyToHandleSideWater() {
    View view = view(0, 0, Trump.NO_TRUMP, 17, "CA CK CQ C3 C5 C6 C7 C8 JK D2", "D3 H3 H4",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(), List.of());
    assertEquals(card("D2"), policy.choose(view).card());
  }
}
