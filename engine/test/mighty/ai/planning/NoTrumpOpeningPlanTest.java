package mighty.ai.planning;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import mighty.ai.bidding.BiddingHand;
import mighty.ai.planning.distribution.NoTrumpDistributionEvaluator;
import mighty.ai.play.RulePlayPolicy;
import mighty.ai.preparation.RuleFriendPolicy;
import mighty.data.CardParser;
import mighty.game.*;
import mighty.game.bidding.Bid;
import mighty.game.friend.*;
import mighty.game.observation.*;
import mighty.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

/**
 * 카드 이름 하나가 아니라 지정·공개 문양·초구가 연결되는 경로를 검사합니다.
 */
class NoTrumpOpeningPlanTest {

  static Stream<Arguments> recoveryHands() {
    return Stream.of(
        Arguments.of("D3 D4 D5 D6 D9 DQ DK CA JK H3", "S4 S8 C10", Trump.DIAMOND, "D3", "CA"),
        Arguments.of("H2 H4 H5 H7 H8 HQ HK DA JK C2", "S3 S9 D10", Trump.HEART, "H2", "DA"),
        Arguments.of("C2 C4 C5 C6 C9 CQ CK HA JK D3", "S3 S9 H10", Trump.CLUB, "C2", "HA"),
        Arguments.of("D2 D3 D4 D5 D6 DJ DQ CA JK H3", "S4 S8 C10", Trump.DIAMOND, "D2", "CA"));
  }

  @ParameterizedTest
  @MethodSource("recoveryHands")
  void distinguishesRecoveryEntryFromOpeningAnExtraPartnerWin(String owned, String removed,
      Trump main, String request, String entry) {
    var hand = CardParser.parseList(owned);
    var buried = CardParser.parseList(removed);
    var friend = new CardFriend(CardParser.parse("SA"));
    for (int points : List.of(17, 20)) {
      var contract = new Bid(points, Trump.NO_TRUMP);
      var bids = List.of(new Bid(16, main), contract);
      var plan = NoTrumpOpeningPlanner.plan(BiddingHand.from(hand), buried, friend, points, bids);
      assertEquals(CardParser.parse(points == 17 ? request : entry), plan.opening().orElseThrow());
      assertEquals(points == 17 ? NoTrumpOpeningPlan.Purpose.REQUEST_FRIEND
          : NoTrumpOpeningPlan.Purpose.PROMOTE_PARTNER, plan.purpose());
      assertEquals(plan.opening().orElseThrow(), new RulePlayPolicy().choose(
          view(hand, buried, contract, bids, friend)).card());
    }
  }

  @Test
  void ownMightyAndJokerFriendNeedAnOrdinaryFirstWin() {
    var hand = CardParser.parseList("D3 D4 D5 D6 D9 DQ DK CA SA H3");
    var buried = CardParser.parseList("S4 S8 C10");
    var contract = new Bid(17, Trump.NO_TRUMP);
    var friend = new CardFriend(Joker.INSTANCE);
    var plan = NoTrumpOpeningPlanner.plan(BiddingHand.from(hand), buried, friend, 17,
        List.of(contract));
    assertEquals(CardParser.parse("CA"), plan.opening().orElseThrow());
    assertEquals(NoTrumpOpeningPlan.Purpose.SECURE_FIRST_TRICK, plan.purpose());
    assertEquals(CardParser.parse("CA"), new RulePlayPolicy().choose(
        view(hand, buried, contract, List.of(contract), friend)).card());
  }

  static Stream<Arguments> connectionHands() {
    return Stream.of(
        Arguments.of("S7 SA D2 DK C3 C4 CQ CK CA JK", "H3 H4 HQ", Trump.CLUB, "DA", "D2"),
        Arguments.of("S9 SA D6 DK C5 C6 CQ CK CA JK", "H3 H4 HQ", Trump.CLUB, "DA", "D6"),
        Arguments.of("S7 SA H2 HK C3 C4 CQ CK CA JK", "D3 D4 DQ", Trump.CLUB, "HA", "H2"),
        Arguments.of("S7 SA H2 HK D3 D4 DQ DK DA JK", "C3 C4 CQ", Trump.DIAMOND, "HA", "H2"),
        Arguments.of("S6 SA DK H3 HQ HA C5 C6 C10 JK", "D2 H2 H4", Trump.CLUB, "HK", "H3"));
  }

  @ParameterizedTest
  @MethodSource("connectionHands")
  void evaluationSelectionAndOpeningUseOneConnectionPlan(String owned, String buried,
      Trump shown, String friend, String lead) {
    var hand = CardParser.parseList(owned);
    var discards = CardParser.parseList(buried);
    var contract = new Bid(20, Trump.NO_TRUMP);
    var history = List.of(new Bid(20, shown), contract);
    var selected = new RuleFriendPolicy().choose(new FriendView(new Seat(0), contract, hand,
        discards, history));
    assertEquals(new CardFriend(CardParser.parse(friend)), selected);
    var evaluated = NoTrumpDistributionEvaluator.bestSupport(BiddingHand.from(hand), discards,
        20, history).plan();
    assertTrue(evaluated.connectionBeforeRun());
    assertTrue(evaluated.finalHand());
    assertEquals(CardParser.parse(lead), evaluated.opening().orElseThrow());
    assertEquals(evaluated.selection(), selected);
    assertEquals(evaluated.opening().orElseThrow(), new RulePlayPolicy().choose(
        view(hand, discards, contract, history, selected)).card());
  }

  @Test
  void openingFriendUsesTheSamePublishedReturnInEvaluationAndExecution() {
    var hand = CardParser.parseList("S7 SA D2 DK C3 C4 CQ CK CA JK");
    var buried = CardParser.parseList("H3 H4 HQ");
    var contract = new Bid(20, Trump.NO_TRUMP);
    for (var history : List.of(List.of(contract), List.of(new Bid(20, Trump.CLUB), contract))) {
      var plan = NoTrumpOpeningPlanner.plan(BiddingHand.from(hand), buried,
          SpecialFriend.FIRST_TRICK,
          20, history);
      assertEquals(CardParser.parse("C3"), plan.opening().orElseThrow());
      assertEquals(plan.opening().orElseThrow(), new RulePlayPolicy().choose(
          view(hand, buried, contract, history, SpecialFriend.FIRST_TRICK)).card());
    }
  }

  private static View view(List<Card> hand, List<Card> buried, Bid contract, List<Bid> bids,
      FriendSelection friend) {
    var seats = IntStream.range(0, 5).mapToObj(Seat::new).toList();
    var order = PlayOrder.from(new TableLayout(GameMode.FIVE_PLAYER, seats), new HashSet<>(seats));
    return ObservationFactory.create(new Seat(0), new DeclarerInfo(hand, buried), contract, friend,
        new Context(order, new Seat(0), UnrevealedFriend.INSTANCE, List.of(), bids), List.of());
  }
}
