package mighty.ai.play;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;
import mighty.data.CardParser;
import mighty.game.*;
import mighty.game.action.*;
import mighty.game.bidding.Bid;
import mighty.game.friend.*;
import mighty.game.observation.*;
import mighty.game.rule.Rules;
import mighty.game.trick.*;
import mighty.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 구 번호·특정 카드 대신 공개 요청과 카운팅으로 연결하는 회귀 검사입니다.
 */
class SupportFlowTest {

  @ParameterizedTest
  @CsvSource({"16,SK", "17,SK", "20,SQ"})
  void jokerFriendUsesOwnMightyBeforeFollowingWithReturnCard(int points, String lead) {
    var original = view(1, Trump.NO_TRUMP, "SA JK S2 H2 H3 H4 D2 D3 D4 C2",
        new CardFriend(Joker.INSTANCE),
        -1, List.of(), List.of(play(0, lead)));
    var observation = new View(original.self(), original.privateInfo(), original.role(),
        new Bid(points, Trump.NO_TRUMP), original.friendSelection(), original.context(),
        original.trick());
    assertEquals(card("SA"), policy.choose(observation).card());
  }

  @Test
  void jokerFriendReturnsPreservedSpadeAfterMighty() {
    var first = trick(1, Trump.NO_TRUMP, play(0, "SK"), play(1, "SA"), play(2, "S3"),
        play(3, "S4"), play(4, "S5"));
    var observation = view(1, Trump.NO_TRUMP, "JK S2 H2 H3 H4 D2 D3 D4 C2",
        new CardFriend(Joker.INSTANCE),
        -1, List.of(first), List.of());
    assertEquals(card("S2"), policy.choose(observation).card());
  }

  @Test
  void jokerRemainsAvailableForDeclarersLaterRequestInAnotherSuit() {
    var first = trick(1, Trump.NO_TRUMP, play(0, "SK"), play(1, "SA"), play(2, "S6"),
        play(3, "S7"), play(4, "S8"));
    var second = trick(2, Trump.NO_TRUMP, play(1, "S2"), play(2, "S3"), play(3, "S4"),
        play(4, "S5"), play(0, "SQ"));
    var observation = view(1, Trump.NO_TRUMP, "JK C2 C4 H2 H3 H4 D2 D3",
        new CardFriend(Joker.INSTANCE),
        -1, List.of(first, second), List.of(play(0, "C5")));
    assertEquals(Joker.INSTANCE, policy.choose(observation).card());
  }

  @Test
  void declarersPrimaryRunAfterMightyRemovalDoesNotCallJokerAgain() {
    var first = trick(1, Trump.NO_TRUMP, play(0, "SK"), play(1, "SA"), play(2, "S6"),
        play(3, "S7"), play(4, "S8"));
    var second = trick(2, Trump.NO_TRUMP, play(1, "S2"), play(2, "S3"), play(3, "S4"),
        play(4, "S5"), play(0, "SQ"));
    var observation = view(1, Trump.NO_TRUMP, "JK C2 C4 H2 H3 H4 D2 D3",
        new CardFriend(Joker.INSTANCE),
        -1, List.of(first, second), List.of(play(0, "S9")));
    assertNotEquals(Joker.INSTANCE, policy.choose(observation).card());
  }

  @Test
  void singletonMightyRouteDoesNotWasteJokerOnSpades() {
    var first = trick(1, Trump.NO_TRUMP, play(0, "SK"), play(1, "SA"), play(2, "S3"),
        play(3, "S4"), play(4, "S5"));
    var observation = view(1, Trump.NO_TRUMP, "JK H2 H3 H4 D2 D3 D4 C2 C4",
        new CardFriend(Joker.INSTANCE),
        -1, List.of(first), List.of());
    assertNotEquals(Joker.INSTANCE, policy.choose(observation).card());
    var facts = new PlayFacts(observation);
    var jokers = facts.legalActions().stream().filter(JokerLead.class::isInstance).toList();
    assertNotEquals(Suit.SPADE,
        ((JokerLead) JokerSuitPlan.choose(jokers, observation, facts)).declaredSuit());
  }

