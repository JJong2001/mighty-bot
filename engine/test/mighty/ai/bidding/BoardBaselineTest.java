package mighty.ai.bidding;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import mighty.ai.evaluation.PreparedRoundEvaluator;
import mighty.ai.play.*;
import mighty.application.round.RoundService;
import mighty.data.CardParser;
import mighty.game.*;
import mighty.game.bidding.*;
import mighty.game.deal.*;
import mighty.game.friend.CardFriend;
import mighty.game.observation.BiddingView;
import mighty.game.round.RoundState;
import mighty.game.trick.PublicPlay;
import mighty.model.*;
import org.junit.jupiter.api.Test;

class BoardBaselineTest {
  private static final Seat SELF = new Seat(0);
  private static final List<Card> HAND = cards("S7 D2 D5 D7 D8 DJ DA C8 HA JK");
  private static final List<Card> KITTY = cards("S2 S3 H2");
  private static final Bid AWARD = new Bid(17, Trump.NO_TRUMP);

  private static List<Card> cards(String codes) {
    return CardParser.parseList(codes);
  }

  private static RoundState round(List<Card> hand, List<Card> kitty, Bid award, int seed) {
    return round(hand, kitty, award, seed, List.of());
  }

  private static RoundState round(List<Card> hand, List<Card> kitty, Bid award, int seed,
      List<PublicBid> auction) {
    var pool = new ArrayList<>(DeckFactory.create());
    pool.removeAll(hand);
    pool.removeAll(kitty);
    Collections.shuffle(pool, new Random(seed));
    return PreparedRoundEvaluator.createRound(hand, award, kitty, pool, auction);
  }

  private static void record(String label, Object value) {
    String line = "BASELINE " + label + " " + value;
    System.out.println(line);
    // 전략의 정답이 아니라 이관 전 관측과의 차이만 검출한다.
    try (var stream = BoardBaselineTest.class.getResourceAsStream("/board-inference-baseline.txt");
         var reader = new java.io.BufferedReader(new java.io.InputStreamReader(
             Objects.requireNonNull(stream), java.nio.charset.StandardCharsets.UTF_8))) {
      assertEquals(reader.lines().filter(row -> row.startsWith("BASELINE " + label + " "))
          .findFirst().orElseThrow(), line);
    } catch (java.io.IOException e) {
      throw new java.io.UncheckedIOException(e);
    }
  }

  private static String buried(List<Card> cards) {
    return cards.stream().map(Card::toCode).sorted().toList().toString();
  }

  @Test void comparesPreparationPathsOnSameDeal() {
    // 같은 낙찰 이력에서 일반 평가기가 실제로 잃어버리는 앞선 입찰은 복원하지 않는다.
    var auction = List.of(new PublicBid(new Seat(4), new BidOffer(new Bid(16, Trump.HEART))),
        new PublicBid(SELF, new BidOffer(AWARD)));
    var a = round(HAND, KITTY, AWARD, 29);
    var before = BidExpectedScoreEvaluator.sampledBeforeKitty(HAND, AWARD);
    var plan = BidExpectedScoreEvaluator.sampledPreparation(a, AWARD, before);
    PreparedRoundEvaluator.applyPreparation(a, plan);
    record("path-A", List.of(before, buried(plan.discarded()), a.contract(),
        a.friendSelection().orElseThrow(), new RulePlayPolicy().choose(a.currentView())));

    var b = round(HAND, KITTY, AWARD, 29, auction);
    var service = RoundService.strategic();
    service.advance(b);
    var actualBefore = b.contract();
    service.advance(b);
    String discarded = buried(b.friendView(SELF).discardedCards());
    service.advance(b);
    service.advance(b);
    assertEquals(a.currentView().context().playOrder(), b.currentView().context().playOrder());
    record("path-B", List.of(actualBefore, discarded, b.contract(),
        b.friendSelection().orElseThrow(), b.currentTrick().get(0).action()));
  }

  @Test void hiddenDealCannotChangeDeclarerInference() {
    var first = fixedPlay(HAND, KITTY, AWARD, "SA", 29);
    var second = fixedPlay(HAND, KITTY, AWARD, "SA", 30);
    compareInference(first, second);
  }

  @Test void hiddenDiscardCannotChangeFollowingSeatInference() {
    var kept = new ArrayList<>(HAND);
    var buried = new ArrayList<>(KITTY);
    kept.set(0, buried.set(0, kept.get(0)));
    var first = fixedPlay(HAND, KITTY, AWARD, "SA", 29);
    var second = fixedPlay(kept, buried, AWARD, "SA", 29);
    var lead = new PublicPlay(SELF, new mighty.game.action.StandardPlay(
        (StandardCard) CardParser.parse("DA"), false));
    first.play(lead);
    second.play(lead);
    assertEquals(first.currentView().hand(), second.currentView().hand());
    compareInference(first, second);
  }

