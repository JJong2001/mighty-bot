package mighty.ai.analysis;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import mighty.ai.bidding.BiddingHand;
import mighty.ai.planning.NoTrumpPlanner;
import mighty.data.CardParser;
import mighty.model.*;
import org.junit.jupiter.api.Test;

class HoldingControlTest {

  @Test
  void countsBlockersAcrossDifferentHeights() {
    for (String[] sample : List.of(new String[]{
        "CK CQ C2", "1"
    }, new String[]{
        "CQ CJ C2", "2"
    }, new String[]{
        "CJ C10 C2", "3"
    })) {
      var plan = HoldingControl.analyze(CardParser.parseList(sample[0]), List.of(), Trump.HEART,
          Suit.CLUB);
      assertEquals(Integer.parseInt(sample[1]), plan.blockers());
      assertEquals(2, plan.connected());
    }
  }

  @Test
  void removedHigherCardsPromoteTheRun() {
    var plan = HoldingControl.analyze(CardParser.parseList("CQ CJ C2"),
        CardParser.parseList("CA CK"), Trump.HEART,
        Suit.CLUB);
    assertEquals(0, plan.blockers());
    assertEquals(2, plan.topRun());
  }

  @Test
  void longPrimaryNumbersAreNotSideWater() {
    var hand = BiddingHand.from(CardParser.parseList("SA SK SQ SJ S6 S3 D2 D3 C2 JK"));
    var plan = NoTrumpPlanner.analyze(hand);
    assertTrue(plan.available());
    assertEquals(17, new mighty.ai.bidding.BiddingHandEvaluator().assess(hand).stream()
        .filter(a -> a.trump() == Trump.NO_TRUMP).findFirst().orElseThrow().ceilingPoints());
  }

  @Test
  void dominantNineCardRunDoesNotNeedIndependentSideAce() {
    var hand = BiddingHand.from(CardParser.parseList("SA SQ SJ S10 S9 S8 S7 S6 S5 JK"));
    var plan = NoTrumpPlanner.analyze(hand);
    assertTrue(plan.available());
    assertEquals(20, new mighty.ai.bidding.BiddingHandEvaluator().assess(hand).stream()
        .filter(a -> a.trump() == Trump.NO_TRUMP).findFirst().orElseThrow().ceilingPoints());
  }

  @Test
  void interruptedMainRunRequiresSideRecovery() {
    var hand = BiddingHand.from(CardParser.parseList("D3 D6 D7 D8 DQ DJ S2 H2 C2 JK"));
    var plan = NoTrumpPlanner.analyze(hand);
    assertFalse(plan.available());
  }

}