  @Test
  void jokerRecoveryReadsDeclarersFreeDiscardInsteadOfOldPrimary() {
    var first = trick(1, Trump.NO_TRUMP, play(0, "SK"), play(1, "S2"), play(2, "SA"),
        play(3, "S3"), play(4, "S4"));
    var second = new PublicTrick(new TrickNumber(2),
        List.of(play(2, "H2"), play(3, "H3"), play(4, "H4"),
            play(0, "D2"), new PublicPlay(new Seat(1), new JokerFollow())), new Seat(1));
    var observation = view(1, Trump.NO_TRUMP, "D3 D4 C2 C4 H5 H6 S5 S6",
        new CardFriend(Joker.INSTANCE),
        1, List.of(first, second), List.of());
    assertEquals(card("D4"), policy.choose(observation).card());
  }

  @Test
  void followingDeclaredSuitIsNotAFreeDiscardSignal() {
    var first = trick(1, Trump.NO_TRUMP, play(0, "SK"), play(1, "S2"), play(2, "SA"),
        play(3, "S3"), play(4, "S4"));
    var second = new PublicTrick(new TrickNumber(2),
        List.of(play(2, "D2"), play(3, "D3"), play(4, "D4"),
            play(0, "D5"), new PublicPlay(new Seat(1), new JokerFollow())), new Seat(1));
    var observation = view(1, Trump.NO_TRUMP, "D6 D7 C2 C4 H5 H6 S5 S6",
        new CardFriend(Joker.INSTANCE),
        1, List.of(first, second), List.of());
    assertEquals(card("S6"), policy.choose(observation).card());
  }

  @Test
  void recoveryRequestEndsWhenDeclarerRegainsLead() {
    var first = trick(1, Trump.NO_TRUMP, play(0, "SK"), play(1, "S2"), play(2, "SA"),
        play(3, "S3"), play(4, "S4"));
    var second = new PublicTrick(new TrickNumber(2),
        List.of(play(2, "H2"), play(3, "H3"), play(4, "H4"),
            play(0, "D2"), new PublicPlay(new Seat(1), new JokerFollow())), new Seat(1));
    var third = trick(3, Trump.NO_TRUMP, play(1, "D4"), play(2, "D5"), play(3, "D6"),
        play(4, "D7"), play(0, "DA"));
    var observation = view(1, Trump.NO_TRUMP, "D3 C2 C4 H5 H6 S5 S6",
        new CardFriend(Joker.INSTANCE),
        1, List.of(first, second, third), List.of(play(0, "S9")));
    assertFalse(
        FriendSupportIntent.returnSuit(observation).filter(Suit.DIAMOND::equals).isPresent());
  }

  @Test
  void earlierJokerDiscardIsNotReinterpretedAfterLaterMightyRemoval() {
    var first = trick(1, Trump.NO_TRUMP, play(0, "C2"), play(1, "C3"), play(2, "CA"),
        play(3, "C4"), play(4, "C5"));
    var second = new PublicTrick(new TrickNumber(2),
        List.of(play(2, "H2"), play(3, "H3"), play(4, "H4"),
            play(0, "D2"), new PublicPlay(new Seat(1), new JokerFollow())), new Seat(1));
    var third = trick(3, Trump.NO_TRUMP, play(1, "S2"), play(2, "S3"), play(3, "S4"),
        play(4, "S5"), play(0, "SK"));
    var fourth = trick(4, Trump.NO_TRUMP, play(0, "SQ"), play(1, "SA"), play(2, "S6"),
        play(3, "S7"), play(4, "S8"));
    var observation = view(1, Trump.NO_TRUMP, "D3 D4 H6 H7 S9 S10", new CardFriend(Joker.INSTANCE),
        1, List.of(first, second, third, fourth), List.of());
    assertEquals(card("S10"), policy.choose(observation).card());
  }

  private final RulePlayPolicy policy = new RulePlayPolicy();

  private static Card card(String code) {
    return CardParser.parse(code);
  }

  private static PublicPlay play(int seat, String code) {
    return new PublicPlay(new Seat(seat), new StandardPlay((StandardCard) card(code), false));
  }

  private static PublicTrick trick(int number, Trump trump, PublicPlay... plays) {
    var cards = List.of(plays);
    var count = new TrickNumber(number);
    return new PublicTrick(count, cards,
        Rules.currentWinner(trump, count, cards).orElseThrow().seat());
  }

