package mighty.ai.bidding;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.stream.IntStream;
import mighty.game.Seat;
import mighty.game.bidding.*;
import mighty.game.deal.DealFormat;
import mighty.game.deal.DealParticipants;
import mighty.game.observation.BiddingView;
import mighty.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RuleBiddingPolicyTest {

  @Test
  @DisplayName("마이티 문양의 K 초구로 여섯 장 K 기루의 엿 경쟁에 진입한다")
  void recognizesSideOpeningWithoutCountingIndependentRecovery() {
    var owned = mighty.data.CardParser.parseList("S4 SK D10 C5 H2 H3 H5 H7 H9 HK");
    var resources = CandidateResources.from(BiddingHand.from(owned), Trump.HEART);
    assertTrue(resources.hasSideOpening());
    assertEquals(0, resources.sideAces());
    var policy = new RuleBiddingPolicy();
    assertEquals(new BidOffer(new Bid(16, Trump.HEART)),
        policy.choose(new BiddingView(new Seat(0), owned, 40, state())));
    var previous = state().submit(
        new PublicBid(new Seat(0), new BidOffer(new Bid(16, Trump.HEART))));
    assertEquals(BidPass.INSTANCE,
        policy.choose(new BiddingView(new Seat(1), owned, 40, previous)));
    var withoutOpening = mighty.data.CardParser.parseList("S4 SQ D10 C5 H2 H3 H5 H7 H9 HK");
    assertFalse(SuitBidEvaluator.assess(CandidateResources.from(BiddingHand.from(withoutOpening),
        Trump.HEART)).canCompete());
  }

  @Test
  @DisplayName("삽 기루에서는 다리 K도 같은 초구 자원으로 읽는다")
  void openingFollowsMightySuitWhenTrumpChanges() {
    var owned = mighty.data.CardParser.parseList("D4 DK H10 C5 S2 S3 S5 S7 S9 SK");
    var assessment = SuitBidEvaluator.assess(
        CandidateResources.from(BiddingHand.from(owned), Trump.SPADE));
    assertTrue(assessment.canCompete());
    assertEquals(16, assessment.ceilingPoints());
  }

  @Test
  @DisplayName("분산 AK와 조커의 일곱 구 자원은 닷노로 진입하고 엿노까지 경쟁한다")
  void scatteredTopRunsAndJokerCompeteToSixteenNoTrump() {
    var owned = mighty.data.CardParser.parseList("S5 DK DA H2 H8 HA C4 CK CA JK");
    var policy = new RuleBiddingPolicy();
    assertEquals(new BidOffer(new Bid(15, Trump.NO_TRUMP)),
        policy.choose(new BiddingView(new Seat(0), owned, 40, state())));
    var previous = state().submit(
        new PublicBid(new Seat(0), new BidOffer(new Bid(16, Trump.CLUB))));
    assertEquals(new BidOffer(new Bid(16, Trump.NO_TRUMP)),
        policy.choose(new BiddingView(new Seat(1), owned, 40, previous)));
    var ceiling = state().submit(
        new PublicBid(new Seat(0), new BidOffer(new Bid(16, Trump.NO_TRUMP))));
    assertEquals(BidPass.INSTANCE, policy.choose(new BiddingView(new Seat(1), owned, 40, ceiling)));
  }

  private static List<Card> hand() {
    return List.of(new StandardCard(Suit.SPADE, Rank.ACE), new StandardCard(Suit.SPADE, Rank.KING),
        new StandardCard(Suit.SPADE, Rank.JACK), new StandardCard(Suit.SPADE, Rank.SEVEN),
        new StandardCard(Suit.SPADE, Rank.TWO), new StandardCard(Suit.DIAMOND, Rank.QUEEN),
        new StandardCard(Suit.DIAMOND, Rank.TEN), new StandardCard(Suit.HEART, Rank.FIVE),
        new StandardCard(Suit.CLUB, Rank.TEN), new StandardCard(Suit.CLUB, Rank.FIVE));
  }

  private static BiddingState state() {
    return BiddingState.start(new DealParticipants(DealFormat.FIVE_PLAYER_OPENING,
        IntStream.range(0, 5).mapToObj(Seat::new).toList()), new Seat(0));
  }

  @Test
  @DisplayName("A29의 닷노와 엿삽 경쟁 상한을 엿노로 혼동하지 않는다")
  void keepsNoTrumpAndSuitCeilingsSeparate() {
    var assessment = new BiddingHandEvaluator().assess(BiddingHand.from(hand()));
    assertEquals(15,
        assessment.stream().filter(a -> a.trump() == Trump.NO_TRUMP).findFirst().orElseThrow()
            .ceilingPoints());
    assertEquals(16,
        assessment.stream().filter(a -> a.trump() == Trump.SPADE).findFirst().orElseThrow()
            .ceilingPoints());
    var fifteenHeart = state().submit(
        new PublicBid(new Seat(0), new BidOffer(new Bid(15, Trump.HEART))));
    assertEquals(new BidOffer(new Bid(15, Trump.NO_TRUMP)),
        new RuleBiddingPolicy().choose(new BiddingView(new Seat(1), hand(), 40, fifteenHeart)));
    var sixteenClub = state().submit(
        new PublicBid(new Seat(0), new BidOffer(new Bid(16, Trump.CLUB))));
    assertEquals(BidPass.INSTANCE,
        new RuleBiddingPolicy().choose(new BiddingView(new Seat(1), hand(), 40, sixteenClub)));
  }

  @Test
  @DisplayName("노기루 성공각이 보이면 기루 평균보다 먼저 닷노 운영을 연다")
  void doesNotOpenAtCeiling() {
    BiddingAction action = new RuleBiddingPolicy().choose(
        new BiddingView(new Seat(0), hand(), 40, state()));
    assertInstanceOf(BidOffer.class, action);
    assertEquals(new BidOffer(new Bid(15, Trump.NO_TRUMP)), action);
    assertNotEquals(16, ((BidOffer) action).bid().points());
  }

  @Test
  @DisplayName("기루와 노기루의 기대 명목 점수 비교는 보유 칩에 따라 바뀌지 않는다")
  void nominalBidChoiceDoesNotDependOnChipBalance() {
    var policy = new RuleBiddingPolicy();
    BiddingAction shortStack = policy.choose(new BiddingView(new Seat(0), hand(), 6, state()));
    BiddingAction deepStack = policy.choose(new BiddingView(new Seat(0), hand(), 40, state()));
    assertEquals(new BidOffer(new Bid(15, Trump.NO_TRUMP)), shortStack);
    assertEquals(deepStack, shortStack);
  }

  @Test
  @DisplayName("노기루 각이 없는 마이티 보유 패는 조커를 통제하는 기루 경로를 택한다")
  void selectsMightyControlledTrumpWhenNoTrumpHasNoSuccessLine() {
    var owned = mighty.data.CardParser.parseList("SA SJ S8 S3 DJ D5 D4 D2 H10 C4");
    var policy = new RuleBiddingPolicy();

    assertEquals(new BidOffer(new Bid(15, Trump.DIAMOND)),
        policy.choose(new BiddingView(new Seat(0), owned, 40, state())));
  }

  @Test
  @DisplayName("마쪼·A·KQ의 유연한 패는 닷노로 진입하고 엿 경쟁에는 패스한다")
  void flexibleSpecialsEnterNoTrumpWithoutLongSuit() {
    var owned = mighty.data.CardParser.parseList("S6 SA DK H3 HQ HA C5 C6 C10 JK");
    var policy = new RuleBiddingPolicy();
    assertEquals(new BidOffer(new Bid(15, Trump.NO_TRUMP)),
        policy.choose(new BiddingView(new Seat(0), owned,
            40, state())));
    var over16 = state().submit(new PublicBid(new Seat(0), new BidOffer(new Bid(16, Trump.CLUB))));
    assertEquals(BidPass.INSTANCE, policy.choose(new BiddingView(new Seat(1), owned, 40, over16)));
    var noJoker = mighty.data.CardParser.parseList("S6 SA DK H3 HQ HA C5 C6 C10 C2");
    assertEquals(BidPass.INSTANCE,
        policy.choose(new BiddingView(new Seat(1), noJoker, 40, over16)));
  }

}
