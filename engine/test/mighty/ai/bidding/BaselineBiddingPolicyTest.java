package mighty.ai.bidding;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import mighty.game.Seat;
import mighty.game.bidding.*;
import mighty.game.deal.DealFormat;
import mighty.game.deal.DealParticipants;
import mighty.game.observation.BiddingView;
import mighty.model.Card;
import mighty.model.Joker;
import mighty.model.Rank;
import mighty.model.StandardCard;
import mighty.model.Suit;
import mighty.model.Trump;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BaselineBiddingPolicyTest {

  private final BaselineBiddingPolicy policy = new BaselineBiddingPolicy();

  private static BiddingState start() {
    return BiddingState.start(new DealParticipants(
        DealFormat.FIVE_PLAYER_OPENING,
        IntStream.range(0, 5).mapToObj(Seat::new).toList()
    ), new Seat(0));
  }

  private static List<Card> longestSuitHand(Suit longest) {
    List<Card> hand = new ArrayList<>();
    for (Rank rank : List.of(Rank.TWO, Rank.THREE, Rank.FOUR, Rank.FIVE)) {
      hand.add(new StandardCard(longest, rank));
    }
    for (Suit suit : Suit.values()) {
      if (suit != longest) {
        hand.add(new StandardCard(suit, Rank.TWO));
        hand.add(new StandardCard(suit, Rank.THREE));
      }
    }
    return hand;
  }

  @Test
  @DisplayName("첫 비딩에서 네 문양 각각의 최장 문양으로 13을 입찰한다")
  void opensAtThirteenInLongestSuit() {
    Trump[] expected = {
        Trump.SPADE, Trump.DIAMOND, Trump.HEART, Trump.CLUB
    };
    for (int index = 0; index < Suit.values().length; index++) {
      BiddingView view = new BiddingView(new Seat(0),
          longestSuitHand(Suit.values()[index]), 40, start());
      BiddingAction action = policy.choose(view);
      assertEquals(new BidOffer(new Bid(13, expected[index])), action);
      assertTrue(LegalBiddingActions.legalActions(view.bidding(), view.hand(), view.playerScore())
          .contains(action));
      assertTrue(view.bidding().history().isEmpty());
    }
  }

  @Test
  @DisplayName("장수가 같으면 선언 순서가 앞선 기루 문양을 유지하고 조커는 장수에서 제외한다")
  void resolvesTiesAndExcludesJoker() {
    List<Card> hand = new ArrayList<>();
    for (Rank rank : List.of(Rank.TWO, Rank.THREE, Rank.FOUR)) {
      hand.add(new StandardCard(Suit.DIAMOND, rank));
      hand.add(new StandardCard(Suit.HEART, rank));
    }
    hand.add(new StandardCard(Suit.SPADE, Rank.ACE));
    hand.add(new StandardCard(Suit.CLUB, Rank.TWO));
    hand.add(new StandardCard(Suit.CLUB, Rank.THREE));
    hand.add(Joker.INSTANCE);
    assertEquals(new BidOffer(new Bid(13, Trump.DIAMOND)),
        policy.choose(new BiddingView(new Seat(0), hand, 40, start())));
  }

  @Test
  @DisplayName("앞선 입찰이 있으면 장수가 긴 손패라도 패스한다")
  void passesAfterExistingOffer() {
    BiddingState state = start().submit(new PublicBid(
        new Seat(0), new BidOffer(new Bid(13, Trump.SPADE))));
    assertEquals(BidPass.INSTANCE, policy.choose(new BiddingView(
        new Seat(1), longestSuitHand(Suit.HEART), 40, state)));
  }

  @Test
  @DisplayName("딜미스 자격이 있어도 기준 정책은 자동 선언하지 않고 첫 공약을 낸다")
  void doesNotAutomaticallyDeclareDealMiss() {
    BiddingView view = new BiddingView(new Seat(0), longestSuitHand(Suit.CLUB), 6, start());
    assertTrue(DealMissRules.canDeclare(view.bidding(), view.hand(), view.playerScore()));
    assertEquals(new BidOffer(new Bid(13, Trump.CLUB)), policy.choose(view));
  }

}