  private static View view(int self, Trump trump, String owned, FriendSelection selection,
      int friend, List<PublicTrick> history, List<PublicPlay> current) {
    var seats = IntStream.range(0, 5).mapToObj(Seat::new).toList();
    var order = PlayOrder.from(new TableLayout(GameMode.FIVE_PLAYER, seats), new HashSet<>(seats));
    var hand = CardParser.parseList(owned);
    PrivateInfo privateInfo = self == 0
        ? new DeclarerInfo(hand, CardParser.parseList("C9 C10 CJ")) : new NonDeclarerInfo(hand);
    return ObservationFactory.create(new Seat(self), privateInfo, new Bid(20, trump), selection,
        new Context(order, new Seat(0), friend < 0 ? UnrevealedFriend.INSTANCE
            : new RevealedFriend(new Seat(friend)), history), current);
  }

  @Test
  void firstTrickFriendReturnsSpadesAfterThreeSideWinners() {
    var history = List.of(
        trick(1, Trump.NO_TRUMP, play(0, "S2"), play(1, "SK"), play(2, "S3"), play(3, "S5"),
            play(4, "S6")),
        trick(2, Trump.NO_TRUMP, play(1, "HA"), play(2, "H2"), play(3, "H3"), play(4, "H4"),
            play(0, "H5")),
        trick(3, Trump.NO_TRUMP, play(1, "DA"), play(2, "D2"), play(3, "D3"), play(4, "D4"),
            play(0, "D5")),
        trick(4, Trump.NO_TRUMP, play(1, "CA"), play(2, "C2"), play(3, "C3"), play(4, "C4"),
            play(0, "C5")));
    var observation = view(1, Trump.NO_TRUMP, "S4 S8 H6 H7 D6 C6", SpecialFriend.FIRST_TRICK,
        1, history, List.of());
    assertEquals(card("S8"), policy.choose(observation).card());
  }

  @Test
  void ordinaryWinnerOfLaterRequestReturnsThatSuitBeforeOpeningSuit() {
    var history = List.of(
        trick(1, Trump.NO_TRUMP, play(0, "HK"), play(1, "H2"), play(2, "H3"), play(3, "H4"),
            play(4, "H5")),
        trick(2, Trump.NO_TRUMP, play(0, "D2"), play(1, "DA"), play(2, "D3"), play(3, "D5"),
            play(4, "D6")));
    var observation = view(1, Trump.NO_TRUMP, "JK D4 D8 H6 C2 C3 S2 S3",
        new CardFriend(Joker.INSTANCE),
        1, history, List.of());
    assertEquals(card("D8"), policy.choose(observation).card());
  }

  @Test
  void declarerTakesPrimaryReturnEvenWhenLowerFriendCardIsAlreadyTop() {
    var first = trick(1, Trump.NO_TRUMP, play(0, "HA"), play(1, "H2"), play(2, "H3"),
        play(3, "H4"), play(4, "H5"));
    var secondCards = List.of(play(0, "D2"), new PublicPlay(new Seat(1), new JokerFollow()),
        play(2, "DA"), play(3, "DQ"), play(4, "D10"));
    var second = new PublicTrick(new TrickNumber(2), secondCards, new Seat(1));
    var observation = view(0, Trump.NO_TRUMP, "DK DJ D6 D7 D9 SA HK C8",
        new CardFriend(Joker.INSTANCE),
        1, List.of(first, second),
        List.of(play(1, "D8"), play(2, "D3"), play(3, "D4"), play(4, "D5")));
    assertTrue(new PlayFacts(observation).isSuitTop(card("D8")));
    assertEquals(card("DK"), policy.choose(observation).card());
  }

  @ParameterizedTest
  @CsvSource({"HEART", "NO_TRUMP"})
  void returnsHighestWaterAfterDesignatedAceRun(Trump trump) {
    var history = List.of(
        trick(1, trump, play(0, "H2"), play(1, "HA"), play(2, "H3"), play(3, "H4"), play(4, "H6")),
        trick(2, trump, play(1, "HK"), play(2, "H7"), play(3, "H9"), play(4, "H10"),
            play(0, "HJ")));
    var observation = view(1, trump, "H5 H8 D2 D3 D4 C2 C3 S2", new CardFriend(card("HA")),
        1, history, List.of());
    assertEquals(card("H8"), policy.choose(observation).card());
  }

