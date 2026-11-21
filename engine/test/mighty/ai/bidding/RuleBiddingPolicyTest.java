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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class RuleBiddingPolicyTest {

  @ParameterizedTest
  @ValueSource(strings = {
      "SK S3 HJ H10 H9 CA CK CJ C2 JK"
  })
  @DisplayName("조커와 클로버 주력이 있는 패는 닷노로 열고 닷기루에도 닷노를 부른다")
  void opensAndCompetesOnlyToFifteenNoTrumpWithDevelopableSuits(String cards) {
    var owned = mighty.data.CardParser.parseList(cards);
    var policy = new RuleBiddingPolicy();
    var opening = state();
    var contested = opening.submit(
        new PublicBid(new Seat(0), new BidOffer(new Bid(15, Trump.CLUB))));
    var expected = new BidOffer(new Bid(15, Trump.NO_TRUMP));
    assertEquals(expected, policy.choose(new BiddingView(new Seat(0), owned, 40, opening)));
    assertEquals(expected, policy.choose(new BiddingView(new Seat(1), owned, 40, contested)));
    var hand = BiddingHand.from(owned);
    var assessment = NoTrumpBidEvaluator.assess(CandidateResources.from(hand, Trump.NO_TRUMP),
        List.of());
    assertEquals(15, assessment.ceilingPoints());
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "SK S3 HJ H10 H9 CA CK C10 C2 JK",
      "SA SQ S5 S3 D10 HA HK H3 H2 CK"
  })
  @DisplayName("별도 회수 탑이 없거나 조커 한 번으로 주력 점수 카드가 살아나지 않으면 이 경로로 입찰하지 않는다")
  void requiresBothDevelopmentAndReentry(String cards) {
    var hand = BiddingHand.from(mighty.data.CardParser.parseList(cards));
    var assessment = NoTrumpBidEvaluator.assess(CandidateResources.from(hand, Trump.NO_TRUMP),
        List.of());
    assertFalse(assessment.canCompete());
  }

  @ParameterizedTest
  @CsvSource({
      "SQ S3 HJ H10 H9 CA CK CJ C2 JK, NO_TRUMP",
      "S4 S3 H4 H8 H9 CA CK CJ C2 JK, CLUB"
  })
  @DisplayName("별도 회수 탑이 없는 주력 패는 닷노와 기루의 정산점을 비교해 입찰한다")
  void comparesJokerSweepAgainstSuitedBid(String cards, Trump selected) {
    var owned = mighty.data.CardParser.parseList(cards);
    var hand = BiddingHand.from(owned);
    var assessment = NoTrumpBidEvaluator.assess(CandidateResources.from(hand, Trump.NO_TRUMP),
        List.of());
    assertTrue(assessment.compareWithSuitedBids());
    assertEquals(15, assessment.ceilingPoints());
    assertEquals(new BidOffer(new Bid(15, selected)),
        new RuleBiddingPolicy().choose(new BiddingView(new Seat(0), owned, 40, state())));
  }

  @ParameterizedTest
  @CsvSource({
      "SA SK S5 S3 D10 HA HK H3 H2 CK, 15, 16, NO_TRUMP",
      "SA SK S5 S3 D10 HA HK HQ H2 CK, 16, 0, NO_TRUMP",
      "JK SK S5 S3 D10 HA HK HQ H2 CK, 16, 16, NO_TRUMP",
      "JK SK S5 S3 D10 HA HK HQ H2 CK, 17, 17, NO_TRUMP",
      "JK SK S5 S3 D10 HA HK HQ H2 CK, 18, 0, NO_TRUMP",
      "SA SK S5 S3 D10 HA HK HQ H2 JK, 15, 16, HEART",
      "SA SK S5 S3 D10 HA HK HQ H2 JK, 17, 17, NO_TRUMP"
  })
  void comparesFriendProspectWithTakingContract(String cards, int standing, int points, Trump trump) {
    var auction = state().submit(new PublicBid(new Seat(0), new BidOffer(new Bid(standing, Trump.CLUB))));
    var action = new RuleBiddingPolicy().choose(new BiddingView(new Seat(1),
        mighty.data.CardParser.parseList(cards), 40, auction));
    assertEquals(points == 0 ? BidPass.INSTANCE : new BidOffer(new Bid(points, trump)), action);
  }

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

  @Test
  void measuresSameSuitCompetitionUsingPublicBidAndActualFriendInEachDeal() {
    var owned = mighty.data.CardParser.parseList("SA SK CK CQ CJ C7 C2 D5 H4 H2");
    var hand = BiddingHand.from(owned);
    for (var bid : List.of(new Bid(15, Trump.CLUB), new Bid(16, Trump.NO_TRUMP),
        new Bid(16, Trump.DIAMOND))) {
      var bidding = state().submit(new PublicBid(new Seat(0), new BidOffer(bid)));
      var view = new BiddingView(new Seat(1), owned, 40, bidding);
      var offers = LegalBiddingActions.bidAndPassActions(bidding).stream()
          .filter(BidOffer.class::isInstance).map(BidOffer.class::cast).toList();
      assertTrue(ContestedSuitAuction.signal(view, hand, offers).isEmpty());
    }
    var signal = new PublicBid(new Seat(0), new BidOffer(new Bid(16, Trump.CLUB)));
    var auction = state().submit(signal);
    var view = new BiddingView(new Seat(1), owned, 40, auction);
    var counter = new BidOffer(new Bid(17, Trump.CLUB));
    assertEquals(signal, ContestedSuitAuction.signal(view, hand, List.of(counter)).orElseThrow());
    var comparison = ContestedSuitAuction.evaluate(view, signal, List.of(counter));
    var outcome = comparison.estimates().get(counter);
    var ally = outcome.bidderIsAlly();
    var enemy = outcome.bidderIsOpponent();
    assertEquals(32, ally.deals + enemy.deals);
    assertTrue(ally.deals > 0 && enemy.deals > 0);
    // 같은 패로 맞기루를 부르면 선입찰자가 야당인 배분에서 성공률과 내 정산점이 더 낮습니다.
    assertTrue((long) enemy.successes * ally.deals < (long) ally.successes * enemy.deals);
    assertTrue((long) enemy.settlement * ally.deals < (long) ally.settlement * enemy.deals);
    assertTrue(comparison.estimates().get(BidPass.INSTANCE).total() > outcome.total());
    assertEquals(BidPass.INSTANCE, comparison.choice());
    assertEquals(comparison.choice(), new RuleBiddingPolicy().choose(view));
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
  @DisplayName("첫 표본이 애매하면 같은 바닥부터 누적해 기루 후보를 다시 비교한다")
  void comparesMoreDealsForUncertainBids() {
    var hand = BiddingHand.from(mighty.data.CardParser.parseList("SA S10 S9 S6 D3 H2 CJ C8 C4 C2"));
    var evaluator = new BidExpectedScoreEvaluator();
    var clubs = new Bid(15, Trump.CLUB);
    var spades = new Bid(15, Trump.SPADE);
    var firstClubs = evaluator.initialBiddingSamples(hand, clubs);
    var firstSpades = evaluator.initialBiddingSamples(hand, spades);

    assertTrue(firstSpades.mean() > firstClubs.mean());
    assertTrue(firstSpades.differenceIsUncertain(firstClubs));

    var expandedClubs = evaluator.expandedBiddingSamples(hand, clubs);
    var expandedSpades = evaluator.expandedBiddingSamples(hand, spades);
    assertEquals(1000, expandedClubs.kittySettlements().size() * expandedClubs.dealsPerKitty());
    assertEquals(1000, expandedSpades.kittySettlements().size() * expandedSpades.dealsPerKitty());
    assertEquals(firstClubs.kittySettlements(), expandedClubs.kittySettlements().subList(0, 10));
    assertEquals(firstSpades.kittySettlements(), expandedSpades.kittySettlements().subList(0, 10));
    assertTrue(expandedClubs.mean() > expandedSpades.mean());
    assertEquals(firstClubs, evaluator.initialBiddingSamples(hand, clubs));
    assertEquals(firstSpades, evaluator.initialBiddingSamples(hand, spades));
  }

  @Test
  @DisplayName("바닥별 정산점의 차이가 일정하면 첫 표본으로 비교를 마친다")
  void confidentlySeparatesConsistentPairedScores() {
    var leader = new BidExpectedScoreEvaluator.ScoreSamples(
        List.of(25, 40, -30, 10, 5, 0, 30, 20, -10, 0), 10);
    var other = new BidExpectedScoreEvaluator.ScoreSamples(
        leader.kittySettlements().stream().map(total -> total - 10).toList(), 10);

    assertFalse(leader.differenceIsUncertain(other));
    assertTrue(leader.differenceIsUncertain(leader));
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
