package mighty.game.bidding;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import mighty.game.Seat;
import mighty.game.deal.DealFormat;
import mighty.game.deal.DealParticipants;
import mighty.model.Trump;
import mighty.model.Card;
import mighty.model.Rank;
import mighty.model.StandardCard;
import mighty.model.Suit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LegalBiddingActionsTest {

  private static BiddingState start() {
    return BiddingState.start(new DealParticipants(
        DealFormat.FIVE_PLAYER_OPENING,
        IntStream.range(0, 5).mapToObj(Seat::new).toList()
    ), new Seat(0));
  }

  private static BiddingState afterOffer(int points, Trump trump) {
    return start().submit(new PublicBid(
        new Seat(0), new BidOffer(new Bid(points, trump))
    ));
  }

  @Test
  @DisplayName("첫 비딩에는 패스 없이 중복 없는 40개 공약이 제공된다")
  void openingOffersEveryContract() {
    List<BiddingAction> actions = LegalBiddingActions.bidAndPassActions(start());
    assertEquals(40, actions.size());
    assertEquals(40, new HashSet<>(actions).size());
    assertFalse(actions.contains(BidPass.INSTANCE));
    assertTrue(actions.contains(new BidOffer(new Bid(13, Trump.SPADE))));
    assertTrue(actions.contains(new BidOffer(new Bid(20, Trump.NO_TRUMP))));
    assertThrows(UnsupportedOperationException.class, actions::clear);
  }

  @Test
  @DisplayName("홉노 다음에는 패스와 모든 풀 공약만 가능하다")
  void nineteenNoTrumpAllowsOnlyTwentyAndPass() {
    Set<BiddingAction> actions = new HashSet<>(
        LegalBiddingActions.bidAndPassActions(afterOffer(19, Trump.NO_TRUMP))
    );
    assertEquals(Set.of(
        BidPass.INSTANCE,
        new BidOffer(new Bid(20, Trump.SPADE)),
        new BidOffer(new Bid(20, Trump.DIAMOND)),
        new BidOffer(new Bid(20, Trump.HEART)),
        new BidOffer(new Bid(20, Trump.CLUB)),
        new BidOffer(new Bid(20, Trump.NO_TRUMP))
    ), actions);
  }

  @Test
  @DisplayName("풀 기루 선언 직후에는 선택지가 없다")
  void twentySuitEndsAuction() {
    assertTrue(LegalBiddingActions.bidAndPassActions(afterOffer(20, Trump.SPADE)).isEmpty());
  }

  @Test
  @DisplayName("풀노 선언 직후에는 선택지가 없다")
  void twentyNoTrumpEndsAuction() {
    assertTrue(LegalBiddingActions.bidAndPassActions(afterOffer(20, Trump.NO_TRUMP)).isEmpty());
  }

  @Test
  @DisplayName("종료된 비딩에는 선택 가능한 행동이 없다")
  void finishedAuctionHasNoActions() {
    BiddingState state = afterOffer(13, Trump.CLUB);
    for (int index = 1; index < 5; index++) {
      state = state.submit(new PublicBid(new Seat(index), BidPass.INSTANCE));
    }
    assertTrue(LegalBiddingActions.bidAndPassActions(state).isEmpty());
  }

  @Test
  @DisplayName("모든 최고 공약에서 선택지 목록과 실제 제출 검증 결과가 일치한다")
  void generatedActionsAgreeWithSubmission() {
    // 목록에 빠진 합법 행동과 잘못 포함된 행동을 함께 검사합니다.
    List<BiddingAction> allOffers = LegalBiddingActions.bidAndPassActions(start());
    for (BiddingAction opening : allOffers) {
      BiddingState state = start().submit(new PublicBid(new Seat(0), opening));
      List<BiddingAction> available = LegalBiddingActions.bidAndPassActions(state);
      PublicBid pass = new PublicBid(new Seat(1), BidPass.INSTANCE);
      if (state.isFinished()) {
        assertTrue(available.isEmpty());
        assertThrows(IllegalStateException.class, () -> state.submit(pass));
        for (BiddingAction candidate : allOffers) {
          assertThrows(IllegalStateException.class,
              () -> state.submit(new PublicBid(new Seat(1), candidate)));
        }
        continue;
      }
      assertDoesNotThrow(() -> state.submit(pass));
      assertTrue(available.contains(BidPass.INSTANCE));
      for (BiddingAction candidate : allOffers) {
        PublicBid submission = new PublicBid(new Seat(1), candidate);
        if (available.contains(candidate)) {
          assertDoesNotThrow(() -> state.submit(submission));
        } else {
          assertThrows(IllegalArgumentException.class, () -> state.submit(submission));
        }
      }
    }
  }

  @Test
  @DisplayName("공약 범위 양 끝은 허용되고 범위 밖은 거절된다")
  void contractBoundaries() {
    assertDoesNotThrow(() -> new Bid(13, Trump.SPADE));
    assertDoesNotThrow(() -> new Bid(20, Trump.NO_TRUMP));
    assertThrows(IllegalArgumentException.class, () -> new Bid(12, Trump.SPADE));
    assertThrows(IllegalArgumentException.class, () -> new Bid(21, Trump.NO_TRUMP));
  }

  private static List<Card> lowHand() {
    return java.util.Arrays.stream(Suit.values())
        .flatMap(suit -> java.util.Arrays.stream(Rank.values())
            .filter(rank -> rank.value() < 10)
            .map(rank -> (Card) new StandardCard(suit, rank)))
        .limit(10).toList();
  }

  @Test
  @DisplayName("첫 비딩자는 조건 충족 시 패스 없이 딜미스를 선택할 수 있다")
  void openingCanDeclareDealMiss() {
    List<BiddingAction> actions = LegalBiddingActions.legalActions(start(), lowHand(), 6);
    assertTrue(actions.contains(DealMiss.INSTANCE));
    assertFalse(actions.contains(BidPass.INSTANCE));
    assertEquals(41, actions.size());
    assertThrows(UnsupportedOperationException.class, actions::clear);
    assertFalse(LegalBiddingActions.legalActions(start(), lowHand(), 5)
        .contains(DealMiss.INSTANCE));
  }

  @Test
  @DisplayName("타인의 입찰은 딜미스를 막지 않지만 본인의 이전 입찰은 막는다")
  void distinguishesOwnOfferFromOthers() {
    BiddingState state = afterOffer(13, Trump.SPADE);
    assertTrue(LegalBiddingActions.legalActions(state, lowHand(), 6)
        .contains(DealMiss.INSTANCE));
    state = state.submit(new PublicBid(new Seat(1), new BidOffer(new Bid(14, Trump.HEART))));
    for (int index = 2; index < 5; index++) {
      state = state.submit(new PublicBid(new Seat(index), BidPass.INSTANCE));
    }
    assertEquals(new Seat(0), state.currentSeat().orElseThrow());
    assertFalse(LegalBiddingActions.legalActions(state, lowHand(), 6)
        .contains(DealMiss.INSTANCE));
  }

  @Test
  @DisplayName("풀 즉시 낙찰 뒤에는 딜미스 자격이 있어도 선택지가 없다")
  void fullContractBlocksDealMiss() {
    assertTrue(LegalBiddingActions.legalActions(
        afterOffer(20, Trump.SPADE), lowHand(), 6).isEmpty());
  }

}