  @Test
  void followsSideDescendingAfterAllOpponentsShowVoid() {
    var first = trick(1, Trump.NO_TRUMP, play(0, "SA"), play(1, "S2"), play(2, "C2"),
        play(3, "C3"), play(4, "C4"));
    var observation = view(1, Trump.NO_TRUMP, "S4 S8 H2 H3 H4 H5 D2 D3 D4",
        SpecialFriend.FIRST_TRICK,
        1, List.of(first), List.of(play(0, "SK")));
    assertEquals(card("S8"), policy.choose(observation).card());
  }

  @Test
  void followsSideDescendingWhileOpponentsMayStillFollow() {
    var first = trick(1, Trump.NO_TRUMP, play(0, "SA"), play(1, "S2"), play(2, "S3"),
        play(3, "S5"), play(4, "S6"));
    var observation = view(1, Trump.NO_TRUMP, "S4 S8 H2 H3 H4 H5 D2 D3 D4",
        SpecialFriend.FIRST_TRICK,
        1, List.of(first), List.of(play(0, "SK")));
    assertEquals(card("S8"), policy.choose(observation).card());
  }

  @ParameterizedTest
  @CsvSource({"HQ,HK", "HJ,HQ", "H9,H10"})
  void pressesMightyFriendWithRemainingMinimumHigherCard(String lead, String higher) {
    var observation = view(1, Trump.DIAMOND, higher + " H2 H3 H4 C2 C3 C4 D2 D3 S2",
        new CardFriend(card("SA")), 4, List.of(), List.of(play(0, lead)));
    var decision = policy.recommend(observation);
    assertEquals(card(higher), decision.action().card());
    assertTrue(decision.reason().contains("마이티 프렌드"));
  }

  @Test
  void pressureAlsoAppliesAfterOpeningTrick() {
    var first = trick(1, Trump.DIAMOND, play(0, "CA"), play(1, "C2"), play(2, "C3"),
        play(3, "C4"), play(4, "C5"));
    var observation = view(1, Trump.DIAMOND, "HK H2 H3 H4 C6 C7 D2 D3 S2",
        new CardFriend(card("SA")), 4, List.of(first), List.of(play(0, "HQ")));
    assertEquals(card("HK"), policy.choose(observation).card());
    assertTrue(policy.recommend(observation).reason().contains("마이티 프렌드"));
  }

  @ParameterizedTest
  @CsvSource({"DK"})
  void supportsUnusableSidePointsWithoutWaitingForFixedTrickNumber(String point) {
    var observation = view(1, Trump.HEART, point + " S2 S3 S4 S5 D2 D3 C2 C3 C4",
        new CardFriend(card("C2")), 1, List.of(), List.of(play(0, "HA")));
    assertEquals(card(point), policy.choose(observation).card());
  }

  @ParameterizedTest
  @CsvSource({"1", "2", "3", "4"})
  void joinsFirstTrickFriendWithLowestWinningRuff(int seat) {
    var current = new java.util.ArrayList<PublicPlay>();
    current.add(play(0, "CA"));
    for (int before = 1; before < seat; before++) {
      current.add(play(before, "C" + (before + 5)));
    }
    var observation = view(seat, Trump.HEART, "H2 HQ HK HA D2 D3 D4 S2 S3 S4",
        SpecialFriend.FIRST_TRICK, -1, List.of(), current);
    assertEquals(card("H2"), policy.choose(observation).card());
  }

  @ParameterizedTest
  @CsvSource({"HQ,HJ,H2,H3", "HJ,H10,HQ,H3", "H10,H9,HQ,HJ"})
  void showsRemainingTrumpOnDeclarersCountedTop(String lead, String following, String third,
      String fourth) {
    var first = trick(1, Trump.HEART, play(0, "C2"), play(1, "CA"), play(2, "C3"),
        play(3, "C4"), play(4, "C5"));
    var second = trick(2, Trump.HEART, play(1, "HK"), play(2, "HA"), play(3, third),
        play(4, fourth), play(0, "SA"));
    var observation = view(1, Trump.HEART, following + " H5 JK D2 D3 D4 S2 S3",
        SpecialFriend.FIRST_TRICK,
        1, List.of(first, second), List.of(play(0, lead)));
    assertEquals(card(following), policy.choose(observation).card());
  }

