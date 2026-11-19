package mighty.ai.planning;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import mighty.ai.bidding.*;
import mighty.ai.preparation.*;
import mighty.data.CardParser;
import mighty.game.Seat;
import mighty.game.bidding.Bid;
import mighty.game.friend.CardFriend;
import mighty.game.observation.FriendView;
import mighty.model.*;
import org.junit.jupiter.api.Test;

class NoTrumpPlannerTest {

  private static NoTrumpPlan plan(String cards) {
    return NoTrumpPlanner.analyze(BiddingHand.from(CardParser.parseList(cards)));
  }

  @Test
  void eightHeartsAndOneSideRequestCanPlayFullNoTrump() {
    var p = plan("H2 H3 H4 H6 H7 HJ HK HA D6 SA");
    assertTrue(p.available());
    assertEquals(Joker.INSTANCE, p.friend());
    assertFalse(p.canDelaySupport());
  }

  @Test
  void connectedTwoSuitHandReservesMightyForEnemyJoker() {
    var p = plan("SJ SQ SK SA D8 DJ DQ DK CK CA");
    assertTrue(p.available());
    assertEquals(1, p.jokerBreaks());
    assertEquals(CardParser.parse("DA"), p.friend());
    assertFalse(p.canDelaySupport());
    var view = new FriendView(new Seat(0), new Bid(17, Trump.NO_TRUMP),
        CardParser.parseList("SJ SQ SK SA D8 DJ DQ DK CK CA"), CardParser.parseList("H2 H3 H4"));
    assertEquals(new CardFriend(CardParser.parse("DA")), new RuleFriendPolicy().choose(view));
  }

  @Test
  void allOrdinaryTopsNeedControlOverJokerTiming() {
    assertFalse(plan("SK SQ SJ DA DK CA CK HA HK H3").available());
    assertTrue(plan("SK SQ SJ DA DK CA CK HA HK C3").available());
  }

  @Test
  void delayedCallUsesOnlyOwnEightWins() {
    var p = plan("D4 D6 D7 DJ DQ DK DA S4 SA C3");
    assertTrue(p.canDelaySupport());
    assertFalse(plan("D3 D6 D7 D8 D9 DJ DQ C6 CA JK").canDelaySupport());
  }

  @Test
  void suitNamesAndLowNumbersDoNotDefineThePlan() {
    for (String suit : List.of("D", "H", "C")) {
      String cards = String.join(" ", List.of("2", "3", "4", "6", "7", "J", "K", "A").stream()
          .map(rank -> suit + rank).toList()) + " S2 SA";
      assertEquals(20,
          new BiddingHandEvaluator().assess(BiddingHand.from(CardParser.parseList(cards))).stream()
              .filter(a -> a.trump() == Trump.NO_TRUMP).findFirst().orElseThrow().ceilingPoints(),
          cards);
    }
  }

  @Test
  void excludedFriendCardCannotBeRequested() {
    var hand = BiddingHand.from(CardParser.parseList("SJ SQ SK SA D8 DJ DQ DK CK CA"));
    var p = NoTrumpPlanner.analyze(hand, CardParser.parseList("DA H2 H3"));
    assertNotEquals(CardParser.parse("DA"), p.friend());
  }

  @Test
  void gunAndTwoConnectedSuitsAreNotRejectedBySingleLossGate() {
    var hand = BiddingHand.from(CardParser.parseList("S4 SJ SQ SK C3 C4 C8 C9 CK CA"));
    var assessment = new BiddingHandEvaluator().assess(hand).stream()
        .filter(a -> a.trump() == Trump.NO_TRUMP).findFirst().orElseThrow();
    assertTrue(assessment.canCompete());
    assertEquals(17, assessment.ceilingPoints());
    var distribution = mighty.ai.planning.distribution.NoTrumpDistributionEvaluator.evaluate(hand,
        List.of(),
        CardParser.parse("SA"));
    assertEquals(256, distribution.samples());
    assertTrue(distribution.seventeenRate() > distribution.twentyRate());
    assertTrue(distribution.seventeenRate() > 0.85);
  }

  @Test
  void evaluatesSixteenNoTrumpWithSplitSupport() {
    var cards = CardParser.parseList("SA C4 C5 C8 CA D3 DQ DA HJ JK");
    var hand = BiddingHand.from(cards);
    var support = mighty.ai.planning.distribution.NoTrumpDistributionEvaluator.bestSupport(hand,
        List.of());
    assertEquals(CardParser.parse("DK"), support.friend());
    assertEquals(16,
        mighty.ai.planning.distribution.NoTrumpDistributionEvaluator.qualifyingContract(hand,
            support));
    var bid = new mighty.ai.bidding.BiddingHandEvaluator().assess(hand).stream()
        .filter(candidate -> candidate.trump() == Trump.NO_TRUMP).findFirst().orElseThrow();
    assertEquals(16, bid.openingPoints());
    assertEquals(16, bid.ceilingPoints());
    assertEquals(new CardFriend(CardParser.parse("DK")), new RuleFriendPolicy().choose(
        new FriendView(new Seat(0), new Bid(16, Trump.NO_TRUMP), cards,
            CardParser.parseList("S2 S3 H2"))));
  }

  @Test
  void comparesOpeningFriendWhenLongPrimaryHasMissingKing() {
    var hand = BiddingHand.from(CardParser.parseList("SA JK HA HQ HJ H9 H8 H7 CA CK"));
    var candidates = NoTrumpPlanner.supportCandidates(hand, List.of());
    assertTrue(candidates.contains(CardParser.parse("HK")));
    assertTrue(candidates.contains(CardParser.parse("SA")));
    var support = mighty.ai.planning.distribution.NoTrumpDistributionEvaluator.bestSupport(hand,
        List.of());
    assertEquals(20,
        mighty.ai.planning.distribution.NoTrumpDistributionEvaluator.qualifyingContract(hand,
            support));
    assertEquals(CardParser.parse("SA"), support.friend());
  }

  @Test
  void choosesSuitFriendBasedOnSideConnectionWithGun() {
    var policy = new RuleFriendPolicy();
    var buried = CardParser.parseList("S3 S4 C2");
    var contract = new Bid(16, Trump.HEART);
    assertEquals(new CardFriend(CardParser.parse("DA")),
        policy.choose(new FriendView(new Seat(0), contract,
            CardParser.parseList("SA C3 H2 H3 H4 H5 DK DQ D2 S2"), buried)));
    assertEquals(mighty.game.friend.SpecialFriend.FIRST_TRICK,
        policy.choose(new FriendView(new Seat(0), contract,
            CardParser.parseList("SA C3 H2 H3 H4 H5 DQ DJ D10 D2"), buried)));
    assertEquals(new CardFriend(CardParser.parse("JK")),
        policy.choose(new FriendView(new Seat(0), contract,
            CardParser.parseList("SA C3 H2 H3 H4 H5 DQ DJ D2 S2"), buried)));
  }

}
