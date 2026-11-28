package mighty.ai.evaluation;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Collections;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class PreparedComparisonTest {
  @Test
  void expandsVariablePairsEvenWhenMeanDifferenceExceedsFour() {
    var differences = IntStream.range(0, 32).map(i -> i % 2 == 0 ? 38 : -28).boxed().toList();
    assertTrue(PreparedRoundEvaluator.needsConfirmation(differences));
  }

  @Test
  void stableLargeAdvantageDoesNotNeedExpansion() {
    assertFalse(PreparedRoundEvaluator.needsConfirmation(Collections.nCopies(32, 6)));
  }

  @Test
  void closeAndUndersampledComparisonsRemainConservative() {
    assertTrue(PreparedRoundEvaluator.needsConfirmation(Collections.nCopies(32, 4)));
    assertTrue(PreparedRoundEvaluator.needsConfirmation(Collections.singletonList(12)));
  }
}
