package mighty.application.bidding;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import mighty.game.Seat;
import mighty.game.bidding.*;
import mighty.game.deal.DealFormat;
import mighty.game.deal.DealParticipants;
import mighty.game.observation.BiddingView;
import mighty.model.Card;
import mighty.model.Rank;
import mighty.model.StandardCard;
import mighty.model.Suit;
import mighty.model.Trump;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BiddingServiceTest {

  private static BiddingState start() {
    return BiddingState.start(new DealParticipants(
        DealFormat.FIVE_PLAYER_OPENING,
        IntStream.range(0, 5).mapToObj(Seat::new).toList()
    ), new Seat(0));
  }

  private static BiddingView view(BiddingState state, int score) {
    List<Card> hand = Arrays.stream(Suit.values())
        .flatMap(suit -> Arrays.stream(Rank.values())
            .filter(rank -> rank.value() < 10)
            .map(rank -> (Card) new StandardCard(suit, rank)))
        .limit(10).toList();
    return new BiddingView(state.currentSeat().orElseThrow(), hand, score, state);
  }

  @Test
  @DisplayName("정책에 현재 관측을 한 번 전달하고 행동자의 자리를 붙여 제출한다")
  void submitsChosenActionWithoutChangingPreviousState() {
    BiddingState before = start();
    BiddingView observation = view(before, 40);
    AtomicInteger calls = new AtomicInteger();
    BidOffer offer = new BidOffer(new Bid(15, Trump.SPADE));
    BiddingService service = new BiddingService(received -> {
      assertSame(observation, received);
      calls.incrementAndGet();
      return offer;
    });
    BiddingState after = service.playTurn(observation);
    assertEquals(1, calls.get());
    assertEquals(List.of(new PublicBid(new Seat(0), offer)), after.history());
    assertTrue(before.history().isEmpty());
    assertEquals(new Seat(1), after.currentSeat().orElseThrow());
  }

  @Test
  @DisplayName("정책이 고른 불법 공약은 제출 검증에서 거절된다")
  void rejectsIllegalPolicyChoice() {
    BiddingState state = start().submit(new PublicBid(
        new Seat(0), new BidOffer(new Bid(15, Trump.SPADE))));
    BiddingService service = new BiddingService(
        observation -> new BidOffer(new Bid(15, Trump.HEART)));
    assertThrows(IllegalArgumentException.class, () -> service.playTurn(view(state, 40)));
    assertEquals(1, state.history().size());
  }

  @Test
  @DisplayName("적격 딜미스는 관측의 자기 손패와 점수로 검증해 접수한다")
  void submitsEligibleDealMiss() {
    BiddingService service = new BiddingService(observation -> DealMiss.INSTANCE);
    BiddingState result = service.playTurn(view(start(), 6));
    assertTrue(result.isFinished());
    assertEquals(new DealMissResult(new Seat(0)), result.result().orElseThrow());
  }

  @Test
  @DisplayName("정책이 딜미스를 골라도 보유 점수가 부족하면 거절된다")
  void rejectsIneligibleDealMiss() {
    BiddingService service = new BiddingService(observation -> DealMiss.INSTANCE);
    assertThrows(IllegalArgumentException.class, () -> service.playTurn(view(start(), 5)));
  }

  @Test
  @DisplayName("정책이 행동을 반환하지 않으면 명확한 오류로 중단한다")
  void rejectsMissingPolicyAction() {
    BiddingService service = new BiddingService(observation -> null);
    NullPointerException error = assertThrows(NullPointerException.class,
        () -> service.playTurn(view(start(), 40)));
    assertEquals("비딩 판단 정책이 행동을 반환하지 않았습니다.", error.getMessage());
  }

}
