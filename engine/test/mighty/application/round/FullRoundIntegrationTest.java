package mighty.application.round;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;
import mighty.ai.bidding.BaselineBiddingPolicy;
import mighty.application.bidding.BiddingService;
import mighty.game.*;
import mighty.game.bidding.*;
import mighty.game.deal.*;
import mighty.game.observation.BiddingView;
import mighty.game.round.*;
import mighty.model.Card;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class FullRoundIntegrationTest {

  @Test
  @DisplayName("비딩부터 바닥 정리와 프렌드 지정 및 10구 득점까지 100판을 실행한다")
  void completesOneHundredFullRounds() {
    for (int seed = 0; seed < 100; seed++) {
      Random random = new Random(seed);
      List<Card> deck = new ArrayList<>(DeckFactory.create());
      Collections.shuffle(deck, random);
      List<Seat> seats = IntStream.range(0, 5).mapToObj(Seat::new).toList();
      Map<Seat, List<Card>> hands = new HashMap<>();
      for (int index = 0; index < 5; index++) {
        hands.put(seats.get(index), List.copyOf(deck.subList(index * 10, index * 10 + 10)));
      }
      BiddingState bidding = BiddingState.start(new DealParticipants(
          DealFormat.FIVE_PLAYER_OPENING, seats), seats.get(random.nextInt(5)));
      BiddingService biddingService = new BiddingService(new BaselineBiddingPolicy());
      while (!bidding.isFinished()) {
        Seat self = bidding.currentSeat().orElseThrow();
        bidding = biddingService.playTurn(new BiddingView(self, hands.get(self), 40, bidding));
      }
      BiddingResult auction = assertInstanceOf(BiddingResult.class, bidding.result().orElseThrow());
      RoundState round = RoundState.start(PlayOrder.from(
              new TableLayout(GameMode.FIVE_PLAYER, seats), new HashSet<>(seats)),
          auction, hands, deck.subList(50, 53));
      RoundResult result = RoundService.baseline().playToEnd(round);
      assertEquals(GamePhase.FINISHED, round.phase());
      assertEquals(10, round.history().size());
      assertEquals(20, result.teamPoints() + result.oppositionPoints());
      assertEquals(auction.contract(), result.contract());
      assertEquals(auction.declarer(), result.declarer());
      assertEquals(result, RoundService.baseline().playToEnd(round));
    }
  }

}
