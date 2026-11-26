package mighty.ai.bidding;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import mighty.game.bidding.*;
import mighty.model.Trump;
import org.junit.jupiter.api.Test;

class ContestedBidRaiseTest {

  private static ContestedSuitAuction.Estimate estimate(List<Integer> samples) {
    var estimate = new ContestedSuitAuction.Estimate();
    estimate.sampleSettlements.addAll(samples);
    estimate.bidderIsOpponent().settlement = samples.stream().mapToInt(Integer::intValue).sum();
    return estimate;
  }

  private static BidOffer bid(int points, Trump trump) {
    return new BidOffer(new Bid(points, trump));
  }

  private static Map<BiddingAction, ContestedSuitAuction.Estimate> choices() {
    var choices = new LinkedHashMap<BiddingAction, ContestedSuitAuction.Estimate>();
    choices.put(BidPass.INSTANCE, estimate(List.of(0, 0, 0, 0)));
    return choices;
  }

  @Test
  void smallUncertainGainDoesNotRaiseSameStrain() {
    var low = bid(17, Trump.NO_TRUMP);
    var high = bid(18, Trump.NO_TRUMP);
    var values = choices();
    values.put(low, estimate(List.of(20, 20, 20, 20)));
    values.put(high, estimate(List.of(0, 42, 0, 42)));
    assertEquals(low, ContestedSuitAuction.bestChoice(values));
  }

  @Test
  void consistentAdditionalRewardCanJustifyRaising() {
    var low = bid(17, Trump.NO_TRUMP);
    var high = bid(18, Trump.NO_TRUMP);
    var values = choices();
    values.put(low, estimate(List.of(20, 20, 20, 20)));
    values.put(high, estimate(List.of(28, 28, 28, 28)));
    assertEquals(high, ContestedSuitAuction.bestChoice(values));
  }

  @Test
  void requiredLevelIsNotBlockedWhenNoLowerLevelIsLegal() {
    var high = bid(18, Trump.NO_TRUMP);
    var values = choices();
    values.put(high, estimate(List.of(0, 42, 0, 42)));
    assertEquals(high, ContestedSuitAuction.bestChoice(values));
  }

  @Test
  void differentStrainsStillUseTheExistingExpectedScoreComparison() {
    var suited = bid(17, Trump.HEART);
    var noTrump = bid(18, Trump.NO_TRUMP);
    var values = choices();
    values.put(suited, estimate(List.of(20, 20, 20, 20)));
    values.put(noTrump, estimate(List.of(0, 42, 0, 42)));
    assertEquals(noTrump, ContestedSuitAuction.bestChoice(values));
  }
}
