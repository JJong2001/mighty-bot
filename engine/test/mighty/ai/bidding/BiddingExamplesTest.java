package mighty.ai.bidding;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import mighty.data.CardParser;
import mighty.game.Seat;
import mighty.game.bidding.*;
import mighty.game.deal.*;
import mighty.game.observation.BiddingView;
import mighty.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class BiddingExamplesTest {

  private record Example(int number, String hand, Trump trump, int opening, int ceiling,
                         boolean voluntary, Trump selectedTrump) {

    private Example(int number, String hand, Trump trump, int opening, int ceiling,
        boolean voluntary) {
      this(number, hand, trump, opening, ceiling, voluntary, trump);
    }

  }

  private final RuleBiddingPolicy policy = new RuleBiddingPolicy();

  static Stream<Example> examples() {
    return Stream.of(
        new Example(1, "SK S9 S5 DK DJ D7 D2 CA CQ CJ", Trump.DIAMOND, 15, 16, true),
        new Example(2, "D9 D8 D3 D2 HA HK H3 C9 C6 JK", Trump.DIAMOND, 15, 16, true),
        new Example(3, "SQ DQ D10 D4 HA H6 CK C10 C6 C3", Trump.CLUB, 15, 15, true),
        new Example(4, "SA SJ S8 S3 DJ D5 D4 D2 H10 C4", Trump.DIAMOND, 15, 16, true),
        new Example(5, "SJ S10 S9 S5 D10 D5 HK H5 C10 C4", Trump.SPADE, 13, 13, false),
        new Example(6, "SK S4 S2 D7 D6 D4 CA C8 C4 C2", Trump.CLUB, 15, 15, true),
        // 7번은 원문이 문양 판단을 유보했으므로 아래 삽 선택은 현재 모델의 검토 결과입니다.
        new Example(7, "SA S10 S7 S4 DQ DJ D9 H8 C6 C3", Trump.SPADE, 15, 16, true),
        new Example(8, "SK S9 S5 HJ H9 H8 H2 CA CK C6", Trump.HEART, 15, 16, true),
        new Example(9, "SA S10 S9 S6 D3 H2 CJ C8 C4 C2", Trump.CLUB, 15, 16, true),
        new Example(10, "SK S4 D8 H10 H7 H6 H3 C5 C3 C2", Trump.HEART, 13, 13, false));
  }

  private static BiddingState state(Bid previous) {
    List<Seat> seats = IntStream.range(0, 5).mapToObj(Seat::new).toList();
    BiddingState state = BiddingState.start(
        new DealParticipants(DealFormat.FIVE_PLAYER_OPENING, seats), new Seat(0));
    return previous == null ? state
        : state.submit(new PublicBid(new Seat(0), new BidOffer(previous)));
  }

  private BiddingAction choose(String hand, Bid previous) {
    BiddingState state = state(previous);
    return policy.choose(
        new BiddingView(state.currentSeat().orElseThrow(), CardParser.parseList(hand), 40,
            state));
  }

  private static BidAssessment assessment(String hand, Trump trump) {
    return new BiddingHandEvaluator().assess(BiddingHand.from(CardParser.parseList(hand))).stream()
        .filter(candidate -> candidate.trump() == trump).findFirst().orElseThrow();
  }

  @ParameterizedTest
  @MethodSource("examples")
  @DisplayName("제시된 10개 패의 첫 입찰과 후보별 상한을 비교한다")
  void opensAndAssessesExamples(Example example) {
    assertEquals(new BidOffer(new Bid(example.opening(), example.selectedTrump())),
        choose(example.hand(), null));
    BidAssessment assessment = assessment(example.hand(), example.trump());
    assertEquals(example.voluntary(), assessment.canCompete());
    assertEquals(example.ceiling(), assessment.ceilingPoints());
  }

  @ParameterizedTest
  @MethodSource("examples")
  @DisplayName("강제 첫 입찰과 자발적 경쟁을 구분하고 상한을 넘지 않는다")
  void competesOnlyWhenSupported(Example example) {
    BiddingAction afterLowBid = choose(example.hand(), new Bid(13, Trump.DIAMOND));
    assertEquals(example.voluntary() ? new BidOffer(new Bid(example.opening(), example.selectedTrump()))
            : BidPass.INSTANCE,
        afterLowBid);
    assertEquals(BidPass.INSTANCE,
        choose(example.hand(), new Bid(example.ceiling(), example.trump())));
    if (example.voluntary() && example.ceiling() > example.opening()) {
      assertEquals(new BidOffer(new Bid(example.ceiling(), example.trump())),
          choose(example.hand(), new Bid(example.ceiling() - 1, example.trump())));
    }
  }

  @Test
  @DisplayName("사이드 A가 없어도 삽 K 초구는 인정하고 독립 회수가 사라진 만큼 경쟁 상한을 낮춘다")
  void losingSideEntryChangesViability() {
    String original = "SK S9 S5 DK DJ D7 D2 CA CQ CJ";
    String changed = original.replace("CA", "C8");
    assertTrue(assessment(original, Trump.DIAMOND).canCompete());
    assertTrue(assessment(changed, Trump.DIAMOND).canCompete());
    assertEquals(16, assessment(original, Trump.DIAMOND).ceilingPoints());
    assertEquals(15, assessment(changed, Trump.DIAMOND).ceilingPoints());
    assertEquals(new BidOffer(new Bid(15, Trump.DIAMOND)),
        choose(changed, new Bid(13, Trump.DIAMOND)));
    assertFalse(assessment(changed.replace("SK", "SQ"), Trump.DIAMOND).canCompete());
  }

  @Test
  @DisplayName("조커를 제거하면 낮은 네 장 기루를 같은 수준으로 평가하지 않는다")
  void removingJokerChangesPlan() {
    String original = "D9 D8 D3 D2 HA HK H3 C9 C6 JK";
    assertFalse(assessment(original.replace("JK", "C5"), Trump.DIAMOND).canCompete());
  }

  @Test
  @DisplayName("하트 A 회수가 생기면 패스하던 JT 기루의 경쟁 가능성이 달라진다")
  void addingEntryChangesPassDecision() {
    String changed = "SJ S10 S9 S5 D10 D5 HA H5 C10 C4";
    assertTrue(assessment(changed, Trump.SPADE).canCompete());
    assertEquals(new BidOffer(new Bid(15, Trump.SPADE)),
        choose(changed, new Bid(13, Trump.DIAMOND)));
  }

  @Test
  @DisplayName("마이티를 잃으면 낮은 J 기루의 기존 16 상한을 복사하지 않는다")
  void movingMightyChangesSuitPlan() {
    String changed = "SK SJ S8 S3 DJ D5 D4 D2 H10 C4";
    assertTrue(assessment(changed, Trump.DIAMOND).canCompete());
    assertEquals(15, assessment(changed, Trump.DIAMOND).ceilingPoints());
    assertFalse(assessment(changed.replace("SK", "SQ"), Trump.DIAMOND).canCompete());
  }

  @Test
  @DisplayName("다리와 하트를 바꾼 새 패도 같은 운영 자원으로 판단한다")
  void generalizesAcrossSuits() {
    String changed = "SK S9 S5 HK HJ H7 H2 CA CQ CJ";
    assertEquals(new BidOffer(new Bid(15, Trump.HEART)), choose(changed, null));
    assertEquals(16, assessment(changed, Trump.HEART).ceilingPoints());
  }

  @Test
  @DisplayName("낮은 카드 숫자가 바뀌어도 같은 운영 구조의 평가를 유지한다")
  void generalizesAcrossLowRanks() {
    String changed = "SK S8 S4 DK DJ D6 D3 CA CQ CJ";
    assertEquals(new BidOffer(new Bid(15, Trump.DIAMOND)), choose(changed, null));
    assertEquals(16, assessment(changed, Trump.DIAMOND).ceilingPoints());
  }

  @Test
  @DisplayName("A1과 A14의 조커 또는 마이티를 가진 긴 기루 경쟁 여력을 유지한다")
  void preservesEarlierTrumpLessons() {
    assertEquals(16, assessment("S8 S3 DK D6 HK HQ H10 H7 C5 JK", Trump.HEART).ceilingPoints());
    assertEquals(17, assessment("SA S2 H7 H9 C10 C8 C6 C5 C3 C2", Trump.CLUB).ceilingPoints());
  }

  @Test
  @DisplayName("A26과 A18은 서로 다른 풀노 운영 구조로 평가한다")
  void recognizesDistinctFullNoTrumpResources() {
    assertEquals(new BidOffer(new Bid(20, Trump.NO_TRUMP)),
        choose("SK SQ SJ S7 S6 S2 DA CA C2 JK", null));
    assertEquals(new BidOffer(new Bid(20, Trump.NO_TRUMP)),
        choose("SA SK SQ HA HK HQ HJ H10 H6 JK", null));
  }

  @Test
  @DisplayName("추가 A만으로 무마·무조·무총 패를 노기루로 허용하지 않는다")
  void distinguishesIndependentReturns() {
    String original = "SK SQ SJ S10 S7 DA DK D4 H9 C8";
    assertFalse(assessment(original, Trump.NO_TRUMP).canCompete());
    assertEquals(17, assessment(original, Trump.SPADE).ceilingPoints());
    assertFalse(assessment(original.replace("H9", "HA"), Trump.NO_TRUMP).canCompete());
  }

}