  @ParameterizedTest
  @CsvSource({"HK HQ,H", "HA HK,A"})
  void firstTrickFriendUsesJokerBeforeUncertainTrumpReturn(String tops, String expected) {
    var first = trick(1, Trump.HEART, play(0, "C2"), play(1, "CA"), play(2, "C3"),
        play(3, "C4"), play(4, "C5"));
    var observation = view(1, Trump.HEART, tops + " HJ H5 JK D2 D3 S2 S3",
        SpecialFriend.FIRST_TRICK,
        1, List.of(first), List.of());
    var action = policy.choose(observation);
    if (expected.equals("A")) {
      assertEquals(card("HA"), action.card());
    } else {
      assertEquals(new JokerLead(Suit.HEART), action);
    }
  }

  @ParameterizedTest
  @CsvSource({"true", "false"})
  void declarerPreservesTrumpWhenFriendMightyWins(boolean revealed) {
    var first = trick(1, Trump.HEART, play(0, "C2"), play(1, "C3"), play(2, "CA"),
        play(3, "C4"), play(4, "C5"));
    var observation = view(0, Trump.HEART, "HQ H2 H3 H4 C6 C7 S2 S3 S4", new CardFriend(card("SA")),
        revealed ? 4 : -1, List.of(first), List.of(play(2, "D2"), play(3, "D3"), play(4, "SA")));
    assertEquals(new Seat(4), new PlayFacts(observation).teams().friendSeat().orElseThrow());
    var action = policy.choose(observation);
    assertInstanceOf(StandardCard.class, action.card());
    assertNotEquals(Suit.HEART, ((StandardCard) action.card()).suit());
  }

  @Test
  void forcedTrumpDiscardRemainsLegalUnderFriendMighty() {
    var first = trick(1, Trump.HEART, play(0, "C2"), play(1, "C3"), play(2, "CA"),
        play(3, "C4"), play(4, "C5"));
    var observation = view(0, Trump.HEART, "HQ H2 H3 H4 H5 H6 H7 H8 H9", new CardFriend(card("SA")),
        4, List.of(first), List.of(play(2, "D2"), play(3, "D3"), play(4, "SA")));
    assertTrue(new PlayFacts(observation).legalActions().contains(policy.choose(observation)));
  }

  @ParameterizedTest
  @CsvSource({"S3 S4,S3", "SJ C6,SA"})
  void friendHidesFromOppositionMightyAttackUntilDeclarerActs(String following, String expected) {
    var first = trick(1, Trump.HEART, play(0, "C2"), play(1, "C3"), play(2, "CA"),
        play(3, "C4"), play(4, "C5"));
    var observation = view(4, Trump.HEART, "SA " + following + " H2 H3 H4 D2 D3 D4",
        new CardFriend(card("SA")), 4, List.of(first), List.of(play(2, "SK"), play(3, "S2")));
    assertEquals(card(expected), policy.choose(observation).card());
  }

  @ParameterizedTest
  @CsvSource({"S4,SA", "D5,SA", "H5,S4"})
  void friendRespondsToDeclarersActualActionOnMightyAttack(String declarerCard, String expected) {
    var first = trick(1, Trump.HEART, play(0, "C2"), play(1, "C3"), play(2, "CA"),
        play(3, "C4"), play(4, "C5"));
    String held = declarerCard.equals("S4") ? "S5 S6" : "S4 S6";
    var observation = view(1, Trump.HEART, "SA " + held + " H2 H3 H4 D2 D3 D4",
        new CardFriend(card("SA")),
        1, List.of(first),
        List.of(play(2, "SK"), play(3, "S2"), play(4, "S3"), play(0, declarerCard)));
    assertEquals(card(expected), policy.choose(observation).card());
  }

  @Test
  void declarerRequestedMightyIsNotHiddenAsOppositionAttack() {
    var first = trick(1, Trump.HEART, play(0, "CA"), play(1, "C2"), play(2, "C3"),
        play(3, "C4"), play(4, "C5"));
    var observation = view(1, Trump.HEART, "SA S3 S4 H2 H3 H4 D2 D3 D4", new CardFriend(card("SA")),
        1, List.of(first), List.of(play(0, "S2")));
    assertEquals(card("SA"), policy.choose(observation).card());
  }

  @Test
  void retainsUsableSideTopWithPaddingForLaterSupport() {
    var observation = view(1, Trump.HEART, "CA S2 S3 S4 S5 D2 D3 C2 C3 C4",
        new CardFriend(card("C2")), 1, List.of(), List.of(play(0, "HA")));
    assertNotEquals(card("CA"), policy.choose(observation).card());
  }

}
