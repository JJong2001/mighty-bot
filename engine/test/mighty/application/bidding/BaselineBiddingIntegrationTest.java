package mighty.application.bidding;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import mighty.ai.bidding.BaselineBiddingPolicy;
import mighty.game.Seat;
import mighty.game.bidding.*;
import mighty.game.deal.DealFormat;
import mighty.game.deal.DealParticipants;
import mighty.game.deal.DeckFactory;
import mighty.game.observation.BiddingView;
import mighty.model.Card;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BaselineBiddingIntegrationTest {

  @Test
  @DisplayName("여러 시작 자리와 배분에서 첫 입찰부터 전원 패스 및 낙찰까지 완료한다")
  void completesAuctionWithOwnHandObservations() {
    // 전체 손패는 테스트 진행부만 보관하고 정책에는 현재 참가자의 패만 전달합니다.
    List<List<Seat>> configurations = List.of(
        List.of(new Seat(0), new Seat(1), new Seat(2), new Seat(3), new Seat(4)),
        List.of(new Seat(0), new Seat(1), new Seat(2), new Seat(3), new Seat(4), new Seat(5)),
        List.of(new Seat(0), new Seat(1), new Seat(3), new Seat(4), new Seat(5))
    );
    BiddingService service = new BiddingService(new BaselineBiddingPolicy());
    for (List<Seat> seats : configurations) {
      DealFormat format = seats.size() == 6
          ? DealFormat.SIX_PLAYER_OPENING : DealFormat.FIVE_PLAYER_OPENING;
      for (int seed = 0; seed < 20; seed++) {
        List<Card> deck = new ArrayList<>(DeckFactory.create());
        Collections.shuffle(deck, new Random(seed));
        Map<Seat, List<Card>> privateHands = new HashMap<>();
        for (int index = 0; index < seats.size(); index++) {
          int from = index * format.handSize();
          privateHands.put(seats.get(index),
              List.copyOf(deck.subList(from, from + format.handSize())));
        }
        Seat opening = seats.get(seed % seats.size());
        BiddingState state = BiddingState.start(new DealParticipants(format, seats), opening);
        int turns = 0;
        while (!state.isFinished()) {
          assertTrue(turns < seats.size(), "비딩이 참가자 수 이내에 종료되어야 합니다.");
          Seat self = state.currentSeat().orElseThrow();
          state = service.playTurn(new BiddingView(self, privateHands.get(self), 40, state));
          turns++;
        }
        assertEquals(seats.size(), turns);
        assertEquals(seats.size(), state.history().size());
        PublicBid first = state.history().get(0);
        BidOffer offer = assertInstanceOf(BidOffer.class, first.action());
        assertEquals(13, offer.bid().points());
        BiddingResult result = assertInstanceOf(BiddingResult.class, state.result().orElseThrow());
        assertEquals(opening, result.declarer());
        assertEquals(offer.bid(), result.contract());
        assertEquals(seats.size() - 1, state.passedSeats().size());
        assertTrue(state.currentSeat().isEmpty());
        for (PublicBid subsequent : state.history().subList(1, turns)) {
          assertEquals(BidPass.INSTANCE, subsequent.action());
        }
      }
    }
  }

}
