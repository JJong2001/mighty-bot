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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PreparationPlannerContractChoiceTest {

  @ParameterizedTest
  @CsvSource({
      "S6 SA DK H3 HQ HA C5 C6 C10 JK, 15, NO_TRUMP",
      "SK S3 HJ H10 H9 CA CK CJ C2 JK, 15, NO_TRUMP",
      "SA SK S5 S3 D10 HA HK H3 H2 CK, 15, NO_TRUMP",
      "SA SJ S8 S3 DJ D5 D4 D2 H10 C4, 16, DIAMOND",
      "S2 S3 S7 D4 D8 H5 H9 HJ C6 C8, 15, NO_TRUMP"
  })
  @DisplayName("닷노 낙찰 후 바닥 전에는 노기루 성공 길과 확정된 주력 문양을 차례로 본다")
  void choosesBeforeKittyFromNoTrumpRouteAndSingleMainSuit(String hand, int points, Trump trump) {
    var award = new Bid(15, Trump.NO_TRUMP);
    assertEquals(new Bid(points, trump),
        new PreparationPlanner().beforeKittyContract(CardParser.parseList(hand), award, award));
  }

  @ParameterizedTest
  @CsvSource({
      "D2 H2 S5, 15, NO_TRUMP",
      "S2 S3 S4, 17, SPADE"
  })
  @DisplayName("같은 닷노 낙찰 패도 실제 바닥에 따라 닷노 유지와 곱기루 전환이 달라진다")
  void reevaluatesSameNoTrumpHandWithActualKitty(String kitty, int points, Trump trump) {
    var award = new Bid(15, Trump.NO_TRUMP);
    var hand = CardParser.parseList("S6 SA DK H3 HQ HA C5 C6 C10 JK " + kitty);
    var plan = new PreparationPlanner().choose(new DiscardView(new Seat(0), award, hand), award);
    assertEquals(new Bid(points, trump), plan.contract());
  }

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
