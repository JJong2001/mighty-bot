package mighty.game.bidding;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import mighty.game.Seat;
import mighty.game.deal.DealFormat;
import mighty.game.deal.DealParticipants;
import mighty.model.Trump;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BiddingStateTest {

  private static Seat seat(int index) {
    return new Seat(index);
  }

  private static BiddingState start(int count, int opening) {
    List<Seat> seats = IntStream.range(0, count)
        .mapToObj(BiddingStateTest::seat).toList();
    DealFormat format = count == 6
        ? DealFormat.SIX_PLAYER_OPENING : DealFormat.FIVE_PLAYER_OPENING;
    return BiddingState.start(new DealParticipants(format, seats), seat(opening));
  }

  private static BiddingState offer(BiddingState state, int points, Trump trump) {
    return state.submit(new PublicBid(
        state.currentSeat().orElseThrow(), new BidOffer(new Bid(points, trump))
    ));
  }

  private static BiddingState pass(BiddingState state) {
    return state.submit(new PublicBid(
        state.currentSeat().orElseThrow(), BidPass.INSTANCE
    ));
  }

  @Test
  @DisplayName("시작 자리부터 비딩하며 아직 최고 공약은 없다")
  void startsAtOpeningSeat() {
    BiddingState state = start(5, 3);
    assertEquals(seat(3), state.currentSeat().orElseThrow());
    assertTrue(state.highestBid().isEmpty());
    assertTrue(state.history().isEmpty());
    assertFalse(state.isFinished());
  }

  @Test
  @DisplayName("참가하지 않은 자리는 비딩을 시작할 수 없다")
  void rejectsNonparticipantOpening() {
    assertThrows(IllegalArgumentException.class, () -> start(5, 5));
  }

  @Test
  @DisplayName("첫 비딩자의 패스는 거절된다")
  void rejectsOpeningPass() {
    assertThrows(IllegalArgumentException.class, () -> pass(start(5, 0)));
  }

  @Test
  @DisplayName("차례가 아닌 자리의 행동은 거절된다")
  void rejectsWrongTurn() {
    assertThrows(IllegalArgumentException.class, () -> start(5, 0).submit(
        new PublicBid(seat(1), new BidOffer(new Bid(13, Trump.SPADE)))
    ));
  }

  @Test
  @DisplayName("패스한 자리를 건너뛰고 처음 자리로 순환한다")
  void skipsPassedSeatOnNextRound() {
    BiddingState state = offer(start(5, 0), 13, Trump.SPADE);
    state = pass(state);
    // 1번은 이후 비딩에서 제외됩니다.
    state = offer(state, 14, Trump.HEART);
    state = pass(state);
    state = pass(state);
    assertEquals(seat(0), state.currentSeat().orElseThrow());
    state = offer(state, 15, Trump.CLUB);
    assertEquals(seat(2), state.currentSeat().orElseThrow());
    assertEquals(Set.of(seat(1), seat(3), seat(4)), state.passedSeats());
    BiddingState active = state;
    assertThrows(IllegalArgumentException.class, () -> active.submit(
        new PublicBid(seat(1), new BidOffer(new Bid(20, Trump.NO_TRUMP)))
    ));
  }

  @Test
  @DisplayName("현재 최고 입찰자를 제외한 모두가 패스하면 종료된다")
  void finishesWithLastBidder() {
    BiddingState state = offer(start(5, 0), 13, Trump.SPADE);
    state = offer(state, 14, Trump.HEART);
    for (int index = 0; index < 4; index++) {
      state = pass(state);
    }
    assertTrue(state.isFinished());
    assertTrue(state.currentSeat().isEmpty());
    assertEquals(new PublicBid(seat(1), new BidOffer(new Bid(14, Trump.HEART))),
        state.highestBid().orElseThrow());
    BiddingState finished = state;
    assertThrows(IllegalStateException.class, () -> finished.submit(
        new PublicBid(seat(1), BidPass.INSTANCE)
    ));
  }

  @Test
  @DisplayName("육마 첫 비딩에는 여섯 명이 모두 참여한다")
  void sixPlayerAuctionIncludesSixSeats() {
    BiddingState state = offer(start(6, 5), 13, Trump.SPADE);
    for (int index = 0; index < 5; index++) {
      assertEquals(seat(index), state.currentSeat().orElseThrow());
      assertFalse(state.isFinished());
      state = pass(state);
    }
    assertTrue(state.isFinished());
    assertEquals(seat(5), state.highestBid().orElseThrow().seat());
  }

  @Test
  @DisplayName("같은 점수의 기루 변경과 낮은 공약은 거절된다")
  void rejectsInvalidOffers() {
    BiddingState state = offer(start(5, 0), 15, Trump.SPADE);
    assertThrows(IllegalArgumentException.class, () -> offer(state, 15, Trump.HEART));
    assertThrows(IllegalArgumentException.class, () -> offer(state, 14, Trump.NO_TRUMP));
    assertEquals(1, state.history().size());
  }

  @Test
  @DisplayName("새 상태 생성 후 이전 상태와 공개 기록은 변경되지 않는다")
  void preservesSnapshots() {
    BiddingState initial = start(5, 0);
    BiddingState offered = offer(initial, 13, Trump.CLUB);
    BiddingState passed = pass(offered);
    assertTrue(initial.history().isEmpty());
    assertEquals(1, offered.history().size());
    assertEquals(2, passed.history().size());
    assertThrows(UnsupportedOperationException.class, () -> passed.history().clear());
    assertThrows(UnsupportedOperationException.class, () -> passed.passedSeats().clear());
  }

  @Test
  @DisplayName("닷노에서 엿노로 올리는 공약은 허용된다")
  void acceptsHigherNoTrump() {
    BiddingState state = offer(start(5, 0), 15, Trump.NO_TRUMP);
    state = offer(state, 16, Trump.NO_TRUMP);
    assertEquals(new BidOffer(new Bid(16, Trump.NO_TRUMP)),
        state.highestBid().orElseThrow().action());
  }

  @Test
  @DisplayName("모든 점수와 기루 조합에서 공약 비교 규칙을 만족한다")
  void comparesAllContracts() {
    for (int currentPoints = 13; currentPoints <= 20; currentPoints++) {
      for (int offeredPoints = 13; offeredPoints <= 20; offeredPoints++) {
        for (Trump currentTrump : Trump.values()) {
          for (Trump offeredTrump : Trump.values()) {
            boolean expected = currentPoints < 20
                && (offeredPoints > currentPoints
                || (offeredPoints == currentPoints
                && offeredTrump == Trump.NO_TRUMP
                && currentTrump != Trump.NO_TRUMP));
            assertEquals(expected, BiddingRules.isHigher(
                new Bid(offeredPoints, offeredTrump),
                new Bid(currentPoints, currentTrump)
            ));
          }
        }
      }
    }
  }

  @Test
  @DisplayName("현재 공약이 없으면 명시한 오류 메시지로 거절된다")
  void rejectsMissingCurrentContract() {
    NullPointerException error = assertThrows(NullPointerException.class,
        () -> BiddingRules.isHigher(new Bid(15, Trump.SPADE), null));
    assertEquals("현재 공약이 필요합니다.", error.getMessage());
  }

  @Test
  @DisplayName("오마와 육마에서 모든 문양의 풀은 즉시 낙찰된다")
  void fullContractImmediatelyWins() {
    for (int count : new int[]{
        5, 6
    }) {
      for (Trump trump : Trump.values()) {
        BiddingState state = offer(start(count, 0), 20, trump);
        assertTrue(state.isFinished());
        assertTrue(state.currentSeat().isEmpty());
        assertEquals(new BiddingResult(seat(0), new Bid(20, trump)),
            state.result().orElseThrow());
        assertThrows(IllegalStateException.class, () -> state.submit(
            new PublicBid(seat(1), BidPass.INSTANCE)
        ));
        assertThrows(IllegalStateException.class, () -> state.submit(
            new PublicBid(seat(1), new BidOffer(new Bid(20, Trump.NO_TRUMP)))
        ));
      }
    }
  }

  @Test
  @DisplayName("경쟁 중 풀을 선언한 참가자가 즉시 주공이 된다")
  void competingFullContractImmediatelyWins() {
    BiddingState state = offer(start(5, 0), 19, Trump.NO_TRUMP);
    state = offer(state, 20, Trump.DIAMOND);
    assertTrue(state.isFinished());
    assertEquals(new BiddingResult(seat(1), new Bid(20, Trump.DIAMOND)),
        state.result().orElseThrow());
  }

}
