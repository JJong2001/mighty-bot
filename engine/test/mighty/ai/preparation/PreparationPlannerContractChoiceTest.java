package mighty.ai.preparation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import mighty.ai.bidding.BiddingHand;
import mighty.ai.bidding.BiddingHandEvaluator;
import mighty.data.CardParser;
import mighty.game.Seat;
import mighty.game.bidding.Bid;
import mighty.game.observation.DiscardView;
import mighty.model.Trump;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class PreparationPlannerContractChoiceTest {

  @Test
  @DisplayName("바닥 뒤 노기루가 경쟁 불가면 가능한 기루 공약으로 전환한다")
  void choosesSuitedContractWhenNoTrumpCannotCompeteAfterKitty() {
    var hand = new ArrayList<>(CardParser.parseList("SA SJ S8 S3 DJ D5 D4 D2 H10 C4"));
    hand.addAll(CardParser.parseList("D3 H2 C2"));
    var awarded = new Bid(15, Trump.NO_TRUMP);

    var plan = new PreparationPlanner().choose(
        new DiscardView(new Seat(0), awarded, hand), awarded);
    var retained = new ArrayList<>(hand);
    retained.removeAll(plan.discarded());
    var assessments = new BiddingHandEvaluator().assess(BiddingHand.from(retained));
    var noTrump = assessments.stream().filter(value -> value.trump() == Trump.NO_TRUMP)
        .findFirst().orElseThrow();
    var suited = assessments.stream().filter(value -> value.trump() == plan.contract().trump())
        .findFirst().orElseThrow();

    assertFalse(noTrump.canCompete());
    assertTrue(suited.canCompete());
    assertEquals(Trump.DIAMOND, plan.contract().trump());
  }
}
