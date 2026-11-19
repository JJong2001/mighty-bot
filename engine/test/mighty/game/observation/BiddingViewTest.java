package mighty.game.observation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import mighty.game.Seat;
import mighty.game.bidding.*;
import mighty.game.deal.DealFormat;
import mighty.game.deal.DealParticipants;
import mighty.game.deal.DeckFactory;
import mighty.model.Card;
import mighty.model.Trump;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BiddingViewTest {

  private static BiddingState start(int count) {
    List<Seat> seats = IntStream.range(0, count).mapToObj(Seat::new).toList();
    DealFormat format = count == 6
        ? DealFormat.SIX_PLAYER_OPENING : DealFormat.FIVE_PLAYER_OPENING;
    return BiddingState.start(new DealParticipants(format, seats), new Seat(0));
  }

  private static List<Card> hand(int size) {
    return new ArrayList<>(DeckFactory.create().subList(0, size));
  }

  private static BiddingState finish() {
    BiddingState state = start(5).submit(new PublicBid(
        new Seat(0), new BidOffer(new Bid(15, Trump.NO_TRUMP))
    ));
    for (int index = 1; index < 5; index++) {
      state = state.submit(new PublicBid(new Seat(index), BidPass.INSTANCE));
    }
    return state;
  }

  @Test
  @DisplayName("오마 비딩에서는 자기 손패 열 장을 받는다")
  void acceptsFivePlayerHand() {
    BiddingView view = new BiddingView(new Seat(0), hand(10), 40, start(5));
    assertEquals(10, view.hand().size());
  }

  @Test
  @DisplayName("육마 최초 비딩에서는 자기 손패 여덟 장을 받는다")
  void acceptsSixPlayerOpeningHand() {
    BiddingView view = new BiddingView(new Seat(0), hand(8), 40, start(6));
    assertEquals(8, view.hand().size());
  }

  @Test
  @DisplayName("배분 형식과 다른 카드 수는 거절한다")
  void rejectsWrongHandSize() {
    assertThrows(IllegalArgumentException.class,
        () -> new BiddingView(new Seat(0), hand(8), 40, start(5)));
    assertThrows(IllegalArgumentException.class,
        () -> new BiddingView(new Seat(0), hand(10), 40, start(6)));
  }

  @Test
  @DisplayName("중복된 손패 카드는 거절한다")
  void rejectsDuplicateCards() {
    List<Card> cards = hand(10);
    cards.set(9, cards.get(0));
    assertThrows(IllegalArgumentException.class,
        () -> new BiddingView(new Seat(0), cards, 40, start(5)));
  }

  @Test
  @DisplayName("현재 차례와 다른 자리와 비참가자 관측은 거절한다")
  void rejectsWrongActor() {
    assertThrows(IllegalArgumentException.class,
        () -> new BiddingView(new Seat(1), hand(10), 40, start(5)));
    assertThrows(IllegalArgumentException.class,
        () -> new BiddingView(new Seat(5), hand(10), 40, start(5)));
  }

  @Test
  @DisplayName("종료된 비딩의 행동용 관측은 만들 수 없다")
  void rejectsFinishedAuction() {
    assertThrows(IllegalArgumentException.class,
        () -> new BiddingView(new Seat(0), hand(10), 40, finish()));
  }

  @Test
  @DisplayName("원본 손패 변경은 관측에 영향을 주지 않는다")
  void copiesHand() {
    List<Card> cards = hand(10);
    BiddingView view = new BiddingView(new Seat(0), cards, 40, start(5));
    cards.clear();
    assertEquals(10, view.hand().size());
    assertThrows(UnsupportedOperationException.class, () -> view.hand().clear());
  }

  @Test
  @DisplayName("진행 중에는 낙찰 결과가 없고 종료 후에는 주공과 공약이 확정된다")
  void exposesOnlyFinishedResult() {
    assertTrue(start(5).result().isEmpty());
    BiddingState offered = start(5).submit(new PublicBid(
        new Seat(0), new BidOffer(new Bid(15, Trump.NO_TRUMP))
    ));
    assertTrue(offered.result().isEmpty());
    assertEquals(new BiddingResult(new Seat(0), new Bid(15, Trump.NO_TRUMP)),
        finish().result().orElseThrow());
  }

  @Test
  @DisplayName("보유 점수가 5점 이하라도 일반 비딩 관측은 생성된다")
  void allowsLowPlayerScore() {
    for (int score : new int[]{
        0, 5, 6, 40
    }) {
      BiddingView view = new BiddingView(new Seat(0), hand(10), score, start(5));
      assertEquals(score, view.playerScore());
    }
  }

}
