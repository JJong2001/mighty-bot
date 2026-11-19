package mighty.ai.bidding;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import mighty.model.Rank;
import mighty.model.Suit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SuitHoldingTest {

  @Test
  @DisplayName("연속 구간은 지정한 숫자부터 첫 빈 숫자 직전까지 센다")
  void countsRequestedRun() {
    SuitHolding holding = new SuitHolding(Suit.SPADE,
        List.of(Rank.EIGHT, Rank.ACE, Rank.NINE, Rank.QUEEN, Rank.KING));
    assertEquals(3, holding.consecutiveFrom(Rank.ACE));
    assertEquals(2, holding.consecutiveFrom(Rank.KING));
    assertEquals(0, holding.consecutiveFrom(Rank.JACK));
    assertEquals(2, holding.consecutiveFrom(Rank.NINE));
  }

  @Test
  @DisplayName("빈 문양과 최저 숫자 및 전체 연속 구간을 처리한다")
  void handlesRunBoundaries() {
    SuitHolding empty = new SuitHolding(Suit.CLUB, List.of());
    assertEquals(0, empty.consecutiveFrom(Rank.ACE));
    SuitHolding full = new SuitHolding(Suit.HEART, List.of(Rank.values()));
    assertEquals(13, full.consecutiveFrom(Rank.ACE));
    assertEquals(1, full.consecutiveFrom(Rank.TWO));
    assertThrows(NullPointerException.class, () -> full.consecutiveFrom(null));
  }

}
