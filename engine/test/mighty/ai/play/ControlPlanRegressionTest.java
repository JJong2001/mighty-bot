package mighty.ai.play;

import static mighty.ai.play.PlayTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import mighty.ai.bidding.*;
import mighty.ai.planning.*;
import mighty.ai.planning.distribution.NoTrumpDistributionEvaluator;
import mighty.data.CardParser;
import mighty.game.*;
import mighty.game.bidding.Bid;
import mighty.game.friend.*;
import mighty.game.observation.*;
import mighty.game.rule.Rules;
import mighty.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 문양이나 공약 숫자 하나에 한정하지 않고 계획과 실행의 일치를 검증합니다.
 */
class ControlPlanRegressionTest {

  @ParameterizedTest
  @CsvSource({
      "C5 C9 C10 CQ CK CA JK S2 D2 H2, S3 D3 H3, CA",
      "D5 D9 D10 DQ DK DA JK S2 C2 H2, S3 C3 H3, DA",
      "H5 H9 H10 HQ HK HA JK S2 D2 C2, S3 D3 C3, HA"
  })
  void preservesMightyForSideWhenOrdinaryRunAndJokerCanDevelopPrimary(String owned,
      String removed, String expected) {
    var own = CardParser.parseList(owned);
    var buried = CardParser.parseList(removed);
    var friend = new CardFriend(card("SA"));
    for (int points : List.of(15, 16, 17, 20)) {
      var bid = new Bid(points, Trump.NO_TRUMP);
      var plan = NoTrumpOpeningPlanner.plan(BiddingHand.from(own), buried, friend, points,
          List.of(bid));
      assertEquals(NoTrumpOpeningPlan.Purpose.RUN_PRIMARY, plan.purpose());
      assertEquals(card(expected), plan.opening().orElseThrow());
      var observation = view(0, 0, Trump.NO_TRUMP, points, owned, removed, friend,
          UnrevealedFriend.INSTANCE, List.of(), List.of());
      assertEquals(plan.opening().orElseThrow(), new RulePlayPolicy().choose(observation).card());
    }
  }

  @ParameterizedTest
  @CsvSource({
      "H3 H4 H5 H6 H8 HJ HQ HK HA C3, S2 S3 S4",
      "D3 D4 D5 D6 D8 DJ DQ DK DA C3, S2 S3 S4"
  })
  void gunAloneDoesNotSupplyReturnAfterFriendWins(String owned, String removed) {
    var hand = BiddingHand.from(CardParser.parseList(owned));
    var buried = CardParser.parseList(removed);
    assertFalse(NoTrumpPlanner.analyze(hand, buried).available());
    var noTrump = new BiddingHandEvaluator().assess(hand, buried).stream()
        .filter(a -> a.trump() == Trump.NO_TRUMP).findFirst().orElseThrow();
    assertFalse(noTrump.canCompete());
    assertEquals(0,
        NoTrumpDistributionEvaluator.bestSupport(hand, buried).distribution().twentyWins());
  }

  @Test
  void independentSideAceCreatesARealReturnRoute() {
    var own = CardParser.parseList("H4 H5 H6 H8 HJ HQ HK HA C3 DA");
    var buried = CardParser.parseList("S2 S3 S4");
    var control = NoTrumpControlPlan.analyze(own, buried);
    assertEquals(Set.of(Suit.DIAMOND), control.returnEntries());
    assertTrue(NoTrumpPlanner.analyze(BiddingHand.from(own), buried).available());
    var plan = NoTrumpOpeningPlanner.plan(BiddingHand.from(own), buried,
        new CardFriend(card("SA")), 20, List.of(new Bid(20, Trump.NO_TRUMP)));
    assertEquals(card("HA"), plan.opening().orElseThrow());
    assertEquals(Optional.of(Suit.DIAMOND), plan.returnSuit());
  }

  @ParameterizedTest
  @CsvSource({"0", "4"})
  void ordinaryRuffThenJokerSweepWorksForBothTeams(int declarer) {
    var history = List.of(row(1, Trump.HEART, p(0, "SA"), p(1, "H10"), p(2, "HJ"),
        p(3, "HQ"), p(4, "HA")));
    var trick = List.of(p(0, "D3"), p(1, "D4"), p(2, "D5"), p(3, "D6"));
    var friend = new CardFriend(card("CA"));
    String buried = declarer == 4 ? "C4 C5 C6" : "";
    var before = view(4, declarer, Trump.HEART, 16, "H2 H3 H4 H5 H6 H7 H8 H9 JK", buried,
        friend, UnrevealedFriend.INSTANCE, history, trick);
    var facts = new PlayFacts(before);
    assertEquals(List.of(card("HK")), new ArrayList<>(facts.cards().trumps().outside()));
    var policy = new RulePlayPolicy();
    assertEquals(card("H2"), policy.choose(before).card());
    var completed = new ArrayList<>(history);
    completed.add(row(2, Trump.HEART, p(0, "D3"), p(1, "D4"), p(2, "D5"), p(3, "D6"), p(4, "H2")));
    var next = view(4, declarer, Trump.HEART, 16, "H3 H4 H5 H6 H7 H8 H9 JK", buried,
        friend, UnrevealedFriend.INSTANCE, completed, List.of());
    var action = policy.choose(next);
    assertEquals(Joker.INSTANCE, action.card());
    assertEquals(Suit.HEART, ((mighty.game.action.JokerLead) action).declaredSuit());
  }

  @Test
  void singletonMightyIsSpentInsteadOfProtectingItWithJoker() {
    var observation = view(1, 0, Trump.HEART, 16,
        "SA JK H2 H4 H5 H6 H7 H8 H9 H10", "",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE,
        List.of(), List.of(p(0, "S2")));
    assertEquals(card("SA"), new RulePlayPolicy().choose(observation).card());
  }
}
