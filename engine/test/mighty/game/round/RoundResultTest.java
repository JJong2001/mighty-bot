package mighty.game.round;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.Optional;
import mighty.game.Seat;
import mighty.game.bidding.Bid;
import mighty.model.Trump;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RoundResultTest {

  private static Map<Seat, Integer> captured() {
    return Map.of(new Seat(0), 5, new Seat(1), 5, new Seat(2), 3,
        new Seat(3), 3, new Seat(4), 1);
  }

  @Test
  @DisplayName("버린 카드 점수를 여당에 포함하고 공약과 같은 점수도 성공이다")
  void countsDiscardsAndContractBoundary() {
    RoundResult result = new RoundResult(new Seat(0), new Bid(13, Trump.NO_TRUMP),
        Optional.of(new Seat(1)), captured(), 3);
    assertEquals(13, result.teamPoints());
    assertEquals(7, result.oppositionPoints());
    assertTrue(result.succeeded());
    assertFalse(new RoundResult(new Seat(0), new Bid(14, Trump.SPADE),
        Optional.of(new Seat(1)), captured(), 3).succeeded());
  }

  @Test
  @DisplayName("실제 프렌드가 없으면 주공과 버린 카드의 점수만 여당에 합산한다")
  void scoresActualSolo() {
    RoundResult result = new RoundResult(new Seat(0), new Bid(13, Trump.CLUB),
        Optional.empty(), captured(), 3);
    assertEquals(8, result.teamPoints());
    assertEquals(12, result.oppositionPoints());
    assertThrows(UnsupportedOperationException.class, () -> result.captured().clear());
  }

  @Test
  @DisplayName("20점 보존과 실제 프렌드 자리의 불변식을 검사한다")
  void rejectsInvalidResults() {
    assertThrows(IllegalArgumentException.class, () -> new RoundResult(new Seat(0),
        new Bid(13, Trump.CLUB), Optional.empty(), captured(), 2));
    assertThrows(IllegalArgumentException.class, () -> new RoundResult(new Seat(0),
        new Bid(13, Trump.CLUB), Optional.of(new Seat(0)), captured(), 3));
  }

  @Test
  void settlesNoTrumpSoloAndLimitsOtherSeatsPayments() {
    var declarer = new Seat(0);
    var result = new RoundResult(declarer, new Bid(18, Trump.NO_TRUMP), Optional.empty(),
        Map.of(declarer, 18, new Seat(1), 2, new Seat(2), 0, new Seat(3), 0, new Seat(4), 0), 0);
    assertEquals(160, RoundSettlement.nominal(result, true).get(declarer));
    var collection = RoundSettlement.collect(result, true, Map.of(new Seat(1), 5));
    assertEquals(125, collection.collected());
    assertEquals(35, collection.shortage());
    assertEquals(-5, collection.losses().get(new Seat(1)));
    assertEquals(160, collection.claims().get(declarer));
  }

}
