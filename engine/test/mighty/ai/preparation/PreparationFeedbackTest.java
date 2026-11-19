package mighty.ai.preparation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.stream.Stream;
import mighty.ai.bidding.*;
import mighty.data.CardParser;
import mighty.game.*;
import mighty.game.bidding.*;
import mighty.game.friend.CardFriend;
import mighty.game.observation.*;
import mighty.model.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class PreparationFeedbackTest {

  private static List<Card> cards(String s) {
    return CardParser.parseList(s);
  }

  private static BidAssessment assess(String s, Trump trump) {
    return new BiddingHandEvaluator().assess(BiddingHand.from(cards(s))).stream()
        .filter(a -> a.trump() == trump).findFirst().orElseThrow();
  }

  static Stream<Arguments> noTrumpFailures() {
    return Stream.of(
        Arguments.of("SA S3 S5 S7 S9 D5 D6 D8 C2 C5"),
        Arguments.of("SK SQ S10 S8 S6 S5 S4 S3 DA CA"),
        Arguments.of("SK SJ S7 S6 DA DK HA HK CA CK"),
        Arguments.of("D2 D4 D8 D9 DJ DQ DK CA CK C3"));
  }

  @ParameterizedTest
  @MethodSource("noTrumpFailures")
  @DisplayName("긴 숫자·사이드 AK·총만으로 노기루를 허용하지 않는다")
  void rejectsBrokenNoTrumpRoutes(String owned) {
    assertFalse(assess(owned, Trump.NO_TRUMP).canCompete());
  }

  @Test
  @DisplayName("긴 QJ 다리와 조커·별도 A는 곱노 운영 후보가 된다")
  void longJokerRoute() {
    var a = assess("D3 D6 D7 D8 D9 DJ DQ C6 CA JK", Trump.NO_TRUMP);
    assertTrue(a.canCompete());
    assertEquals(17, a.ceilingPoints());
  }

  @Test
  @DisplayName("주력 A 하나가 없으면 곱다리, 있으면 총과 연결한 풀노로 달라진다")
  void missingPrimaryAceChangesContract() {
    String original = "D2 D4 D8 D9 DJ DQ DK CA CK C3";
    assertEquals(17, assess(original, Trump.DIAMOND).ceilingPoints());
    var repaired = assess(original.replace("D2", "DA"), Trump.NO_TRUMP);
    assertTrue(repaired.canCompete());
    assertEquals(20, repaired.ceilingPoints());
  }

  @Test
  @DisplayName("여덟 삽과 다리 마이티는 엿삽 진입·풀삽 경쟁 한도를 갖는다")
  void eightSpadesAreNotAutomaticallyNoTrump() {
    var a = assess("SK SQ S10 S8 S6 S5 S4 S3 DA CA", Trump.SPADE);
    assertTrue(a.canCompete());
    assertEquals(16, a.openingPoints());
    assertEquals(20, a.ceilingPoints());
  }

  @Test
  @DisplayName("긴 삽에 총이 생기면 조커의 시점을 통제하는 풀노 후보가 된다")
  void gunChangesJokerTiming() {
    var a = assess("SK SQ S10 S8 S6 S5 S4 S3 CA C3", Trump.NO_TRUMP);
    assertTrue(a.canCompete());
    assertEquals(20, a.ceilingPoints());
  }

  @Test
  @DisplayName("강한 AK 사이드라도 삽 네 장의 노기루와 풀삽 한도는 구분한다")
  void strongHandStillNeedsTrump() {
    var a = assess("SK SJ S7 S6 DA DK HA HK CA CK", Trump.SPADE);
    assertEquals(16, a.openingPoints());
    assertEquals(20, a.ceilingPoints());
  }

  @Test
  @DisplayName("숫자들이 A 아래 있는 문양보다 K를 보호하는 부기루를 남긴다")
  void keepsProtectedSideKing() {
    var view = new DiscardView(new Seat(0), new Bid(16, Trump.HEART),
        cards("H2 H3 H4 H5 H6 H7 CA C2 C3 C4 DK D5 D8"));
    assertEquals(Set.copyOf(cards("C2 C3 C4")), Set.copyOf(new RuleDiscardPolicy().choose(view)));
  }

  @Test
  @DisplayName("기루 여섯 장의 숫자보다 KQ 연결을 점수 묻기로 파괴하지 않는다")
  void keepsKingQueenConnection() {
    var view = new DiscardView(new Seat(0), new Bid(16, Trump.HEART),
        cards("H5 H7 H8 H10 HJ HQ DA DK C4 C6 CQ CK JK"));
    var discarded = new RuleDiscardPolicy().choose(view);
    assertFalse(discarded.contains(CardParser.parse("CK")));
    assertFalse(discarded.contains(CardParser.parse("CQ")));
  }

  @Test
  @DisplayName("강한 다리 패의 물패를 묻되 빵을 남기고 잘못된 노기루로 바꾸지 않는다")
  void discardAndContractAgree() {
    var view = new DiscardView(new Seat(0), new Bid(17, Trump.DIAMOND),
        cards("D2 D4 D8 D9 DJ DQ DK H2 H4 C3 C8 CK CA"));
    var plan = new PreparationPlanner().choose(view);
    assertEquals(new Bid(17, Trump.DIAMOND), plan.contract());
    assertEquals(Set.copyOf(cards("H2 H4 C8")), Set.copyOf(plan.discarded()));
    assertEquals(new CardFriend(CardParser.parse("SA")), plan.friend());
  }

  @Test
  @DisplayName("바닥 뒤 긴 다리·조커가 완성되면 합법적인 곱노 변경을 한다")
  void revisesAfterKitty() {
    var view = new DiscardView(new Seat(0), new Bid(16, Trump.DIAMOND),
        cards("D3 D6 D7 D8 D9 DJ DQ C6 CA JK H2 H4 S2"));
    var plan = new PreparationPlanner().choose(view);
    assertEquals(new Bid(17, Trump.NO_TRUMP), plan.contract());
    assertTrue(ContractChangeRules.canChange(view.contract(), view.contract(), plan.contract(),
        GamePhase.DISCARD));
    var kept = new ArrayList<>(view.hand());
    kept.removeAll(plan.discarded());
    assertEquals(plan.contract(), new PreparationPlanner().finalContract(
        new FriendView(view.self(), view.contract(), kept, plan.discarded()), view.contract()));
  }

  @Test
  @DisplayName("변경 비용이 상한을 넘으면 바닥 뒤 문양을 억지로 바꾸지 않는다")
  void respectsRevisionCost() {
    var view = new FriendView(new Seat(0), new Bid(17, Trump.DIAMOND),
        cards("D3 D6 D7 D8 D9 DJ DQ C6 CA JK"), cards("H2 H4 S2"));
    assertEquals(view.contract(), new PreparationPlanner().finalContract(view, view.contract()));
  }

  @Test
  @DisplayName("삽 기루에서 자기 다리 마이티 아래 K를 잘못 묻지 않는다")
  void preservesKingBelowOwnedMighty() {
    var view = new DiscardView(new Seat(0), new Bid(17, Trump.SPADE),
        cards("SK SJ S7 S6 DA DK D8 HA HK H9 H8 CA CK"));
    var plan = new PreparationPlanner().choose(view);
    assertEquals(new Bid(17, Trump.SPADE), plan.contract());
    assertEquals(Set.copyOf(cards("D8 H9 H8")), Set.copyOf(plan.discarded()));
    assertEquals(new CardFriend(CardParser.parse("JK")), plan.friend());
  }

  @Test
  @DisplayName("주력 A를 보강한 총 보유 패는 버리기 뒤 풀노로 변경한다")
  void finalFullNoTrumpUsesBothMultipliers() {
    var view = new DiscardView(new Seat(0), new Bid(17, Trump.DIAMOND),
        cards("DA D4 D8 D9 DJ DQ DK H2 H4 C3 C8 CK CA"));
    var plan = new PreparationPlanner().choose(view);
    assertEquals(new Bid(20, Trump.NO_TRUMP), plan.contract());
    assertEquals(Set.copyOf(cards("H2 H4 C8")), Set.copyOf(plan.discarded()));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "D", "H"
  })
  @DisplayName("주력 A 단절 판단은 다리뿐 아니라 하트에도 동일하게 적용한다")
  void primaryGapIsNotDiamondSpecific(String suit) {
    String original = String.join(" ",
        List.of("2", "4", "8", "9", "J", "Q", "K").stream().map(n -> suit + n).toList())
        + " CA CK C3";
    Trump trump = suit.equals("D") ? Trump.DIAMOND : Trump.HEART;
    assertFalse(assess(original, Trump.NO_TRUMP).canCompete());
    assertEquals(17, assess(original, trump).ceilingPoints());
    assertEquals(20,
        assess(original.replace(suit + "2", suit + "A"), Trump.NO_TRUMP).ceilingPoints());
  }

  @Test
  @DisplayName("풀노 직행 낙찰도 풀삽·풀노 공개 선언으로 반환 문양을 알려준다")
  void fullNoTrumpSignalsPrimarySuit() {
    var view = new FriendView(new Seat(0), new Bid(20, Trump.NO_TRUMP),
        cards("SK SQ S10 S8 S6 S5 S4 S3 CA C3"), cards("D2 H2 H3"));
    var planner = new PreparationPlanner();
    var path = planner.declarations(view, view.contract(), List.of(view.contract()));
    assertEquals(List.of(new Bid(20, Trump.SPADE), view.contract()), path);
    assertTrue(planner.declarations(view, view.contract(),
        List.of(view.contract(), path.get(0), path.get(1))).isEmpty());
  }

  @Test
  @DisplayName("AKQJ 여섯 삽과 마이티는 물패 세 장이어도 곱노 후보로 평가한다")
  void solidSpadesUseJokerFriendForWater() {
    var a = assess("S3 S6 SJ SQ SK SA DA H2 C2 D2", Trump.NO_TRUMP);
    assertTrue(a.canCompete());
    assertEquals(17, a.ceilingPoints());
    assertEquals(17, a.openingPoints());
  }

  @Test
  @DisplayName("마쪼의 AQ 문양은 K 프렌드로 연결할 수 있다")
  void flexibleSpecialsCanCompleteAceQueen() {
    var view = new FriendView(new Seat(0), new Bid(15, Trump.NO_TRUMP),
        cards("S6 SA DK H3 HQ HA C5 C6 C10 JK"), cards("D2 H2 H4"));
    assertEquals(new CardFriend(CardParser.parse("HK")), new RuleFriendPolicy().choose(view));
  }

  @Test
  @DisplayName("닷노 진입 후 실제 바닥 삽 보강이 생기면 합법 곱삽으로 재평가한다")
  void flexibleOpeningReevaluatesRealKitty() {
    var view = new DiscardView(new Seat(0), new Bid(15, Trump.NO_TRUMP),
        cards("S6 SA DK H3 HQ HA C5 C6 C10 JK S2 S3 S4"));
    var plan = new PreparationPlanner().choose(view);
    assertEquals(new Bid(17, Trump.SPADE), plan.contract());
    assertEquals(new CardFriend(CardParser.parse("DA")), plan.friend());
  }

  @Test
  @DisplayName("AKJ 다섯 다리·조커·전 문양 회수는 마이티 프렌드 풀노로 평가한다")
  void completeFiveCardRunWithFriendIsFullNoTrump() {
    String owned = "SK D4 D7 DJ DK DA HA CA CK JK";
    var a = assess(owned, Trump.NO_TRUMP);
    assertTrue(a.canCompete());
    assertEquals(20, a.openingPoints());
    assertEquals(20, a.ceilingPoints());
    assertFalse(assess(owned.replace("JK", "H2"), Trump.NO_TRUMP).canCompete());
    assertEquals(20, assess(owned.replace("HA", "H2"), Trump.NO_TRUMP).ceilingPoints());
    assertEquals(17,
        assess(owned.replace("HA", "H2").replace("CK", "H3"), Trump.NO_TRUMP).ceilingPoints());
  }

  @Test
  @DisplayName("아홉 장 주력과 마쪼는 K 고정 패턴에 없어도 풀노로 평가한다")
  void dominantNineCardsAreNotBlockedByMissingKingTemplate() {
    var a = assess("SA SQ SJ S10 S9 S8 S7 S6 S5 JK", Trump.NO_TRUMP);
    assertTrue(a.canCompete());
    assertEquals(20, a.ceilingPoints());
  }

  @Test
  void upgradedFullNoTrumpSignalsClubsForFirstTrickFriend() {
    var award = new Bid(15, Trump.NO_TRUMP);
    var view = new FriendView(new Seat(0), award,
        cards("SA H8 HK HA C2 C8 C9 CJ CA JK"), cards("S5 S8 S10"));
    assertEquals(List.of(new Bid(20, Trump.CLUB), new Bid(20, Trump.NO_TRUMP)),
        new PreparationPlanner().declarations(view, award, List.of(award)));
  }

  @Test
  void keepsHeartAceQueenJackWhenBuryingForClubs() {
    var hand = cards("S7 SA D2 H8 HJ HQ HA C2 C7 C8 C10 CQ CK");
    var view = new DiscardView(new Seat(0), new Bid(16, Trump.CLUB), hand);
    assertEquals(Set.copyOf(cards("S7 D2 H8")), Set.copyOf(new RuleDiscardPolicy().choose(view)));
  }

  @Test
  void comparesJokerFriendForLongDiamondsAndSoloForSelfControlledHand() {
    var award = new Bid(16, Trump.DIAMOND);
    var hand = cards("S6 S8 SA D3 D8 D9 D10 DJ DK DA");
    var buried = cards("C2 H2 H3");
    var planner = new PreparationPlanner();
    var finalBid = planner.finalContract(new FriendView(new Seat(0), award, hand, buried), award);
    assertEquals(new Bid(17, Trump.NO_TRUMP), finalBid);
    assertEquals(new CardFriend(Joker.INSTANCE),
        new RuleFriendPolicy().choose(new FriendView(new Seat(0), finalBid, hand, buried)));
    var solo = new FriendView(new Seat(0), new Bid(20, Trump.NO_TRUMP),
        cards("SA D3 D7 D8 D9 DJ DK DA HA JK"), buried);
    assertEquals(mighty.game.friend.SpecialFriend.NONE, new RuleFriendPolicy().choose(solo));
  }

}