  private static void compareInference(RoundState first, RoundState second) {
    var a = new PlayFacts(first.currentView());
    var b = new PlayFacts(second.currentView());
    assertEquals(a.behavior().holdingHints(), b.behavior().holdingHints());
    assertEquals(a.joker().relativeWeights(), b.joker().relativeWeights());
    assertEquals(new RulePlayPolicy().choose(first.currentView()),
        new RulePlayPolicy().choose(second.currentView()));
    for (Suit suit : Suit.values()) {
      assertEquals(a.declarerSuits().assess(suit), b.declarerSuits().assess(suit));
      for (Seat seat : first.currentView().context().playOrder().seats()) {
        assertEquals(a.locations().countRange(seat, suit), b.locations().countRange(seat, suit));
      }
    }
    for (Card card : DeckFactory.create()) {
      assertEquals(a.locations().possibleHolders(card), b.locations().possibleHolders(card));
      for (Seat seat : first.currentView().context().playOrder().seats()) {
        assertEquals(a.locations().behavioralWeight(seat, card),
            b.locations().behavioralWeight(seat, card));
      }
    }
  }

  private static RoundState fixedPlay(List<Card> hand, List<Card> kitty, Bid bid,
      String friend, int seed) {
    var round = round(hand, kitty, bid, seed);
    round.takeKitty(SELF);
    round.discard(SELF, kitty);
    round.chooseFriend(SELF, new CardFriend(CardParser.parse(friend)));
    return round;
  }

  private static void play(String label, String hand, String kitty, int points, String friend) {
    var round = fixedPlay(cards(hand), cards(kitty), new Bid(points, Trump.NO_TRUMP), friend, 29);
    var policy = new RulePlayPolicy();
    var leads = new ArrayList<String>();
    while (round.currentSeat().isPresent()) {
      var view = round.currentView();
      var action = policy.choose(view);
      if (view.self().equals(SELF)) leads.add(action.card().toCode());
      round.play(new PublicPlay(view.self(), action));
    }
    record(label, leads);
  }

  private static void bid(String label, String hand, Bid prior) {
    var seats = java.util.stream.IntStream.range(0, 5).mapToObj(Seat::new).toList();
    var state = BiddingState.start(new DealParticipants(DealFormat.FIVE_PLAYER_OPENING, seats), SELF);
    if (prior != null) state = state.submit(new PublicBid(SELF, new BidOffer(prior)));
    var view = new BiddingView(state.currentSeat().orElseThrow(), cards(hand), 0, state);
    record(label + "-ceilings", new BiddingHandEvaluator().assess(BiddingHand.from(cards(hand))).stream()
        .filter(BidAssessment::canCompete).map(a -> a.trump() + "=" + a.ceilingPoints()).toList());
    record(label, new RuleBiddingPolicy().choose(view));
  }

  @Test void missingFifteenCandidate() { bid("1", "S2 S3 SJ DK DA C6 CJ H10 HA JK", null); }
  @Test void diamondAward() { bid("2", "S7 D5 D6 D7 DK DA C6 CJ CA JK", null); }

  // 원 제보에 배분 전체가 없는 사례는 아래의 재현용 대체 패와 바닥을 명시한다.
  @Test void jokerFriendRecoveryExample() {
    var recovery = round(cards("S5 S10 SQ SK SA D3 D6 D8 D10 HK"), cards("H6 H8 H9"),
        new Bid(15, Trump.NO_TRUMP), 29);
    var service = RoundService.strategic();
    service.advance(recovery);
    service.advance(recovery);
    var discarded = buried(recovery.friendView(SELF).discardedCards());
    service.advance(recovery);
    record("3-preparation", List.of(discarded, recovery.contract(), recovery.friendSelection().orElseThrow()));
    play("3-proxy", "S5 S10 SQ SK SA D8 D10 H6 H8 HK", "D3 D6 H9", 15, "JK");
  }

  @Test void sideAceReturnExample() {
    play("4-proxy", "S3 SK SA HA HK HQ H2 CA CK JK", "D2 D3 D4", 20, "SQ");
  }

  @Test void seventeenOperation() {
    play("5", "S7 D2 D5 D7 D8 DJ DA C8 HA JK", "S2 S3 H2", 17, "SA");
  }

  @Test void clubKingFriendExample() {
    play("6-proxy", "S3 SK SA HA HK HQ H2 CA C2 JK", "D2 D3 D4", 17, "CK");
  }

  @Test void competingWithFiveClubs() {
    bid("7-proxy", "C2 C3 C4 CQ CJ SA H2 H3 H4 H5", new Bid(16, Trump.HEART));
  }

  @Test void restrictedBidCandidates() { bid("8", "S4 S8 SK SA DQ DK C5 H3 H8 HA", null); }
}
