package mighty.ai.bidding;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import mighty.ai.evaluation.PairedSettlementComparison;
import org.junit.jupiter.api.Test;

class PairedSettlementComparisonTest {

  @Test
  void uncertainFriendComparisonKeepsTheOperationalDefault() {
    var usual = List.of(-4, -4, -4, 40, 48, 48, -8, 48);
    var alternative = List.of(-16, 48, 48, -28, 40, 48, 48, -20);
    assertFalse(PairedSettlementComparison.hasClearGain(usual, alternative));
  }

  @Test
  void consistentImprovementCanReplaceTheDefault() {
    assertTrue(PairedSettlementComparison.hasClearGain(List.of(8, -4, 12), List.of(12, 0, 16)));
  }

  @Test
  void unpairedOrInsufficientSamplesCannotJustifyChangingThePlan() {
    assertFalse(PairedSettlementComparison.hasClearGain(List.of(0), List.of(100)));
    assertFalse(PairedSettlementComparison.hasClearGain(List.of(0, 0), List.of(100)));
  }
}
