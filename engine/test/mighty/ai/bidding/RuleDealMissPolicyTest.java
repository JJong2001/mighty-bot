package mighty.ai.bidding;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.*;
import mighty.data.CardParser;
import mighty.game.*;
import mighty.game.bidding.*;
import mighty.game.deal.*;
import mighty.game.observation.BiddingView;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class RuleDealMissPolicyTest {

  static Stream<Arguments> cases() {
    return Stream.of(
        Arguments.of(false, "SA S2 D2 D3 D4 H2 H3 H4 C2 C3", true),
        Arguments.of(false, "SA S2 S3 D2 D3 D4 H2 H3 C2 C3", false),
        Arguments.of(false, "SA S2 D2 D3 D4 D5 H2 H3 C2 C3", true),
        Arguments.of(false, "SA S2 D2 D3 D4 D5 D6 H2 C2 C3", false),
        Arguments.of(false, "JK S2 S3 D2 D3 H2 H3 C2 C3 C4", true),
        Arguments.of(false, "JK SK S2 D2 D3 H2 H3 C2 C3 C4", true),
        Arguments.of(false, "JK HA H2 D2 D3 D4 D5 C2 C3 S2", false),
        Arguments.of(false, "JK D10 D2 D3 D4 H2 H3 C2 C3 S2", false),
        Arguments.of(false, "JK D5 D2 D3 D4 H2 H3 C2 C3 S2", true),
        Arguments.of(false, "JK HA H2 H3 H4 D2 D3 C2 C3 S2", false),
        Arguments.of(false, "JK HA H2 H3 D2 D3 D4 C2 C3 S2", false),
        Arguments.of(false, "S2 S3 D2 D3 H2 H3 H4 C2 C3 C10", true),
        Arguments.of(false, "S2 S3 D2 D3 H2 H3 H4 C2 C3 CQ", false),
        Arguments.of(false, "JK H10 D10 H2 H3 D2 D3 C2 C3 S2", false),
        Arguments.of(false, "JK HJ D10 H2 H3 D2 D3 C2 C3 S2", false),
        Arguments.of(false, "JK HA H2 H3 D2 D3 C2 C3 S2 S3", false),
        Arguments.of(false, "SA JK S2 D2 D3 H2 H3 C2 C3 C4", false),
        Arguments.of(true, "SA S2 D2 D3 H2 H3 C2 C3", false),
        Arguments.of(true, "JK S2 D2 D3 H2 H3 C2 C3", false),
        Arguments.of(true, "S2 S3 D2 D3 H2 H3 C2 C3", true),
        Arguments.of(true, "S2 S3 D2 D3 H2 H3 C2 CA", false));
  }

  @ParameterizedTest
  @MethodSource("cases")
  @DisplayName("오마·육마의 선언 자격과 유지 예외를 분리한다")
  void followsModeSpecificCriteria(boolean six, String owned, boolean expected) {
    var format = six ? DealFormat.SIX_PLAYER_OPENING : DealFormat.FIVE_PLAYER_OPENING;
    var seats = IntStream.range(0, format.participantCount()).mapToObj(Seat::new).toList();
    var state = BiddingState.start(new DealParticipants(format, seats), new Seat(0));
    var view = new BiddingView(new Seat(0), CardParser.parseList(owned), 40, state);
    assertEquals(expected, new RuleDealMissPolicy().shouldDeclare(view));
    assertEquals(expected, new RuleBiddingPolicy().choose(view) instanceof DealMiss);
  }

  @Test
  @DisplayName("보유 점수 5 이하와 이미 공약을 제시한 자리는 딜미스하지 않는다")
  void respectsEligibilityAndPriorOffer() {
    var seats = IntStream.range(0, 5).mapToObj(Seat::new).toList();
    var state = BiddingState.start(new DealParticipants(DealFormat.FIVE_PLAYER_OPENING, seats),
        new Seat(0));
    var hand = CardParser.parseList("SA S2 D2 D3 D4 H2 H3 H4 C2 C3");
    assertFalse(
        new RuleDealMissPolicy().shouldDeclare(new BiddingView(new Seat(0), hand, 5, state)));
    state = state.submit(
            new PublicBid(new Seat(0), new BidOffer(new Bid(13, mighty.model.Trump.CLUB))))
        .submit(new PublicBid(new Seat(1), new BidOffer(new Bid(14, mighty.model.Trump.DIAMOND))))
        .submit(new PublicBid(new Seat(2), BidPass.INSTANCE))
        .submit(new PublicBid(new Seat(3), BidPass.INSTANCE))
        .submit(new PublicBid(new Seat(4), BidPass.INSTANCE));
    assertFalse(
        new RuleDealMissPolicy().shouldDeclare(new BiddingView(new Seat(0), hand, 40, state)));
  }

  @Test
  @DisplayName("육마 휴식 판의 다섯 명 10장 배분에도 육마 유지 기준을 적용한다")
  void sixPlayerRestingRoundKeepsSpecials() {
    var seats = IntStream.range(0, 5).mapToObj(Seat::new).toList();
    var state = BiddingState.start(new DealParticipants(DealFormat.FIVE_PLAYER_OPENING, seats),
        new Seat(0));
    for (String hand : java.util.List.of("SA S2 D2 D3 D4 H2 H3 H4 C2 C3",
        "JK S2 S3 D2 D3 H2 H3 C2 C3 C4")) {
      assertFalse(new RuleDealMissPolicy().shouldDeclare(new BiddingView(new Seat(0),
          CardParser.parseList(hand), 40, state, GameMode.SIX_PLAYER)));
    }
    assertTrue(new RuleDealMissPolicy().shouldDeclare(new BiddingView(new Seat(0),
        CardParser.parseList("S2 S3 D2 D3 D4 H2 H3 H4 C2 C3"), 40, state, GameMode.SIX_PLAYER)));
  }

  @Test
  @DisplayName("조커와 점수 있는 4장 문양은 입찰하고 상한을 넘으면 패스한다")
  void keepsFourCardJokerHandForBiddingOrPassing() {
    var seats = IntStream.range(0, 5).mapToObj(Seat::new).toList();
    var state = BiddingState.start(new DealParticipants(DealFormat.FIVE_PLAYER_OPENING, seats),
        new Seat(0));
    var hand = CardParser.parseList("JK HA H2 D2 D3 D4 D5 C2 C3 S2");
    var policy = new RuleBiddingPolicy();
    assertEquals(new BidOffer(new Bid(15, mighty.model.Trump.DIAMOND)),
        policy.choose(new BiddingView(new Seat(0), hand, 40, state)));
    var aboveCeiling = state.submit(new PublicBid(new Seat(0),
        new BidOffer(new Bid(17, mighty.model.Trump.NO_TRUMP))));
    assertEquals(BidPass.INSTANCE,
        policy.choose(new BiddingView(new Seat(1), hand, 40, aboveCeiling)));
  }

}
