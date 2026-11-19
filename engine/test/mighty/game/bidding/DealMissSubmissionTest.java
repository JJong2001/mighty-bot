package mighty.game.bidding;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;
import mighty.game.Seat;
import mighty.game.deal.DealFormat;
import mighty.game.deal.DealParticipants;
import mighty.model.Card;
import mighty.model.Rank;
import mighty.model.StandardCard;
import mighty.model.Suit;
import mighty.model.Trump;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DealMissSubmissionTest {

  private static BiddingState start(int count) {
    return BiddingState.start(new DealParticipants(
        count == 6 ? DealFormat.SIX_PLAYER_OPENING : DealFormat.FIVE_PLAYER_OPENING,
        IntStream.range(0, count).mapToObj(Seat::new).toList()
    ), new Seat(0));
  }

  private static List<Card> lowHand(int size) {
    return Arrays.stream(Suit.values())
        .flatMap(suit -> Arrays.stream(Rank.values())
            .filter(rank -> rank.value() < 10)
            .map(rank -> (Card) new StandardCard(suit, rank)))
        .limit(size).toList();
  }

  private static PublicBid miss(int seat) {
    return new PublicBid(new Seat(seat), DealMiss.INSTANCE);
  }

  @Test
  @DisplayName("오마와 육마 첫 차례에 적격 딜미스를 접수하고 즉시 종료한다")
  void acceptsOpeningDealMissInBothFormats() {
    for (int count : new int[]{
        5, 6
    }) {
      BiddingState before = start(count);
      int handSize = count == 6 ? 8 : 10;
      BiddingState after = before.submit(miss(0), lowHand(handSize), 6);
      assertTrue(after.isFinished());
      assertTrue(after.currentSeat().isEmpty());
      assertEquals(new DealMissResult(new Seat(0)), after.result().orElseThrow());
      assertTrue(after.highestBid().isEmpty());
      assertEquals(List.of(miss(0)), after.history());
      assertTrue(before.history().isEmpty());
      assertFalse(before.isFinished());
      assertTrue(LegalBiddingActions.legalActions(after, lowHand(handSize), 6).isEmpty());
    }
  }

  @Test
  @DisplayName("선행 입찰이 있어도 다른 참가자의 딜미스는 낙찰 없이 종료한다")
  void dealMissOverridesExistingOffer() {
    BiddingState state = start(5).submit(new PublicBid(
        new Seat(0), new BidOffer(new Bid(13, Trump.SPADE))));
    state = state.submit(miss(1), lowHand(10), 6);
    assertEquals(new DealMissResult(new Seat(1)), state.result().orElseThrow());
    assertTrue(state.highestBid().isPresent());
  }

  @Test
  @DisplayName("잘못된 차례와 개인 정보 없는 딜미스 제출을 거절한다")
  void rejectsWrongTurnAndUncheckedSubmission() {
    BiddingState state = start(5);
    assertThrows(IllegalArgumentException.class, () -> state.submit(miss(1), lowHand(10), 6));
    assertThrows(IllegalArgumentException.class, () -> state.submit(miss(0)));
    assertTrue(state.history().isEmpty());
  }

  @Test
  @DisplayName("점수 부족과 손패 기준 미달 딜미스를 거절한다")
  void rejectsIneligibleDeclaration() {
    BiddingState state = start(5);
    assertThrows(IllegalArgumentException.class, () -> state.submit(miss(0), lowHand(10), 5));
    var highHand = new java.util.ArrayList<>(lowHand(10));
    highHand.set(9, new StandardCard(Suit.HEART, Rank.ACE));
    assertThrows(IllegalArgumentException.class, () -> state.submit(miss(0), highHand, 6));
    assertThrows(IllegalArgumentException.class, () -> state.submit(miss(0), lowHand(8), 6));
  }

  @Test
  @DisplayName("이미 공약을 제시한 사람은 다시 차례가 와도 딜미스를 선언할 수 없다")
  void rejectsPreviousBidder() {
    BiddingState state = start(5).submit(new PublicBid(
        new Seat(0), new BidOffer(new Bid(13, Trump.SPADE))));
    state = state.submit(new PublicBid(new Seat(1), new BidOffer(new Bid(14, Trump.HEART))));
    for (int index = 2; index < 5; index++) {
      state = state.submit(new PublicBid(new Seat(index), BidPass.INSTANCE));
    }
    BiddingState current = state;
    assertThrows(IllegalArgumentException.class,
        () -> current.submit(miss(0), lowHand(10), 6));
  }

  @Test
  @DisplayName("풀 낙찰 또는 딜미스로 종료된 뒤에는 추가 선언을 거절한다")
  void rejectsAfterCompletion() {
    BiddingState full = start(5).submit(new PublicBid(
        new Seat(0), new BidOffer(new Bid(20, Trump.CLUB))));
    BiddingState missed = start(5).submit(miss(0), lowHand(10), 6);
    assertThrows(IllegalStateException.class, () -> full.submit(miss(1), lowHand(10), 6));
    assertThrows(IllegalStateException.class, () -> missed.submit(miss(1), lowHand(10), 6));
    assertThrows(IllegalStateException.class, () -> missed.submit(
        new PublicBid(new Seat(1), BidPass.INSTANCE)));
  }

  @Test
  @DisplayName("개인 정보 인자를 받는 경로에서도 일반 입찰과 패스 검증을 유지한다")
  void ordinaryActionsUseExistingValidation() {
    BiddingState state = start(5).submit(new PublicBid(
        new Seat(0), new BidOffer(new Bid(13, Trump.SPADE))), lowHand(10), 6);
    assertThrows(IllegalArgumentException.class, () -> state.submit(new PublicBid(
        new Seat(1), new BidOffer(new Bid(13, Trump.HEART))), lowHand(10), 6));
    BiddingState passed = state.submit(new PublicBid(new Seat(1), BidPass.INSTANCE), lowHand(10),
        6);
    assertEquals(new Seat(2), passed.currentSeat().orElseThrow());
  }

}
