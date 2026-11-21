package mighty.ai.play;

import static org.junit.jupiter.api.Assertions.*;
import static mighty.ai.play.PlayTestFixture.*;

import java.util.*;
import java.util.stream.IntStream;
import mighty.data.CardParser;
import mighty.game.*;
import mighty.game.action.*;
import mighty.game.bidding.Bid;
import mighty.game.deal.DeckFactory;
import mighty.game.friend.*;
import mighty.game.observation.*;
import mighty.game.rule.Rules;
import mighty.game.trick.*;
import mighty.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OperatingPlanTest {

  @org.junit.jupiter.params.ParameterizedTest
  @org.junit.jupiter.params.provider.CsvSource({"SA,DA", "SK,DA SA", "SQ,DA SA SK"})
  void oppositionUsesExpiringJokerBeforeRuffingEvenWhenAllyLeads(String top, String spent) {
    String owned = top + " JK";
    var current = List.of(p(0, "CJ"), p(1, "CA"));
    var history = countedHistory(owned, "", spent, "S2 S3 S4", current, null);
    View v = view(2, 0, Trump.SPADE, 16, owned, "", SpecialFriend.FIRST_TRICK,
        new RevealedFriend(new Seat(4)), history, current);
    var facts = new PlayFacts(v);
    assertTrue(facts.allyWinning());
    assertTrue(facts.cards().publicHistory().cards().contains(card("DA")));
    assertTrue(facts.isSuitTop(card(top)));
    assertEquals(Joker.INSTANCE, policy.choose(v).card());
  }

  private final RulePlayPolicy policy = new RulePlayPolicy();

  @Test
  @DisplayName("친구 마이티에 점수 지원을 이유로 기루 A를 버리지 않는다")
  void preservesTrumpAceOnFriendMighty() {
    var history = List.of(
        row(1, Trump.SPADE, p(0, "C2"), p(1, "CA"), p(2, "C3"), p(3, "C4"), p(4, "C5")));
    View v = view(0, 0, Trump.SPADE, 16, "SA S2 C6 C7 C8 H2 H3 H4 H5", "D8 D9 D10",
        new CardFriend(card("DA")), new RevealedFriend(new Seat(1)), history,
        List.of(p(1, "DA"), p(2, "D4"), p(3, "D5"), p(4, "D6")));
    assertNotEquals(card("SA"), policy.choose(v).card());
    assertEquals(0, policy.choose(v).card().points());
  }

  @Test
  @DisplayName("주공 마이티에 프렌드도 기루 A를 보존한다")
  void friendUsesSameRecoveryProtection() {
    View v = view(1, 0, Trump.SPADE, 16, "SA S2 C6 C7 C8 H2 H3 H4 H5 H6", "D8 D9 D10",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(1)), List.of(),
        List.of(p(0, "DA")));
    assertNotEquals(card("SA"), policy.choose(v).card());
    assertEquals(0, policy.choose(v).card().points());
  }

  @Test
  @DisplayName("A가 빠진 친구 Q에 K 대신 낮은 같은 문양을 낸다")
  void preservesKingOnFriendQueen() {
    var history = List.of(
        row(1, Trump.NO_TRUMP, p(0, "C2"), p(1, "CA"), p(2, "C3"), p(3, "C4"), p(4, "C5")));
    View v = view(0, 0, Trump.NO_TRUMP, 16, "CK C9 S2 S3 S4 D2 D3 H2 H3", "D8 D9 D10",
        new CardFriend(card("CA")), new RevealedFriend(new Seat(1)), history,
        List.of(p(1, "CQ"), p(2, "C6"), p(3, "C7"), p(4, "C8")));
    assertEquals(card("C9"), policy.choose(v).card());
  }

  @Test
  @DisplayName("합법적으로 K만 따라낼 수 있으면 보존 규칙이 불법 버리기를 만들지 않는다")
  void obeysForcedKingFollow() {
    var history = List.of(
        row(1, Trump.NO_TRUMP, p(0, "C2"), p(1, "CA"), p(2, "C3"), p(3, "C4"), p(4, "C5")));
    View v = view(0, 0, Trump.NO_TRUMP, 16, "CK S2 S3 S4 D2 D3 H2 H3 H4", "D8 D9 D10",
        new CardFriend(card("CA")), new RevealedFriend(new Seat(1)), history,
        List.of(p(1, "CQ"), p(2, "C6"),
            p(3, "C7"), p(4, "C8")));
    assertEquals(card("CK"), policy.choose(v).card());
  }

  @Test
  @DisplayName("마이티와 조커가 모두 이길 때 딸마를 먼저 쓰고 조커 선택권을 남긴다")
  void usesSingletonMightyBeforeJokerToCapture() {
    var history = List.of(
        row(1, Trump.DIAMOND, p(0, "CA"), p(1, "C4"), p(2, "C5"), p(3, "C6"), p(4, "C7")));
    View v = view(2, 0, Trump.DIAMOND, 16, "SA JK H3 H4 D2 D3 D4 C2 C3", "D8 D9 D10",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(2)), history,
        List.of(p(0, "H2"), p(1, "HA")));
    assertEquals(card("SA"), policy.choose(v).card());
  }

  @Test
  @DisplayName("마쪼의 기루 운영은 마이티 선취 후 조커 기루 지정으로 연결한다")
  void mightyThenJokerLeadSequence() {
    View first = view(0, 0, Trump.DIAMOND, 16, "SA JK DQ DJ D7 D5 D3 CK C6 H2", "C2 C3 C4",
        SpecialFriend.NONE, NoFriend.INSTANCE, List.of(), List.of());
    assertEquals(card("SA"), policy.choose(first).card());
    var history = List.of(
        row(1, Trump.DIAMOND, p(0, "SA"), p(1, "S2"), p(2, "S3"), p(3, "S4"), p(4, "S5")));
    View next = view(0, 0, Trump.DIAMOND, 16, "JK DQ DJ D7 D5 D3 CK C6 H2", "C2 C3 C4",
        SpecialFriend.NONE, NoFriend.INSTANCE, history, List.of());
    assertEquals(new JokerLead(Suit.DIAMOND), policy.choose(next));
  }

  @Test
  @DisplayName("야당 기루 소진 뒤에도 회수용 기루를 남기고 복수 물패를 먼저 처리한다")
  void shedsEarlyBeforeBurningRuffEntries() {
    String owned = "SA SK S7 C2 C6 H2 H3", buried = "D8 D9 D10";
    var history = countedHistory(owned, buried, "DA S2 S3 S4 S5 S6 S8 S9 S10 SJ SQ", "JK",
        List.of(), null);
    View v = view(0, 0, Trump.SPADE, 16, owned, buried, SpecialFriend.NONE, NoFriend.INSTANCE,
        history, List.of());
    assertEquals(card("C2"), policy.choose(v).card());
    assertTrue(policy.recommend(v).reason().contains("기루를 남기고"));
  }

  @Test
  @DisplayName("A 소모 후 Q와 낮은 클럽은 물패 버리기가 아닌 K 제거 계획으로 사용한다")
  void developsQueenInsteadOfThrowingItsGuard() {
    String owned = "SA SK S7 CQ C6 H2 H3", buried = "D8 D9 D10";
    var history = countedHistory(owned, buried, "DA CA S2 S3 S4 S5 S6 S8 S9 S10 SJ SQ", "JK CK",
        List.of(),
        null);
    View v = view(0, 0, Trump.SPADE, 16, owned, buried, SpecialFriend.NONE, NoFriend.INSTANCE,
        history, List.of());
    assertEquals(card("C6"), policy.choose(v).card());
    assertTrue(policy.recommend(v).reason().contains("부기루"));
  }

  private View lossPosition(int contract, String owned) {
    String buried = "D8 D9 D10";
    var current = List.of(p(1, "CK"), p(2, "CQ"), p(3, "C3"), p(4, "C4"));
    int number = 10 - CardParser.parseList(owned).size();
    PublicTrick lost = row(number, Trump.SPADE, p(0, "D2"), p(1, "DA"), p(2, "DJ"), p(3, "D4"),
        p(4, "D5"));
    var history = countedHistory(owned, buried, "S2 JK S3 S4 S5 S6 S7 S8 S9 S10 SJ SQ CA", "HA",
        current,
        lost);
    return view(0, 0, Trump.SPADE, contract, owned, buried, SpecialFriend.NONE, NoFriend.INSTANCE,
        history,
        current);
  }

  @Test
  @DisplayName("엿에서 이미 2점 손실이고 마지막 자리 2점이면 물패를 흘리고 남은 승리를 확보한다")
  void shedsAtLastSeatWithinContractBudget() {
    View v = lossPosition(16, "SA SK H2");
    assertEquals(card("H2"), policy.choose(v).card());
    assertTrue(policy.recommend(v).reason().contains("손실 2점"));
  }

  @Test
  @DisplayName("곱에서 같은 2점은 허용 손실을 넘으므로 간친다")
  void ruffsWhenSameLossBreaksContract() {
    View v = lossPosition(17, "SA SK H2");
    assertNotEquals(card("H2"), policy.choose(v).card());
  }

  @Test
  @DisplayName("내가 버릴 카드의 점수도 손실에 포함한다")
  void includesOwnDiscardedPointInLoss() {
    View v = lossPosition(16, "SA SK H10");
    assertNotEquals(card("H10"), policy.choose(v).card());
  }

  @Test
  @DisplayName("유일한 일반 물패는 나머지 확정 승리를 돌린 뒤 마지막으로 미룬다")
  void leavesSingleWaterUntilEnd() {
    String owned = "SA SK H2", buried = "D8 D9 D10";
    var history = countedHistory(owned, buried, "S2 JK DA S3 S4 S5 S6 S7 S8 S9 S10 SJ SQ", "HA",
        List.of(),
        null);
    View v = view(0, 0, Trump.SPADE, 17, owned, buried, SpecialFriend.NONE, NoFriend.INSTANCE,
        history, List.of());
    assertEquals(card("SA"), policy.choose(v).card());
    assertTrue(policy.recommend(v).reason().contains("유일한 물패"));
  }

  @Test
  @DisplayName("조커만 외부 위협이면 점수 기루로 먼저 유도하고 거절되면 낮은 기루를 쓴다")
  void invitesOnlyJokerThreatAndKeepsLastPoint() {
    var history = List.of(
        row(1, Trump.SPADE, p(0, "DA"), p(1, "D2"), p(2, "D3"), p(3, "D4"), p(4, "D5")),
        row(2, Trump.SPADE, p(0, "SA"), p(1, "S10"), p(2, "S4"), p(3, "S3"), p(4, "S2")),
        row(3, Trump.SPADE, p(0, "SK"), p(1, "C2"), p(2, "D6"), p(3, "H2"), p(4, "C3")));
    View v = view(0, 0, Trump.SPADE, 17, "SQ SJ S9 S8 S7 S6 S5", "D8 D9 D10",
        SpecialFriend.NONE, NoFriend.INSTANCE, history, List.of());
    // 직전 K도 모든 야당이 비기루인 상황에 조커를 유도한 카드입니다.
    assertEquals(card("S5"), policy.choose(v).card());
    assertTrue(policy.recommend(v).reason().contains("10구"));
  }

  @Test
  @DisplayName("9구에도 외부 조커를 못 뺐으면 마지막 점수 기루는 10구에 남긴다")
  void keepsPointTrumpForTenthWhenJokerDeclines() {
    String owned = "SJ S5", buried = "D8 D9 D10";
    var history = countedHistory(owned, buried, "DA S2 S3 S4 S6 S7 S8 S9 S10 SQ SK SA", "JK",
        List.of(), null);
    View v = view(0, 0, Trump.SPADE, 17, owned, buried, SpecialFriend.NONE, NoFriend.INSTANCE,
        history, List.of());
    assertEquals(card("S5"), policy.choose(v).card());
  }

  @Test
  @DisplayName("조커 유도를 아직 하지 않은 순회에서는 Q를 먼저 제시하고 다음에는 J를 남긴다")
  void baitsOnceThenPreservesLastPoint() {
    var first = row(1, Trump.SPADE, p(0, "DA"), p(1, "D2"), p(2, "D3"), p(3, "D4"), p(4, "D5"));
    var second = row(2, Trump.SPADE, p(0, "SA"), p(1, "SK"), p(2, "S10"), p(3, "S3"), p(4, "S2"));
    var third = row(3, Trump.SPADE, p(0, "S6"), p(1, "C2"), p(2, "D6"), p(3, "H2"), p(4, "C3"));
    var history = List.of(first, second, third);
    View before = view(0, 0, Trump.SPADE, 17, "SQ SJ S9 S8 S7 S5 S4", "D8 D9 D10",
        SpecialFriend.NONE, NoFriend.INSTANCE, history, List.of());
    assertEquals(card("SQ"), policy.choose(before).card());
    var fourth = row(4, Trump.SPADE, p(0, "SQ"), p(1, "C4"), p(2, "D7"), p(3, "H3"), p(4, "C5"));
    View after = view(0, 0, Trump.SPADE, 17, "SJ S9 S8 S7 S5 S4", "D8 D9 D10",
        SpecialFriend.NONE, NoFriend.INSTANCE, List.of(first, second, third, fourth), List.of());
    assertEquals(card("S4"), policy.choose(after).card());
  }

  @Test
  @DisplayName("점수가 적어도 Q를 키울 전개 카드가 있으면 간으로 선을 확보한다")
  void capturesSmallTrickToPreserveDevelopmentPlan() {
    String owned = "SA SK CQ C6 H2", buried = "D8 D9 D10";
    var current = List.of(p(1, "DK"), p(2, "DQ"), p(3, "D3"), p(4, "D4"));
    var last = row(5, Trump.SPADE, p(0, "D2"), p(1, "DA"), p(2, "DJ"), p(3, "D5"), p(4, "D6"));
    var history = countedHistory(owned, buried, "S2 JK S3 S4 S5 S6 S7 S8 S9 S10 SJ SQ CA", "HA CK",
        current,
        last);
    View v = view(0, 0, Trump.SPADE, 16, owned, buried, SpecialFriend.NONE, NoFriend.INSTANCE,
        history, current);
    assertEquals(card("SK"), policy.choose(v).card());
    assertTrue(policy.recommend(v).reason().contains("부기루 전개"));
  }

  @Test
  @DisplayName("K탑 네 장 부기루를 기루로 회수하며 육성한다")
  void developsFourCardKingSide() {
    String owned = "SA SK S7 CK C9 C6 C2", buried = "D8 D9 D10";
    var history = countedHistory(owned, buried, "DA S2 S3 S4 S5 S6 S8 S9 S10 SJ SQ", "JK CA",
        List.of(), null);
    View v = view(0, 0, Trump.SPADE, 17, owned, buried, SpecialFriend.NONE, NoFriend.INSTANCE,
        history, List.of());
    assertEquals(card("C2"), policy.choose(v).card());
  }

  @Test
  @DisplayName("확인된 NT 초구 Q 반환 신호는 마쪼 우선 규칙으로 덮어쓰지 않는다")
  void preservesNoTrumpOpeningSignal() {
    View v = view(0, 0, Trump.NO_TRUMP, 20, "SA SK SQ HA DK DQ D9 D5 D4 JK", "H2 H3 H4",
        new CardFriend(card("DA")), UnrevealedFriend.INSTANCE, List.of(), List.of());
    assertEquals(card("SQ"), policy.choose(v).card());
  }

  @Test
  @DisplayName("마쪼라도 9구는 조커 사용 기한을 우선한다")
  void expiryOverridesMightyFirst() {
    String owned = "DA JK", buried = "D8 D9 D10";
    var history = countedHistory(owned, buried, "", "", List.of(), null);
    View v = view(0, 0, Trump.SPADE, 17, owned, buried, SpecialFriend.NONE, NoFriend.INSTANCE,
        history, List.of());
    assertInstanceOf(JokerLead.class, policy.choose(v));
  }

  @Test
  @DisplayName("회수 A는 보존하면서 살릴 길이 없는 다른 점수는 확보된 승리에 지원한다")
  void feedsExpendablePointWithoutSacrificingTrumpAce() {
    var history = List.of(
        row(1, Trump.SPADE, p(0, "C2"), p(1, "CA"), p(2, "C3"), p(3, "C4"), p(4, "C5")));
    View v = view(0, 0, Trump.SPADE, 16, "SA S2 CQ H2 H3 H4 H5 H6 H7", "D8 D9 D10",
        new CardFriend(card("DA")), new RevealedFriend(new Seat(1)), history,
        List.of(p(1, "DA"), p(2, "D4"), p(3, "D5"), p(4, "D6")));
    assertEquals(card("CQ"), policy.choose(v).card());
  }

  @Test
  @DisplayName("주공의 간은 뒤의 미출현 조커 때문에 마이티로 덮지 않는다")
  void friendProtectsDeclarerRuffWithUnseenJoker() {
    var prior = row(1, Trump.SPADE, p(0, "C2"), p(1, "C3"), p(2, "C4"), p(3, "CA"), p(4, "C5"));
    View v = view(1, 0, Trump.SPADE, 16, "DA JK S3 H2 H3 D6 C6 C7 C8", "D8 D9 D10",
        new CardFriend(card("DA")), new RevealedFriend(new Seat(1)), List.of(prior),
        List.of(p(3, "D2"), p(4,
            "D3"), p(0, "S2")));
    assertFalse(new PlayFacts(v).isSecured(new PlayFacts(v).currentWinner().orElseThrow()));
    assertNotEquals(card("DA"), policy.choose(v).card());
    assertNotEquals(card("JK"), policy.choose(v).card());
  }

  @Test
  @DisplayName("프렌드의 간도 주공이 마이티로 덮지 않는다")
  void declarerProtectsFriendRuff() {
    var prior = row(1, Trump.SPADE, p(0, "C2"), p(1, "CA"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    View v = view(0, 0, Trump.SPADE, 16, "DA JK S3 H2 H3 D6 C6 C7 C8", "D8 D9 D10",
        new CardFriend(card("S2")), new RevealedFriend(new Seat(2)), List.of(prior),
        List.of(p(1, "D2"), p(2, "S2"), p(3, "D3"), p(4, "D4")));
    assertNotEquals(card("DA"), policy.choose(v).card());
    assertNotEquals(card("JK"), policy.choose(v).card());
  }

  @Test
  @DisplayName("야당이 아군의 간을 실제로 덮은 뒤에는 마이티로 회수한다")
  void capturesActualEnemyOverruff() {
    var prior = row(1, Trump.SPADE, p(0, "C2"), p(1, "CA"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    View v = view(0, 0, Trump.SPADE, 16, "DA JK S3 H2 H3 H4 C6 C7 C8", "D8 D9 D10",
        new CardFriend(card("S2")), new RevealedFriend(new Seat(2)), List.of(prior),
        List.of(p(1, "D2"), p(2, "S2"), p(3, "SA"), p(4, "D4")));
    assertEquals(card("DA"), policy.choose(v).card());
  }

  @Test
  @DisplayName("내 AK 때문에 외부가 이길 수 없는 주공 Q는 낮게 따라 지원한다")
  void recognizesDeclarerQueenBelowOwnAceKing() {
    View v = view(1, 0, Trump.DIAMOND, 16, "SA HA HK H2 D2 D3 D4 C2 C3 C4", "D8 D9 D10",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(1)), List.of(),
        List.of(p(0, "HQ")));
    assertEquals(card("H2"), policy.choose(v).card());
  }

  @Test
  @DisplayName("마이티 다음에는 조커보다 기루 A를 먼저 확인한다")
  void probesTrumpAceAfterMightyBeforeJoker() {
    var prior = row(1, Trump.SPADE, p(0, "DA"), p(1, "D2"), p(2, "D3"), p(3, "D4"), p(4, "D5"));
    View v = view(0, 0, Trump.SPADE, 16, "SA SK SQ S9 S8 S7 H2 C2 JK", "D8 D9 D10",
        SpecialFriend.NONE, NoFriend.INSTANCE, List.of(prior), List.of());
    assertEquals(card("SA"), policy.choose(v).card());
  }

  @Test
  @DisplayName("마쪼와 기루 A를 모두 들면 초구는 마이티가 우선이다")
  void mightyStillPrecedesAceProbe() {
    View v = view(0, 0, Trump.SPADE, 16, "DA SA SK SQ S9 S8 S7 H2 C2 JK", "D8 D9 D10",
        SpecialFriend.NONE, NoFriend.INSTANCE, List.of(), List.of());
    assertEquals(card("DA"), policy.choose(v).card());
  }

  @Test
  @DisplayName("기루 A로 외부 기루가 소진되면 조커를 기루에 고정하지 않는다")
  void stopsForcingJokerTrumpAfterSuccessfulAceProbe() {
    var first = row(1, Trump.SPADE, p(0, "DA"), p(1, "S2"), p(2, "S3"), p(3, "S4"), p(4, "D5"));
    var second = row(2, Trump.SPADE, p(0, "SA"), p(1, "S5"), p(2, "S6"), p(3, "S7"), p(4, "S8"));
    View v = view(0, 0, Trump.SPADE, 16, "SK SQ SJ S10 S9 H2 C2 JK", "D8 D9 D10",
        SpecialFriend.NONE, NoFriend.INSTANCE, List.of(first, second), List.of());
    assertNotEquals(new JokerLead(Suit.SPADE), policy.choose(v));
  }

  @Test
  @DisplayName("초반 기루 반환으로 미공개 프렌드를 판단하되 공식 공개 상태는 유지한다")
  void infersFriendFromEarlyReturn() {
    var first = row(1, Trump.SPADE, p(0, "C2"), p(1, "CA"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    View v = view(0, 0, Trump.SPADE, 16, "DA S2 H2 H3 H4 C6 C7 C8 C9", "D8 D9 D10",
        new CardFriend(card("JK")), UnrevealedFriend.INSTANCE, List.of(first),
        List.of(p(1, "SK"), p(2, "D2"), p(3, "D3"), p(4, "D4")));
    assertTrue(new PlayFacts(v).teams().isAlly(new Seat(1)));
    assertSame(UnrevealedFriend.INSTANCE, v.context().friendStatus());
  }

  @Test
  @DisplayName("야당도 반환 신호를 낸 미공개 프렌드를 자기 팀으로 취급하지 않는다")
  void oppositionUsesSamePublicFriendSignal() {
    var first = row(1, Trump.SPADE, p(0, "C2"), p(1, "CA"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    View v = view(2, 0, Trump.SPADE, 16, "S2 H2 H3 H4 C6 C7 C8 C9 D2", "D8 D9 D10",
        new CardFriend(card("JK")), UnrevealedFriend.INSTANCE, List.of(first), List.of(p(1, "SK")));
    assertFalse(new PlayFacts(v).teams().isAlly(new Seat(1)));
    assertTrue(new PlayFacts(v).teams().isAlly(new Seat(3)));
  }

  @Test
  @DisplayName("야당의 확정 승리 기루라도 초반 탈기 근거 없으면 사이드로 공격한다")
  void oppositionCannotBypassTrumpAvoidanceWithMaster() {
    var first = row(1, Trump.SPADE, p(0, "C2"), p(1, "CA"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    View v = view(1, 0, Trump.SPADE, 16, "DA SA SK S2 H2 H3 C6 C7 C8", "D8 D9 D10",
        SpecialFriend.NONE, NoFriend.INSTANCE, List.of(first), List.of());
    Action chosen = policy.choose(v);
    assertFalse(v.contract().trump().matches(((StandardCard) chosen.card()).suit()));
  }

  @Test
  @DisplayName("전체 두 기루에서 사이드가 약하면 내 강한 마지막 기루도 회수용으로 남긴다")
  void keepsStrongLastTrumpWhenSidesAreWeak() {
    String owned = "SA C2 H2 D2", buried = "D8 D9 D10";
    var history = countedHistory(owned, buried, "DA JK S2 S3 S4 S5 S6 S7 S8 S9 S10 SJ SQ",
        "SK CA HA DK",
        List.of(), null);
    View v = view(0, 0, Trump.SPADE, 16, owned, buried, SpecialFriend.NONE, NoFriend.INSTANCE,
        history, List.of());
    assertNotEquals(card("SA"), policy.choose(v).card());
    assertTrue(new TrumpControl(v, new PlayFacts(v)).shouldPreserveLastTrump());
  }

  @Test
  @DisplayName("내 기루가 더 강하고 사이드 AK 연결을 누를 수 있으면 상대 기루를 뺀다")
  void extractsLastWeakerTrumpForConnectedSide() {
    String owned = "SA CA CK C2", buried = "D8 D9 D10";
    var history = countedHistory(owned, buried, "DA JK S2 S3 S4 S5 S6 S7 S8 S9 S10 SJ SQ", "SK",
        List.of(),
        null);
    View v = view(0, 0, Trump.SPADE, 16, owned, buried, SpecialFriend.NONE, NoFriend.INSTANCE,
        history, List.of());
    assertEquals(card("SA"), policy.choose(v).card());
    assertFalse(new TrumpControl(v, new PlayFacts(v)).shouldPreserveLastTrump());
  }

  @Test
  @DisplayName("남은 낮은 기루가 프렌드에게 있을 수 있으면 주공의 마지막 탑 기루를 남긴다")
  void preservesLastTrumpWhenFriendCanStillHoldTheOther() {
    var first = row(1, Trump.SPADE, p(0, "DA"), p(1, "JK"), p(2, "C2"), p(3, "C3"), p(4, "C4"));
    var second = row(2, Trump.SPADE, p(0, "SA"), p(1, "S2"), p(2, "S3"), p(3, "S4"), p(4, "S5"));
    var third = row(3, Trump.SPADE, p(0, "SJ"), p(1, "S6"), p(2, "C6"), p(3, "S7"), p(4, "C7"));
    var fourth = row(4, Trump.SPADE, p(0, "SK"), p(1, "S10"), p(2, "C8"), p(3, "S9"), p(4, "C9"));
    View v = view(0, 0, Trump.SPADE, 17, "SQ CA CK C10 H2 H3", "D8 D9 D10",
        new CardFriend(card("JK")), new RevealedFriend(new Seat(1)),
        List.of(first, second, third, fourth), List.of());
    assertEquals(List.of(card("S8")), new TrumpControl(v, new PlayFacts(v)).outside());
    assertEquals(card("CA"), policy.choose(v).card());
  }

  @Test
  @DisplayName("사이드 AK가 강해도 상대 기루가 더 강하면 사이드를 먼저 쓴다")
  void playsSideBeforeLosingLastTrumpLead() {
    String owned = "S2 CA CK C2", buried = "D8 D9 D10";
    var history = countedHistory(owned, buried, "DA JK S3 S4 S5 S6 S7 S8 S9 S10 SJ SQ SK", "SA",
        List.of(),
        null);
    View v = view(0, 0, Trump.SPADE, 16, owned, buried, SpecialFriend.NONE, NoFriend.INSTANCE,
        history, List.of());
    assertEquals(card("CA"), policy.choose(v).card());
    assertTrue(new TrumpControl(v, new PlayFacts(v)).shouldPreserveLastTrump());
  }

  @Test
  @DisplayName("문양 강제로 마이티와 조커만 합법이면 아군 간에 마이티 대신 조커를 쓴다")
  void forcedSpecialFollowPreservesMighty() {
    var prior = row(1, Trump.SPADE, p(0, "C2"), p(1, "CA"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    View v = view(0, 0, Trump.SPADE, 16, "DA JK S3 H2 H3 H4 C6 C7 C8", "D8 D9 D10",
        new CardFriend(card("S2")), new RevealedFriend(new Seat(2)), List.of(prior),
        List.of(p(1, "D2"), p(2, "S2"), p(3, "D3"), p(4, "D4")));
    assertEquals(card("JK"), policy.choose(v).card());
  }

  @Test
  @DisplayName("야당은 후기 공개 근거로 주공의 마지막 기루를 이겨 빼는 공격을 허용한다")
  void permitsProvenLateDeclarerStrip() {
    String owned = "SA CA C2", buried = "D8 D9 D10";
    var last = row(7, Trump.SPADE, p(0, "S3"), p(1, "SK"), p(2, "H2"), p(3, "D2"), p(4, "C3"));
    var history = countedHistory(owned, buried, "S4 JK DA S5 S6 S7 S8 S9 S10 SJ SQ", "S2",
        List.of(), last);
    View v = view(1, 0, Trump.SPADE, 16, owned, buried, SpecialFriend.NONE, NoFriend.INSTANCE,
        history, List.of());
    assertEquals(1, new TrumpControl(v, new PlayFacts(v)).outside().size());
    assertEquals(card("SA"), policy.choose(v).card());
  }

  @Test
  @DisplayName("후기 실제 탈기로 모든 기루가 소진된 리드는 미공개 프렌드로 오인하지 않는다")
  void doesNotInferFriendFromProvenLateStrip() {
    String owned = "CA C2 H2", buried = "D8 D9 D10";
    var last = row(7, Trump.SPADE, p(1, "SA"), p(2, "D2"), p(3, "H3"), p(4, "C3"), p(0, "S2"));
    var current = List.of(p(1, "D3"), p(2, "D4"), p(3, "D5"), p(4, "D6"));
    var history = countedHistory(owned, buried, "S3 JK DA S4 S5 S6 S7 S8 S9 S10 SJ SQ SK", "CQ",
        current,
        last);
    View v = view(0, 0, Trump.SPADE, 16, owned, buried, new CardFriend(card("CQ")),
        UnrevealedFriend.INSTANCE,
        history, current);
    assertTrue(new PlayFacts(v).teams().friendSeat().isEmpty());
  }

  @Test
  @DisplayName("단독 A와 물패를 AK 연결과 혼동해 마지막 기루를 소모하지 않는다")
  void doesNotTreatAceWithWaterAsConnectedRun() {
    String owned = "SA CA C2 H2", buried = "D8 D9 D10";
    var history = countedHistory(owned, buried, "DA JK S2 S3 S4 S5 S6 S7 S8 S9 S10 SJ SQ",
        "SK CK CQ HA",
        List.of(), null);
    View v = view(0, 0, Trump.SPADE, 16, owned, buried, SpecialFriend.NONE, NoFriend.INSTANCE,
        history, List.of());
    assertNotEquals(card("SA"), policy.choose(v).card());
    assertTrue(new TrumpControl(v, new PlayFacts(v)).shouldPreserveLastTrump());
  }

  @Test
  @DisplayName("기루 A가 내 손이면 친구 K를 탑카로 보고 마이티와 A를 모두 보존한다")
  void protectsFriendKingBelowOwnTrumpAce() {
    var prior = row(1, Trump.SPADE, p(0, "C2"), p(1, "CA"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    View v = view(0, 0, Trump.SPADE, 16, "DA SA S2 H2 H3 C6 C7 C8 C9", "D8 D9 D10",
        new CardFriend(card("JK")), UnrevealedFriend.INSTANCE, List.of(prior),
        List.of(p(1, "SK"), p(2, "D2"), p(3, "D3"), p(4, "D4")));
    assertTrue(new PlayFacts(v).isSuitTop(card("SK")));
    assertEquals(card("S2"), policy.choose(v).card());
  }

  @Test
  @DisplayName("야당이 모두 공개 무기루이면 주공 9구 조커로 친구 기루를 빼지 않는다")
  void declarerJokerDoesNotStripOnlyFriend() {
    String owned = "JK C2", buried = "D8 D9 D10";
    var last = row(8, Trump.SPADE, p(1, "S2"), p(2, "H2"), p(3, "H3"), p(4, "H4"), p(0, "SK"));
    var history = countedHistory(owned, buried, "DA", "SA S3", List.of(), last);
    View v = view(0, 0, Trump.SPADE, 17, owned, buried, new CardFriend(card("DA")),
        new RevealedFriend(new Seat(1)), history, List.of());
    Action selected = policy.choose(v);
    assertInstanceOf(JokerLead.class, selected);
    assertNotEquals(Suit.SPADE, ((JokerLead) selected).declaredSuit());
  }

  @Test
  @DisplayName("야당이 모두 공개 무기루이면 친구 9구 조커로 주공 기루를 빼지 않는다")
  void friendJokerDoesNotStripOnlyDeclarer() {
    String owned = "JK C2", buried = "D8 D9 D10";
    var last = row(8, Trump.SPADE, p(1, "SK"), p(2, "H2"), p(3, "H3"), p(4, "H4"), p(0, "S2"));
    var history = countedHistory(owned, buried, "DA", "SA S3", List.of(), last);
    View v = view(1, 0, Trump.SPADE, 17, owned, buried, new CardFriend(card("DA")),
        new RevealedFriend(new Seat(1)), history, List.of());
    Action selected = policy.choose(v);
    assertInstanceOf(JokerLead.class, selected);
    assertNotEquals(Suit.SPADE, ((JokerLead) selected).declaredSuit());
  }

  @Test
  @DisplayName("9구 따라내기의 주공 간 계획보다 활성 조커 기한이 먼저다")
  void declarerFollowDoesNotKeepJokerForTenth() {
    String owned = "JK SQ", buried = "D8 D9 D10";
    var current = List.of(p(1, "H2"), p(2, "H3"), p(3, "H4"), p(4, "H5"));
    var last = row(8, Trump.SPADE, p(0, "C2"), p(1, "CA"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var history = countedHistory(owned, buried, "DA", "HA", current, last);
    View v = view(0, 0, Trump.SPADE, 17, owned, buried, SpecialFriend.NONE, NoFriend.INSTANCE,
        history, current);
    assertEquals(card("JK"), policy.choose(v).card());
  }

  @Test
  @DisplayName("기루 정리 뒤 부기루를 누를 때 9구 전에도 조커를 연결한다")
  void declarerUsesJokerOnSideAfterTrumpClearance() {
    var first = row(1, Trump.SPADE, p(0, "DA"), p(1, "D4"), p(2, "D5"), p(3, "D6"), p(4, "D7"));
    var second = row(2, Trump.SPADE, p(0, "SA"), p(1, "S2"), p(2, "C2"), p(3, "C3"), p(4, "C4"));
    View v = view(0, 0, Trump.SPADE, 17, "JK SQ H2 D2 D3 C5 C6 C7", "D8 D9 D10",
        new CardFriend(card("S2")), new RevealedFriend(new Seat(1)), List.of(first, second),
        List.of());
    var action = policy.choose(v);
    assertInstanceOf(JokerLead.class, action);
    assertNotEquals(Suit.SPADE, ((JokerLead) action).declaredSuit());
  }

  @Test
  @DisplayName("주공과 프렌드만 기루가 남으면 일반 기루 반환도 중지한다")
  void friendDoesNotReturnOnlyAlliedTrumps() {
    var first = row(1, Trump.SPADE, p(0, "CA"), p(1, "DA"), p(2, "C2"), p(3, "C3"), p(4, "C4"));
    var second = row(2, Trump.SPADE, p(1, "SK"), p(2, "H2"), p(3, "H3"), p(4, "H4"), p(0, "S2"));
    View v = view(1, 0, Trump.SPADE, 17, "S3 S4 C5 C6 D2 D3 H5 H6", "D8 D9 D10",
        new CardFriend(card("DA")), new RevealedFriend(new Seat(1)), List.of(first, second),
        List.of());
    assertFalse(JokerSuitPlan.enemiesMayHoldTrump(v, new PlayFacts(v)));
    assertFalse(Trump.SPADE.matches(((StandardCard) policy.choose(v).card()).suit()));
  }

  @Test
  @DisplayName("프렌드는 야당이 간친 비요청 문양을 낮은 카드 fallback에서도 반복하지 않는다")
  void friendAvoidsRuffedSuitInFallback() {
    var first = row(1, Trump.SPADE, p(0, "C2"), p(1, "CA"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var second = row(2, Trump.SPADE, p(1, "H2"), p(2, "S2"), p(3, "H3"), p(4, "H4"), p(0, "H5"));
    var third = row(3, Trump.SPADE, p(2, "C6"), p(3, "C7"), p(4, "C8"), p(0, "C9"), p(1, "DA"));
    View v = view(1, 0, Trump.SPADE, 17, "HQ H6 D2 D3 D4 C10 CJ", "D8 D9 D10",
        new CardFriend(card("DA")), new RevealedFriend(new Seat(1)), List.of(first, second, third),
        List.of());
    assertFalse(mighty.ai.play.friend.FriendReturnSafety.allows(Suit.HEART, v, new PlayFacts(v)));
    assertNotEquals(Suit.HEART, ((StandardCard) policy.choose(v).card()).suit());
  }

  @Test
  @DisplayName("오래된 주공 리드 뒤 야당 간이 확인된 문양은 다시 돌리지 않는다")
  void declarerRequestedSuitIsException() {
    var first = row(1, Trump.SPADE, p(0, "HA"), p(1, "H2"), p(2, "H3"), p(3, "H4"), p(4, "H5"));
    var second = row(2, Trump.SPADE, p(0, "C2"), p(1, "CA"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var third = row(3, Trump.SPADE, p(1, "H6"), p(2, "S2"), p(3, "H7"), p(4, "H8"), p(0, "H9"));
    var fourth = row(4, Trump.SPADE, p(2, "C6"), p(3, "C7"), p(4, "C8"), p(0, "C9"), p(1, "DA"));
    View v = view(1, 0, Trump.SPADE, 17, "HQ H10 D2 D3 D4 C10", "D8 D9 D10",
        new CardFriend(card("DA")), new RevealedFriend(new Seat(1)),
        List.of(first, second, third, fourth), List.of());
    assertFalse(mighty.ai.play.friend.FriendReturnSafety.allows(Suit.HEART, v, new PlayFacts(v)));
  }

  @Test
  @DisplayName("야당 기루가 소진되면 이전에 간친 문양의 제한은 해제한다")
  void ruffedSuitBecomesAvailableAfterTrumpClearance() {
    var first = row(1, Trump.SPADE, p(0, "C2"), p(1, "CA"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var second = row(2, Trump.SPADE, p(1, "H2"), p(2, "S2"), p(3, "H3"), p(4, "H4"), p(0, "H5"));
    var third = row(3, Trump.SPADE, p(2, "C6"), p(3, "C7"), p(4, "C8"), p(0, "C9"), p(1, "DA"));
    var fourth = row(4, Trump.SPADE, p(1, "SK"), p(2, "D5"), p(3, "D6"), p(4, "D7"), p(0, "S3"));
    View v = view(1, 0, Trump.SPADE, 17, "HQ H6 D2 D3 C10 CJ", "D8 D9 D10",
        new CardFriend(card("DA")), new RevealedFriend(new Seat(1)),
        List.of(first, second, third, fourth), List.of());
    assertTrue(mighty.ai.play.friend.FriendReturnSafety.allows(Suit.HEART, v, new PlayFacts(v)));
  }

  @Test
  @DisplayName("간 이력이 없어도 주공이 요청하지 않은 두 번 돈 문양을 재반환하지 않는다")
  void friendAvoidsRepeatedUnrequestedSuit() {
    var first = row(1, Trump.SPADE, p(0, "C2"), p(1, "CA"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var second = row(2, Trump.SPADE, p(1, "HA"), p(2, "H2"), p(3, "H3"), p(4, "H4"), p(0, "H5"));
    var third = row(3, Trump.SPADE, p(1, "HK"), p(2, "H7"), p(3, "H8"), p(4, "H9"), p(0, "HJ"));
    View v = view(1, 0, Trump.SPADE, 17, "H6 HQ D2 D3 D4 C10 CJ", "D8 D9 D10",
        new CardFriend(card("CA")), new RevealedFriend(new Seat(1)), List.of(first, second, third),
        List.of());
    assertFalse(mighty.ai.play.friend.FriendReturnSafety.allows(Suit.HEART, v, new PlayFacts(v)));
    assertNotEquals(Suit.HEART, ((StandardCard) policy.choose(v).card()).suit());
  }

  @Test
  @DisplayName("부기루 5 T Q는 초구 T를 마이티에게 처리하고 Q와 5를 남긴다")
  void feedsMiddleSidePointOnOpening() {
    View v = view(0, 0, Trump.HEART, 16, "HA HK HQ HJ H2 H3 D5 D10 DQ C2", "H8 H9 C10",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(), List.of());
    assertEquals(card("D10"), policy.choose(v).card());
  }

  @Test
  @DisplayName("초구 중간 점수를 처리한 뒤 Q 5의 낮은 5로 상위 소모를 시도한다")
  void continuesSideProbeAfterMiddlePoint() {
    var first = row(1, Trump.HEART, p(0, "D10"), p(1, "SA"), p(2, "D2"), p(3, "D3"), p(4, "D4"));
    var second = row(2, Trump.HEART, p(1, "H2"), p(2, "C2"), p(3, "C3"), p(4, "C4"), p(0, "HA"));
    View v = view(0, 0, Trump.HEART, 16, "HK HQ HJ H3 DQ D5 C5 C6", "H8 H9 C10",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(1)), List.of(first, second),
        List.of());
    assertEquals(card("D5"), policy.choose(v).card());
  }

  @Test
  @DisplayName("친구 마이티에 전개 문양의 중간 T를 지원하고 Q와 탐색 숫자를 남긴다")
  void supportsMiddlePointInsteadOfReservingWholeSuit() {
    var first = row(1, Trump.HEART, p(0, "C2"), p(1, "CA"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    View v = view(0, 0, Trump.HEART, 16, "H2 H3 H4 H5 DQ D10 D5 C6 C7", "H8 H9 C10",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(1)), List.of(first),
        List.of(p(1, "SA"), p(2, "D2"), p(3, "D3"), p(4, "D4")));
    assertEquals(card("D10"), policy.choose(v).card());
  }

  @Test
  @DisplayName("기루 A 후 K가 탑이면 조커보다 K를 먼저 사용한다")
  void followsAceWithKingBeforeJoker() {
    var first = row(1, Trump.SPADE, p(0, "DA"), p(1, "D2"), p(2, "D3"), p(3, "D4"), p(4, "D5"));
    var second = row(2, Trump.SPADE, p(0, "SA"), p(1, "S2"), p(2, "S3"), p(3, "S4"), p(4, "S5"));
    View v = view(0, 0, Trump.SPADE, 17, "SK SQ S6 JK H2 C2 C3 C4", "D8 D9 D10",
        SpecialFriend.NONE, NoFriend.INSTANCE, List.of(first, second), List.of());
    assertEquals(card("SK"), policy.choose(v).card());
  }

  @Test
  @DisplayName("A에 외부 K가 빠지면 Q로 연결하고 조커를 남긴다")
  void promotedQueenContinuesBeforeJoker() {
    var first = row(1, Trump.SPADE, p(0, "DA"), p(1, "D2"), p(2, "D3"), p(3, "D4"), p(4, "D5"));
    var second = row(2, Trump.SPADE, p(0, "SA"), p(1, "SK"), p(2, "S2"), p(3, "S3"), p(4, "S4"));
    View v = view(0, 0, Trump.SPADE, 17, "SQ SJ S6 JK H2 C2 C3 C4", "D8 D9 D10",
        SpecialFriend.NONE, NoFriend.INSTANCE, List.of(first, second), List.of());
    assertEquals(card("SQ"), policy.choose(v).card());
  }

  @Test
  @DisplayName("A를 써도 K가 남아 Q가 탑이 아니면 조커 기루 소모를 사용한다")
  void outstandingKingStillNeedsJoker() {
    var first = row(1, Trump.SPADE, p(0, "DA"), p(1, "D2"), p(2, "D3"), p(3, "D4"), p(4, "D5"));
    var second = row(2, Trump.SPADE, p(0, "SA"), p(1, "S2"), p(2, "S3"), p(3, "S4"), p(4, "S5"));
    View v = view(0, 0, Trump.SPADE, 17, "SQ SJ S6 JK H2 C2 C3 C4", "D8 D9 D10",
        SpecialFriend.NONE, NoFriend.INSTANCE, List.of(first, second), List.of());
    assertEquals(new JokerLead(Suit.SPADE), policy.choose(v));
  }

  @Test
  @DisplayName("기루 A의 점수 따라내기는 모든 역할의 공통 추론이고 확정 무기루가 아니다")
  void trumpPointsAreSharedSoftEvidence() {
    var first = row(1, Trump.SPADE, p(0, "SA"), p(1, "SJ"), p(2, "SQ"), p(3, "S2"), p(4, "S3"));
    View a = view(0, 0, Trump.SPADE, 17, "SK S4 H2 H3 H4 H5 C3 C4 D2", "D8 D9 D10",
        new CardFriend(card("DA")), UnrevealedFriend.INSTANCE, List.of(first), List.of());
    View b = view(1, 0, Trump.SPADE, 17, "DA S5 H2 H3 H4 H5 C3 C4 D2", "D8 D9 D10",
        new CardFriend(card("DA")), UnrevealedFriend.INSTANCE, List.of(first), List.of(p(0, "C2")));
    var knownA = new PlayFacts(a).cards();
    var knownB = new PlayFacts(b).cards();
    assertEquals(knownA.trumps().likelyShortSeats(), knownB.trumps().likelyShortSeats());
    assertTrue(knownA.trumps().likelyShort(new Seat(1)));
    assertFalse(knownA.isVoid(new Seat(1), Suit.SPADE));
    assertTrue(knownA.outsideCards().contains(card("S6")));
  }

  @Test
  @DisplayName("나중에 숫자 기루를 제출하면 이전의 점수뿐 추론을 수정한다")
  void laterNumericTrumpRevisesSoftEvidence() {
    var first = row(1, Trump.SPADE, p(0, "SA"), p(1, "SJ"), p(2, "SQ"), p(3, "S2"), p(4, "S3"));
    var second = row(2, Trump.SPADE, p(0, "SK"), p(1, "S4"), p(2, "S5"), p(3, "S6"), p(4, "S7"));
    View v = view(0, 0, Trump.SPADE, 17, "S8 S9 H2 H3 H4 C2 C3 D2", "D8 D9 D10",
        new CardFriend(card("DA")), UnrevealedFriend.INSTANCE, List.of(first, second), List.of());
    assertFalse(new PlayFacts(v).cards().trumps().likelyShort(new Seat(1)));
    assertFalse(new PlayFacts(v).cards().isVoid(new Seat(1), Suit.SPADE));
  }

  @Test
  @DisplayName("노기루 야당은 선을 잡으면 미소모 조커에 콜한다")
  void noTrumpOppositionAlwaysCallsUnseenJoker() {
    var first = row(1, Trump.NO_TRUMP, p(0, "C2"), p(1, "CA"), p(2, "C4"), p(3, "C5"), p(4, "C6"));
    View v = view(1, 0, Trump.NO_TRUMP, 17, "C3 H2 H3 H4 S2 S3 D2 D3 D4", "H8 H9 C10",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(4)), List.of(first), List.of());
    var selected = policy.choose(v);
    assertEquals(card("C3"), selected.card());
    assertTrue(((StandardPlay) selected).call());
  }

  @Test
  @DisplayName("기루 게임도 미공개 조커 프렌드에 야당 총을 사용한다")
  void oppositionCallsUnrevealedJokerFriend() {
    var first = row(1, Trump.HEART, p(0, "C2"), p(1, "CA"), p(2, "C4"), p(3, "C5"), p(4, "C6"));
    View v = view(1, 0, Trump.HEART, 17, "C3 H2 H3 H4 S2 S3 D2 D3 D4", "H8 H9 C10",
        new CardFriend(card("JK")), UnrevealedFriend.INSTANCE, List.of(first), List.of());
    var selected = policy.choose(v);
    assertEquals(card("C3"), selected.card());
    assertTrue(((StandardPlay) selected).call());
  }

  @Test
  @DisplayName("주공은 조커가 자기 손에 없으면 기루 게임에서도 빵을 사용한다")
  void declarerCallsJokerInSuitGame() {
    var first = row(1, Trump.HEART, p(0, "SA"), p(1, "S2"), p(2, "S3"), p(3, "S4"), p(4, "S5"));
    View v = view(0, 0, Trump.HEART, 17, "C3 HA HK HQ HJ H2 D2 D3 C2", "H8 H9 C10",
        SpecialFriend.NONE, NoFriend.INSTANCE, List.of(first), List.of());
    var selected = policy.choose(v);
    assertEquals(card("C3"), selected.card());
    assertTrue(((StandardPlay) selected).call());
  }

  @Test
  @DisplayName("야당은 조커로 2점과 선을 빼앗고 여러 기루를 남긴다")
  void oppositionUsesJokerInsteadOfRuff() {
    var first = row(1, Trump.SPADE, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    View v = view(2, 0, Trump.SPADE, 17, "SA S3 S4 JK D2 D3 C6 C7 C8", "H8 H9 C10",
        new CardFriend(card("DA")), new RevealedFriend(new Seat(4)), List.of(first),
        List.of(p(0, "HA"), p(1,
            "H10")));
    assertEquals(card("JK"), policy.choose(v).card());
  }

  @Test
  @DisplayName("짧은 2 J Q 야당은 물패 대신 J로 점수 압박을 시작한다")
  void shortSidePointsAreFedBeforeWater() {
    var first = row(1, Trump.SPADE, p(0, "C2"), p(1, "CA"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    View v = view(2, 0, Trump.SPADE, 17, "H2 HJ HQ S2 S3 D2 D3 C6 C7", "H8 H9 C10",
        new CardFriend(card("DA")), new RevealedFriend(new Seat(4)), List.of(first),
        List.of(p(1, "H5")));
    assertEquals(card("HQ"), policy.choose(v).card());
  }

  @Test
  @DisplayName("짧은 사이드 점수도 야당 선두에 지원해 주공의 값싼 흘리기를 막는다")
  void scarceSidePointWaitsForBetterOpportunity() {
    var first = row(1, Trump.SPADE, p(0, "C2"), p(1, "CA"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    View v = view(2, 0, Trump.SPADE, 17, "H2 HK S2 S3 D2 D3 C6 C7 C8", "H8 H9 C10",
        new CardFriend(card("DA")), new RevealedFriend(new Seat(4)), List.of(first),
        List.of(p(1, "H5")));
    assertEquals(card("HK"), policy.choose(v).card());
  }

  @Test
  @DisplayName("마지막 야당 A가 마이티에 빠지면 K 가능성이 남아도 여당 교환을 중지한다")
  void lastOpponentAceStopsAlliedExchangeWithoutClaimingVoid() {
    var first = row(1, Trump.SPADE, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var second = row(2, Trump.SPADE, p(0, "SJ"), p(1, "S2"), p(2, "C6"), p(3, "C7"), p(4, "S3"));
    var third = row(3, Trump.SPADE, p(0, "D2"), p(1, "DA"), p(2, "D3"), p(3, "D4"), p(4, "SA"));
    View v = view(1, 0, Trump.SPADE, 17, "SQ S4 C8 C9 H2 H3 D5", "H8 H9 C10",
        new CardFriend(card("DA")), new RevealedFriend(new Seat(1)), List.of(first, second, third),
        List.of());
    var facts = new PlayFacts(v);
    assertFalse(facts.cards().isVoid(new Seat(4), Suit.SPADE));
    assertTrue(facts.cards().outsideCards().contains(card("SK")));
    assertTrue(facts.cards().trumps().avoidExchange(facts));
    assertFalse(Trump.SPADE.matches(((StandardCard) policy.choose(v).card()).suit()));
  }

  @Test
  @DisplayName("같은 소진 신호에서 조커 선언도 여당 기루를 빼지 않는다")
  void jokerAlsoRespectsLastOpponentAceSignal() {
    var first = row(1, Trump.SPADE, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var second = row(2, Trump.SPADE, p(0, "SJ"), p(1, "S2"), p(2, "C6"), p(3, "C7"), p(4, "S3"));
    var third = row(3, Trump.SPADE, p(0, "D2"), p(1, "DA"), p(2, "D3"), p(3, "D4"), p(4, "SA"));
    View v = view(1, 0, Trump.SPADE, 17, "JK SQ S4 C8 H2 H3 D5", "H8 H9 C10",
        new CardFriend(card("DA")), new RevealedFriend(new Seat(1)), List.of(first, second, third),
        List.of());
    var selected = policy.choose(v);
    assertInstanceOf(JokerLead.class, selected);
    assertNotEquals(Suit.SPADE, ((JokerLead) selected).declaredSuit());
  }

  @Test
  @DisplayName("반환할 기루 경로가 없는 친구의 반복 사이드는 A보다 마이티를 사용한다")
  void declarerUsesMightyOnRepeatedSideReturn() {
    var first = row(1, Trump.SPADE, p(0, "HK"), p(1, "H2"), p(2, "H3"), p(3, "H4"), p(4, "H5"));
    var second = row(2, Trump.SPADE, p(0, "S2"), p(1, "JK"), p(2, "S3"), p(3, "S4"), p(4, "S5"));
    View v = view(0, 0, Trump.SPADE, 17, "DA HA SQ S6 C2 C3 D2 D3", "D8 D9 D10",
        new CardFriend(card("JK")), new RevealedFriend(new Seat(1)), List.of(first, second),
        List.of(p(1, "H6"), p(2, "HJ"), p(3, "H7"), p(4, "H8")));
    assertEquals(card("DA"), policy.choose(v).card());
  }

  @Test
  @DisplayName("셋팅은 이기는 순서가 하나 있어도 다른 순서에서 간을 당하면 거부한다")
  void settingRejectsOrderDependentSideRuff() {
    String owned = "SA HA", buried = "D8 D9 D10";
    var history = countedHistory(owned, buried, "DA JK", "SK", List.of(), null);
    View v = view(0, 0, Trump.SPADE, 17, owned, buried, SpecialFriend.NONE, NoFriend.INSTANCE,
        history, List.of());
    assertFalse(mighty.game.setting.SettingProof.canDeclare(v));
  }

  @Test
  @DisplayName("셋팅은 현재 구에서 이겨도 미소모 활성 조커의 후속 역전을 포함해 거부한다")
  void settingRejectsUnseenActiveJoker() {
    String owned = "SA SK", buried = "D8 D9 D10";
    var history = countedHistory(owned, buried, "DA", "JK", List.of(), null);
    View v = view(0, 0, Trump.SPADE, 17, owned, buried, SpecialFriend.NONE, NoFriend.INSTANCE,
        history, List.of());
    assertFalse(mighty.game.setting.SettingProof.canDeclare(v));
  }

  @Test
  @DisplayName("소모 J와 자기 QK 사이에서 친구 T는 같은 A를 압박하므로 덮지 않는다")
  void equivalentAllyPressureKeepsHigherCards() {
    var first = row(1, Trump.SPADE, p(0, "CJ"), p(1, "C9"), p(2, "C3"), p(3, "C4"), p(4, "CA"));
    View v = view(0, 0, Trump.SPADE, 16, "CQ CK C2 S2 S3 S4 H2 H3 D2", "D8 D9 D10",
        new CardFriend(card("JK")), new RevealedFriend(new Seat(4)), List.of(first),
        List.of(p(4, "C10")));
    assertEquals(card("C2"), policy.choose(v).card());
  }

  @Test
  @DisplayName("친구 아래의 외부 중간 카드가 있으면 동일 압박으로 가정하지 않는다")
  void outsideIntermediateCardPreventsEquivalentPass() {
    var first = row(1, Trump.SPADE, p(0, "CJ"), p(1, "C9"), p(2, "C3"), p(3, "C4"), p(4, "CA"));
    View v = view(0, 0, Trump.SPADE, 16, "CK C2 S2 S3 S4 H2 H3 D2 D3", "D8 D9 D10",
        new CardFriend(card("JK")), new RevealedFriend(new Seat(4)), List.of(first),
        List.of(p(4, "C10")));
    assertTrue(
        EquivalentPressure.pass(v, new PlayFacts(v), new PlayFacts(v).legalActions()).isEmpty());
  }

  @Test
  @DisplayName("A 미소모여도 중간 카드가 소모 또는 자기 손이면 친구 점수를 덮지 않는다")
  void equivalentPressureWithUnseenAce() {
    var first = row(1, Trump.SPADE, p(0, "H2"), p(1, "HA"), p(2, "CJ"), p(3, "H3"), p(4, "H4"));
    View v = view(0, 0, Trump.SPADE, 16, "CQ CK C2 S2 S3 S4 H5 D2 D3", "D8 D9 D10",
        new CardFriend(card("JK")), new RevealedFriend(new Seat(1)), List.of(first),
        List.of(p(1, "C10"), p(2,
            "C6"), p(3, "C7"), p(4, "C8")));
    assertEquals(card("C2"), policy.choose(v).card());
  }

  @Test
  @DisplayName("프렌드는 남은 주공 마이티 문양보다 다른 반환 문양을 고른다")
  void jokerFriendAvoidsMightyAttack() {
    var first = row(1, Trump.NO_TRUMP, p(0, "DK"), p(1, "D2"), p(2, "D3"), p(3, "D4"), p(4, "D5"));
    var second = row(2, Trump.NO_TRUMP, p(0, "SJ"), p(1, "S2"), p(2, "S3"), p(3, "S4"), p(4, "S5"));
    var third = row(3, Trump.NO_TRUMP, p(0, "H2"), p(1, "HA"), p(2, "H3"), p(3, "H4"), p(4, "H5"));
    View v = view(1, 0, Trump.NO_TRUMP, 17, "SK SQ JK H6 C2 C3 D6", "D8 D9 D10",
        new CardFriend(card("JK")), new RevealedFriend(new Seat(1)), List.of(first, second, third),
        List.of());
    Action action = policy.choose(v);
    assertFalse(action.card() instanceof StandardCard c && c.suit() == Suit.SPADE);
    if (action instanceof JokerLead lead) {
      assertNotEquals(Suit.SPADE, lead.declaredSuit());
    }
  }

  @Test
  @DisplayName("주공 A 뒤 상위 K가 남으면 조커로 기루를 연결한다")
  void declarerUsesJokerAfterBlockedAceProbe() {
    var first = row(1, Trump.SPADE, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var second = row(2, Trump.SPADE, p(0, "SA"), p(1, "S2"), p(2, "S3"), p(3, "S4"), p(4, "S5"));
    View v = view(0, 0, Trump.SPADE, 16, "SQ S6 S7 JK H2 H3 D2 D3", "D8 D9 D10",
        new CardFriend(card("DA")), new RevealedFriend(new Seat(1)), List.of(first, second),
        List.of());
    assertEquals(new JokerLead(Suit.SPADE), policy.choose(v));
  }

  @Test
  @DisplayName("야당은 점수지원 후보가 있어도 주공을 직접 간쳐 잡는 선택이 먼저다")
  void winningRuffPrecedesSpeculativePointFeed() {
    View v = view(1, 0, Trump.HEART, 16, "H2 DQ D2 D3 D4 C2 C3 C4 C5 C6", "",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(4)), List.of(),
        List.of(p(0, "SK")));
    assertEquals(card("H2"), policy.choose(v).card());
  }

  @Test
  @DisplayName("초구 A 뒤 같은 문양의 총을 마이티로 받은 프렌드는 세 번째 반환을 피한다")
  void doesNotReturnTwiceLedGunSuit() {
    var first = row(1, Trump.SPADE, p(0, "CA"), p(1, "C2"), p(2, "C4"), p(3, "C5"), p(4, "C6"));
    var called = new PublicPlay(new Seat(0), new StandardPlay((StandardCard) card("C3"), true));
    var second = row(2, Trump.SPADE, called, p(1, "DA"), p(2, "C9"), p(3, "C10"), p(4, "CJ"));
    View v = view(1, 0, Trump.SPADE, 16, "CQ CK C7 C8 H2 H3 D2 D3", "D8 D9 D10",
        new CardFriend(card("DA")), new RevealedFriend(new Seat(1)), List.of(first, second),
        List.of());
    assertNotEquals(Suit.CLUB, ((StandardCard) policy.choose(v).card()).suit());
  }

  private static List<PublicTrick> feedbackFirstTwo() {
    var first = row(1, Trump.HEART, p(1, "CA"), p(2, "C6"), p(3, "C4"), p(4, "C10"), p(0, "C7"));
    var call = new PublicPlay(new Seat(1), new StandardPlay((StandardCard) card("C3"), true));
    var second = row(2, Trump.HEART, call, p(2, "JK"), p(3, "CJ"), p(4, "H7"), p(0, "SA"));
    return List.of(first, second);
  }

  @Test
  @DisplayName("마쪼 소모 후 첫 기루 순회의 작은 점수에는 A를 숨겨 다음 공격을 남긴다")
  void oppositionHidesSafeAceOnFirstLowPointTrumpRound() {
    View v = view(3, 1, Trump.HEART, 16, "H9 D2 D8 S6 S5 S7 HA DK", "",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(0)), feedbackFirstTwo(),
        List.of(p(0, "HQ"), p(1, "H2"), p(2, "H5")));
    assertEquals(card("H9"), policy.choose(v).card());
  }

  @Test
  @DisplayName("같은 첫 순회라도 주공의 점수 기루에 점수가 겹치면 A로 잡는다")
  void oppositionTakesPointsInsteadOfAlwaysHidingAce() {
    View v = view(3, 1, Trump.HEART, 16, "H9 D2 D8 S6 S5 S7 HA DK", "",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(0)), feedbackFirstTwo(),
        List.of(p(0, "HQ"), p(1, "HK"), p(2, "H5")));
    assertEquals(card("HA"), policy.choose(v).card());
  }

  @Test
  @DisplayName("A3 K4 Q5의 장수 경계는 미소모 상위 개수로 계산한다")
  void trumpSurvivalThresholdsAreDerivedFromThreats() {
    for (String[] sample : List.of(
        new String[]{
            "HA H2 H3 S2 S3 S4 D2 D3 D4 C6", "HA", "2", "true"
        },
        new String[]{
            "HA H2 S2 S3 S4 S5 D2 D3 D4 C6", "HA", "2", "false"
        },
        new String[]{
            "HK H2 H3 H4 S2 S3 D2 D3 D4 C6", "HK", "3", "true"
        },
        new String[]{
            "HK H2 H3 S2 S3 S4 D2 D3 D4 C6", "HK", "3", "false"
        },
        new String[]{
            "HQ H2 H3 H4 H5 S2 D2 D3 D4 C6", "HQ", "4", "true"
        },
        new String[]{
            "HQ H2 H3 H4 S2 S3 D2 D3 D4 C6", "HQ", "4", "false"
        })) {
      View v = view(1, 0, Trump.HEART, 16, sample[0], "", new CardFriend(card("SA")),
          new RevealedFriend(new Seat(4)), List.of(), List.of(p(0, "C2")));
      var survival = new TrumpSurvival(v, new PlayFacts(v));
      assertEquals(Integer.parseInt(sample[2]),
          survival.threatsAbove((StandardCard) card(sample[1])));
      assertEquals(Boolean.parseBoolean(sample[3]),
          survival.survives((StandardCard) card(sample[1])));
    }
  }

  @Test
  @DisplayName("마쪼가 공개 소모되면 A 두 장의 안전 장수도 즉시 갱신한다")
  void consumedSpecialsReduceRequiredLength() {
    var first = row(1, Trump.HEART, p(0, "CA"), p(1, "JK"), p(2, "SA"), p(3, "C2"), p(4, "C3"));
    View v = view(1, 0, Trump.HEART, 16, "HA H2 S2 S3 S4 S5 D6 D7 C6", "",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(2)), List.of(first),
        List.of(p(2, "D2"), p(3, "D3"), p(4, "D4"), p(0, "D5")));
    var survival = new TrumpSurvival(v, new PlayFacts(v));
    assertEquals(0, survival.threatsAbove((StandardCard) card("HA")));
    assertTrue(survival.survives((StandardCard) card("HA")));
  }

  @Test
  @DisplayName("야당 A 세 장은 마쪼를 버티므로 낮은 기루로 간친다")
  void oppositionKeepsSurvivingAceWhenRuffing() {
    View v = view(1, 0, Trump.HEART, 16, "HA H2 H3 S2 S3 S4 D2 D3 D4 D5", "",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(4)), List.of(),
        List.of(p(0, "CA")));
    assertEquals(card("H2"), policy.choose(v).card());
  }

  @Test
  @DisplayName("야당 A 두 장은 상위 특수카드를 못 버티므로 점수 간을 친다")
  void oppositionSpendsExposedAceWhenRuffing() {
    View v = view(1, 0, Trump.HEART, 16, "HA H2 S2 S3 S4 S5 D2 D3 D4 D5", "",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(4)), List.of(),
        List.of(p(0, "CA")));
    assertEquals(card("HA"), policy.choose(v).card());
  }

  @Test
  @DisplayName("여당도 A를 버틸 장수가 있으면 낮은 기루로 간친다")
  void friendUsesSameSurvivalRule() {
    View v = view(1, 0, Trump.HEART, 16, "HA H2 H3 S2 S3 S4 D2 D3 D4 D5", "",
        new CardFriend(card("HA")), new RevealedFriend(new Seat(1)), List.of(),
        List.of(p(0, "C2")));
    // 주공이 먼저 간친 상황이 아니라 낮은 사이드로 회수를 요청한 상황입니다.
    assertEquals(card("H2"), policy.choose(v).card());
  }

  @Test
  @DisplayName("주공도 A 대신 상대 상위에 막힐 J를 간에 사용한다")
  void declarerSpendsExposedPointInsteadOfAceRuff() {
    var first = row(1, Trump.HEART, p(0, "C2"), p(1, "CA"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    View v = view(0, 0, Trump.HEART, 16, "HA HJ H2 S2 S3 S4 C6 C7 C8", "D8 D9 D10",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(2)), List.of(first),
        List.of(p(1, "DK"), p(2, "DQ"), p(3, "D3"), p(4, "D4")));
    assertEquals(card("HJ"), policy.choose(v).card());
  }

  @Test
  @DisplayName("주공은 A간 직후 막힌 J를 다시 내주지 않고 사이드로 전환한다")
  void declarerDoesNotLeadBlockedJackAfterAceRuff() {
    var first = row(1, Trump.HEART, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var second = row(2, Trump.HEART, p(0, "D2"), p(1, "DK"), p(2, "D3"), p(3, "D4"), p(4, "D5"));
    var third = row(3, Trump.HEART, p(1, "CK"), p(2, "C6"), p(3, "C7"), p(4, "C8"), p(0, "HA"));
    View v = view(0, 0, Trump.HEART, 16, "HJ H2 C9 C10 S2 S3 D6", "D8 D9 D10",
        new CardFriend(card("C3")), new RevealedFriend(new Seat(2)), List.of(first, second, third),
        List.of());
    assertFalse(Trump.HEART.matches(((StandardCard) policy.choose(v).card()).suit()));
  }

  @Test
  @DisplayName("주공은 외부 상위에 막힌 점수 대신 중간 숫자로 상위를 유도한다")
  void declarerProbesBlockedTrumpWithMiddleNumber() {
    var first = row(1, Trump.HEART, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    View v = view(0, 0, Trump.HEART, 16, "HJ H7 H8 C6 C7 S2 S3 D2 D3", "D8 D9 D10",
        new CardFriend(card("C3")), new RevealedFriend(new Seat(2)), List.of(first), List.of());
    assertEquals(card("H8"), policy.choose(v).card());
  }

  @Test
  @DisplayName("주공은 기루와 노기루 모두 조커 프렌드를 총으로 쏘지 않는다")
  void declarerDoesNotCallJokerFriend() {
    for (Trump trump : List.of(Trump.HEART, Trump.NO_TRUMP)) {
      var first = row(1, trump, p(0, "CA"), p(1, "C2"), p(2, "C4"), p(3, "C5"), p(4, "C6"));
      View v = view(0, 0, trump, 16, "C3 H2 H3 H4 S2 S3 D2 D3 D4", "H8 H9 C10",
          new CardFriend(card("JK")), UnrevealedFriend.INSTANCE, List.of(first), List.of());
      Action chosen = policy.choose(v);
      assertFalse(chosen instanceof StandardPlay play && play.call());
    }
  }

  @Test
  @DisplayName("낮은 기루 호출에는 뒤 A K를 못 이기는 Q 대신 조커로 응답한다")
  void jokerFriendDoesNotReplaceCallWithQueen() {
    var first = row(1, Trump.HEART, p(0, "CA"), p(1, "C2"), p(2, "C4"), p(3, "C5"), p(4, "C6"));
    View v = view(1, 0, Trump.HEART, 16, "HQ H2 JK S2 S3 D2 D3 D4 C7", "H8 H9 C10",
        new CardFriend(card("JK")), UnrevealedFriend.INSTANCE, List.of(first), List.of(p(0, "H3")));
    assertEquals(card("JK"), policy.choose(v).card());
  }

  @Test
  @DisplayName("공개상 주공이 못 따르는 문양 A를 다른 문양 수비보다 먼저 지원한다")
  void feedsDeclarerVoidAceBeforeSpadeDefense() {
    var first = row(1, Trump.HEART, p(1, "CA"), p(2, "C6"), p(3, "C4"), p(4, "C10"), p(0, "C7"));
    var second = row(2, Trump.HEART, p(1, "H2"), p(2, "H5"), p(3, "HA"), p(4, "H8"), p(0, "HQ"));
    var third = row(3, Trump.HEART, p(3, "D2"), p(4, "D10"), p(0, "DJ"), p(1, "HJ"), p(2, "D3"));
    View v = view(4, 1, Trump.HEART, 16, "SK SQ S9 DA D4 D7 H7", "",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(0)), List.of(first, second, third),
        List.of(p(1, "C2"), p(2, "CQ"), p(3, "D8")));
    assertEquals(card("DA"), policy.choose(v).card());
  }

  @Test
  @DisplayName("여야의 연결된 QK 승리 카드는 K로 보여주되 외부 중간 카드가 있으면 승격하지 않는다")
  void connectedWinningPointsShowHigherAcrossRoles() {
    for (int self : List.of(1, 2)) {
      var current = self == 1 ? List.of(p(0, "H10")) : List.of(p(0, "H10"), p(1, "HJ"));
      View v = view(self, 0, Trump.SPADE, 16, "HQ HK H2 S2 S3 S4 D2 C2 C3", "",
          new CardFriend(card("DA")), new RevealedFriend(new Seat(1)),
          List.of(row(1, Trump.SPADE, p(0, "HA"), p(1,
              "H3"), p(2, "H4"), p(3, "H5"), p(4, "H6"))), current);
      assertEquals(card("HK"), policy.choose(v).card());
    }
  }

  @Test
  @DisplayName("Q 다섯 장 수비도 마쪼AK를 버틸 수 있으므로 먼저 소비하지 않는다")
  void queenLengthDefenseUsesSharedSurvivalRule() {
    var first = row(1, Trump.SPADE, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    View v = view(1, 0, Trump.SPADE, 16, "SQ S3 S4 S5 S6 H2 H3 D2 D3", "",
        new CardFriend(card("DA")), new RevealedFriend(new Seat(4)), List.of(first),
        List.of(p(0, "S2")));
    assertEquals(card("S3"), policy.choose(v).card());
  }

  @Test
  @DisplayName("K 세 장은 미소모 마쪼A를 못 버티므로 고정 장수로 보존하지 않는다")
  void insufficientKingLengthDoesNotPretendGuaranteedDefense() {
    var first = row(1, Trump.SPADE, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    View v = view(1, 0, Trump.SPADE, 16, "SK SJ S4 H2 H3 H4 D2 D3 D4", "",
        new CardFriend(card("DA")), new RevealedFriend(new Seat(4)), List.of(first),
        List.of(p(0, "S10")));
    assertEquals(card("SJ"), policy.choose(v).card());
  }

  @Test
  @DisplayName("미소모 마이티 프렌드가 있으면 짧은 기루도 지원을 요청하며 상위를 확인한다")
  void remainingMightyFriendSupportsBlockedTrumpReturnAfterRuff() {
    var first = row(1, Trump.HEART, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var second = row(2, Trump.HEART, p(0, "D2"), p(1, "DK"), p(2, "D3"), p(3, "D4"), p(4, "D5"));
    var third = row(3, Trump.HEART, p(1, "CK"), p(2, "C6"), p(3, "C7"), p(4, "C8"), p(0, "HA"));
    View v = view(0, 0, Trump.HEART, 16, "HJ H10 C9 C10 S2 S3 D6", "D8 D9 D10",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(first, second, third),
        List.of());
    assertTrue(Trump.HEART.matches(((StandardCard) policy.choose(v).card()).suit()));
  }

  @Test
  @DisplayName("상위가 소모된 K Q T의 점수 제출도 A와 같은 교환 중단 신호다")
  void allOpponentPointSubmissionsStopWastefulJokerTrumpExchange() {
    var first = row(1, Trump.SPADE, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var second = row(2, Trump.SPADE, p(0, "SA"), p(1, "S3"), p(2, "S4"), p(3, "SJ"), p(4, "S5"));
    var third = row(3, Trump.SPADE, p(0, "S2"), p(1, "SK"), p(2, "SQ"), p(3, "DA"), p(4, "S10"));
    View v = view(3, 0, Trump.SPADE, 16, "JK S6 S7 HA H2 D2 D3", "",
        new CardFriend(card("DA")), new RevealedFriend(new Seat(3)), List.of(first, second, third),
        List.of());
    var facts = new PlayFacts(v);
    assertTrue(facts.cards().trumps().avoidExchange(facts));
    for (int seat : List.of(1, 2, 4)) {
      assertFalse(facts.cards().isVoid(new Seat(seat), Suit.SPADE));
    }
    Action chosen = policy.choose(v);
    assertFalse(chosen instanceof JokerLead lead && lead.declaredSuit() == Suit.SPADE);
    assertFalse(chosen.card() instanceof StandardCard c && c.suit() == Suit.SPADE);
  }

  @Test
  @DisplayName("같은 K Q T 제출도 상위 A가 미소모이면 확정 소진처럼 처리하지 않는다")
  void unspentHigherTrumpPreventsOverconfidentExchangeSignal() {
    var first = row(1, Trump.SPADE, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var second = row(2, Trump.SPADE, p(0, "SJ"), p(1, "S3"), p(2, "S4"), p(3, "S9"), p(4, "S5"));
    var third = row(3, Trump.SPADE, p(0, "S2"), p(1, "SK"), p(2, "SQ"), p(3, "DA"), p(4, "S10"));
    View v = view(3, 0, Trump.SPADE, 16, "JK S6 S7 HA H2 D2 D3", "",
        new CardFriend(card("DA")), new RevealedFriend(new Seat(3)), List.of(first, second, third),
        List.of());
    var facts = new PlayFacts(v);
    assertFalse(facts.cards().trumps().avoidExchange(facts));
  }

  @Test
  @DisplayName("노기루 프렌드는 자기 마이티 아래 삽 K도 다른 문양으로 피하지 않고 선취한다")
  void noTrumpFriendCashesKingBelowOwnMighty() {
    var first = row(1, Trump.NO_TRUMP, p(0, "C2"), p(1, "CA"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    View v = view(1, 0, Trump.NO_TRUMP, 17, "SA SK S2 D2 D3 H2 H3 C6 C7", "",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(first), List.of());
    assertEquals(card("SK"), policy.choose(v).card());
  }

  @Test
  @DisplayName("기루 정리 이후 외부 조커를 이유로 일반 사이드 탑카를 계속 아끼지 않는다")
  void trumpClearedFriendCashesSideDespiteUnseenJoker() {
    var first = row(1, Trump.SPADE, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var second = row(2, Trump.SPADE, p(0, "SA"), p(1, "DA"), p(2, "H2"), p(3, "H3"), p(4, "H4"));
    View v = view(1, 0, Trump.SPADE, 16, "HA H5 D2 D3 C6 C7 S2 S3", "",
        new CardFriend(card("DA")), new RevealedFriend(new Seat(1)), List.of(first, second),
        List.of());
    assertEquals(card("HA"), policy.choose(v).card());
  }

  @Test
  @DisplayName("주공이 기루 뒤 전환한 부기루는 프렌드 탑카로 간을 유도해도 된다")
  void friendUsesTopOfDeclarerSideAfterTrumpOperation() {
    var first = row(1, Trump.SPADE, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var second = row(2, Trump.SPADE, p(0, "SA"), p(1, "S2"), p(2, "S3"), p(3, "S4"), p(4, "S5"));
    var third = row(3, Trump.SPADE, p(0, "HK"), p(1, "HA"), p(2, "H2"), p(3, "H3"), p(4, "H4"));
    View v = view(1, 0, Trump.SPADE, 16, "HQ HJ H5 H6 S6 D2 D3", "",
        new CardFriend(card("HA")), new RevealedFriend(new Seat(1)), List.of(first, second, third),
        List.of());
    assertTrue(JokerSuitPlan.enemiesMayHoldTrump(v, new PlayFacts(v)));
    assertEquals(card("HQ"), policy.choose(v).card());
  }

  @Test
  @DisplayName("A 소모 후 최고가 된 단독 K는 마이티에 조커를 버려 보호할 수 있다")
  void oppositionProtectsPromotedKingUnderMighty() {
    var first = row(1, Trump.SPADE, p(0, "SA"), p(1, "S2"), p(2, "S3"), p(3, "S4"), p(4, "S5"));
    View v = view(2, 0, Trump.SPADE, 16, "SK JK H2 H3 H4 D2 D3 C2 C3", "",
        new CardFriend(card("DA")), new RevealedFriend(new Seat(1)), List.of(first),
        List.of(p(0, "S6"), p(1,
            "DA")));
    assertEquals(card("JK"), policy.choose(v).card());
  }

  @Test
  @DisplayName("A가 남은 K는 최고 기루가 아니므로 조커를 버려 보호하지 않는다")
  void oppositionDoesNotProtectKingAsTopWhileAceUnspent() {
    var first = row(1, Trump.SPADE, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    View v = view(2, 0, Trump.SPADE, 16, "SK JK H2 H3 H4 D2 D3 C6 C7", "",
        new CardFriend(card("DA")), new RevealedFriend(new Seat(1)), List.of(first),
        List.of(p(0, "S2"), p(1,
            "DA")));
    assertNotEquals(card("JK"), policy.choose(v).card());
  }

  @Test
  @DisplayName("A가 소모되어 K가 탑카이면 K에 점수를 따른 것도 공통 짧은 기루 신호다")
  void promotedTopLeadUsesSamePublicPointInference() {
    var first = row(1, Trump.SPADE, p(0, "SA"), p(1, "S2"), p(2, "S3"), p(3, "S4"), p(4, "S5"));
    var second = row(2, Trump.SPADE, p(0, "SK"), p(1, "SQ"), p(2, "S6"), p(3, "S7"), p(4, "S8"));
    View v = view(0, 0, Trump.SPADE, 16, "S9 S10 SJ DA H2 H3 C2 C3", "D8 D9 D10",
        new CardFriend(card("JK")), UnrevealedFriend.INSTANCE, List.of(first, second), List.of());
    var knowledge = new PlayFacts(v).cards();
    assertTrue(knowledge.trumps().likelyShort(new Seat(1)));
    assertFalse(knowledge.isVoid(new Seat(1), Suit.SPADE));
  }

  @Test
  @DisplayName("야당 세 명 0점 후 뒤가 친구뿐이면 다른 부기루 계획이 있어도 물패를 흘린다")
  void shedsZeroPointWaterBeforeOnlyFriendBehind() {
    var first = row(1, Trump.HEART, p(3, "HA"), p(4, "H2"), p(0, "SA"), p(1, "H3"), p(2, "H4"));
    View v = view(3, 3, Trump.HEART, 16, "S2 S3 H5 H6 DK DQ D4 D5 D6", "C8 C9 C10",
        new CardFriend(card("H2")), new RevealedFriend(new Seat(4)), List.of(first),
        List.of(p(0, "C2"), p(1,
            "C3"), p(2, "C4")));
    assertTrue(
        new PlayFacts(v).remainingSeats().stream().allMatch(new PlayFacts(v).teams()::isAlly));
    assertEquals(Suit.SPADE, ((StandardCard) policy.choose(v).card()).suit());
  }

  @Test
  @DisplayName("회수 불가능한 K Q 점수도 마지막 적은 점수 트릭에 물패처럼 처리한다")
  void shedsUnrecoverablePointsAcrossRanks() {
    for (String rank : List.of("K", "Q")) {
      var first = row(1, Trump.HEART, p(3, "HA"), p(4, "H2"), p(0, "SA"), p(1, "H3"), p(2, "H4"));
      View v = view(3, 3, Trump.HEART, 16, "D" + rank + " H5 H6 H7 H8 H9 H10 HJ HQ", "C8 C9 C10",
          new CardFriend(card("H2")), new RevealedFriend(new Seat(4)), List.of(first),
          List.of(p(0, "C2"), p(1,
              "C3"), p(2, "C4")));
      assertEquals(card("D" + rank), policy.choose(v).card());
    }
  }

  @Test
  @DisplayName("프렌드는 주공 J에 뒤 K를 못 이기는 Q를 겹쳐 내지 않는다")
  void friendDoesNotDonateUnsecuredQueenOverDeclarerJack() {
    View v = view(1, 0, Trump.HEART, 16, "C6 CQ S2 S3 S4 D2 D3 D4 D5 D6", "",
        new CardFriend(card("C6")), new RevealedFriend(new Seat(1)), List.of(),
        List.of(p(0, "CJ")));
    assertEquals(card("C6"), policy.choose(v).card());
  }

  @Test
  @DisplayName("프렌드의 Q가 실제 마지막 자리 승리라면 낮게 흘리지 않고 이긴다")
  void friendStillWinsWithQueenAtLastSeat() {
    View v = view(4, 0, Trump.HEART, 16, "C6 CQ S2 S3 S4 D2 D3 D4 D5 D6", "",
        new CardFriend(card("C6")), new RevealedFriend(new Seat(4)), List.of(),
        List.of(p(0, "C10"), p(1, "CJ"),
            p(2, "C3"), p(3, "C4")));
    assertEquals(card("CQ"), policy.choose(v).card());
  }

  @Test
  @DisplayName("친구의 점수 보호 물패는 점수를 묻기 전에 먼저 없애지 않는다")
  void friendKeepsScarceGuardsUntilPointsAreSupported() {
    View v = view(1, 0, Trump.HEART, 16, "S2 S10 SJ D5 DK C3 C4 C6 CQ C8", "",
        new CardFriend(card("DK")), new RevealedFriend(new Seat(1)), List.of(),
        List.of(p(0, "H2")));
    Card chosen = policy.choose(v).card();
    assertTrue(List.of(card("C3"), card("C4"), card("C6"), card("C8")).contains(chosen));
  }

  private View operatingFriendPair(String pair, boolean queenSpent) {
    var first = row(1, Trump.HEART, p(0, "CA"), p(1, "C5"), p(2, "C6"), p(3, "C7"), p(4, "C8"));
    var second = row(2, Trump.HEART, p(0, "D2"), p(1, "D3"), p(2, queenSpent ? "DQ" : "D4"),
        p(3, "D5"), p(4,
            "SA"));
    return view(1, 0, Trump.HEART, 16, pair + " C2 C3 C4 S2 S3 S4", "",
        new CardFriend(card("D3")), new RevealedFriend(new Seat(1)), List.of(first, second),
        List.of(p(4, "H2"),
            p(0, "HA")));
  }

  @Test
  @DisplayName("주공 부기루 QJ는 AK를 못 버티므로 먼저 점수 지원한다")
  void friendFeedsLowOperatingPairWithoutWinningPath() {
    assertEquals(card("DJ"), policy.choose(operatingFriendPair("DQ DJ", false)).card());
  }

  @Test
  @DisplayName("주공 부기루 AQ는 A를 남기고 연결되지 않은 Q를 지원한다")
  void friendFeedsUnconnectedQueenBelowOwnAce() {
    assertEquals(card("DQ"), policy.choose(operatingFriendPair("DA DQ", false)).card());
  }

  @Test
  @DisplayName("주공 부기루 KQ와 Q 소모 후 KJ는 연결된 승리 가능성을 보존한다")
  void friendPreservesConnectedOperatingPairsAfterCounting() {
    assertEquals(0, policy.choose(operatingFriendPair("DK DQ", false)).card().points());
    assertEquals(0, policy.choose(operatingFriendPair("DK DJ", true)).card().points());
  }

  @Test
  @DisplayName("상위 소모 후 숫자 9로 T를 찾으면 친구가 T를 보여준다")
  void trumpProbeContinuesBelowFixedFaceRanks() {
    var first = row(1, Trump.HEART, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var second = row(2, Trump.HEART, p(0, "HA"), p(1, "HK"), p(2, "HQ"), p(3, "HJ"), p(4, "H2"));
    View v = view(1, 0, Trump.HEART, 16, "H10 H3 D2 D3 D4 S2 S3 C6", "",
        new CardFriend(card("H10")), new RevealedFriend(new Seat(1)), List.of(first, second),
        List.of(p(0, "H9")));
    assertEquals(card("H10"), policy.choose(v).card());
  }

  @Test
  @DisplayName("야당이 A를 마이티에 소모해도 주공은 승격 K를 조커보다 먼저 쓴다")
  void promotedTrumpUsesPublicCountRegardlessWhoSpentAce() {
    var first = row(1, Trump.HEART, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var main = row(2, Trump.HEART, p(0, "SA"), p(1, "HA"), p(2, "H2"), p(3, "H3"), p(4, "H4"));
    View v = view(0, 0, Trump.HEART, 16, "HK HQ JK H5 D2 D3 S2 S3", "D8 D9 D10",
        new CardFriend(card("C2")), new RevealedFriend(new Seat(1)), List.of(first, main),
        List.of());
    assertEquals(card("HK"), policy.choose(v).card());
  }

  @Test
  @DisplayName("초간을 친 야당의 후속 간에는 미공개 프렌드라도 조커를 낭비하지 않는다")
  void identifiesOppositionOpeningAttackerBeforeFriendReveal() {
    var first = row(1, Trump.HEART, p(0, "CA"), p(1, "H2"), p(2, "C2"), p(3, "C3"), p(4, "C4"));
    var second = row(2, Trump.HEART, p(1, "S2"), p(2, "S3"), p(3, "S4"), p(4, "S5"), p(0, "SA"));
    View v = view(2, 0, Trump.HEART, 16, "JK H5 H6 H7 D2 D3 C6 C7", "",
        new CardFriend(card("SQ")), UnrevealedFriend.INSTANCE, List.of(first, second),
        List.of(p(0, "CK"), p(1,
            "H10")));
    assertTrue(new PlayFacts(v).teams().isAlly(new Seat(1)));
    assertInstanceOf(UnrevealedFriend.class, v.context().friendStatus());
    assertNotEquals(card("JK"), policy.choose(v).card());
  }

  @Test
  @DisplayName("주공은 초간과 마공 뒤 이미 간 당한 클럽 대신 기루 소모를 택한다")
  void declarerAvoidsKnownRuffSuitAfterMightyRecovery() {
    var first = row(1, Trump.HEART, p(0, "CA"), p(1, "H2"), p(2, "C2"), p(3, "C3"), p(4, "C4"));
    var second = row(2, Trump.HEART, p(1, "S2"), p(2, "S3"), p(3, "S4"), p(4, "S5"), p(0, "SA"));
    View v = view(0, 0, Trump.HEART, 16, "HA HK H5 H6 CK C5 D2 D3", "D8 D9 D10",
        new CardFriend(card("SQ")), UnrevealedFriend.INSTANCE, List.of(first, second), List.of());
    assertTrue(Trump.HEART.matches(((StandardCard) policy.choose(v).card()).suit()));
  }

  @Test
  @DisplayName("노기루와 기루 모두 주공 운영 문양을 막는 최고 카드를 먼저 내주지 않는다")
  void oppositionKeepsOperatingStopperAcrossContracts() {
    for (Trump trump : List.of(Trump.NO_TRUMP, Trump.DIAMOND)) {
      var first = row(1, trump, p(0, "C4"), p(1, "CA"), p(2, "C2"), p(3, "C3"), p(4, "C5"));
      var second = row(2, trump, p(1, "D10"), p(2, "D2"), p(3, "DK"), p(4, "D3"), p(0, "SA"));
      var joker = new PublicPlay(new Seat(0), new JokerLead(Suit.DIAMOND));
      var third = row(3, trump, joker, p(1, "DQ"), p(2, "D5"), p(3, "D6"), p(4, "D8"));
      var fourth = row(4, trump, p(0, "D9"), p(1, "D4"), p(2, "DJ"), p(3, "D7"), p(4, "C6"));
      View v = view(2, 0, trump, 17, "DA C7 C8 H2 H3 H4", "",
          new CardFriend(card("CA")), new RevealedFriend(new Seat(1)),
          List.of(first, second, third, fourth), List.of());
      assertNotEquals(card("DA"), policy.choose(v).card());
    }
  }

  @Test
  @DisplayName("상위 소모 후 9의 T 탐색에는 마이티를 숨기고 낮은 카드로 따른다")
  void mightyFriendRecognizesNumericProbeAfterCounting() {
    var first = row(1, Trump.HEART, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var second = row(2, Trump.HEART, p(0, "HA"), p(1, "HK"), p(2, "HQ"), p(3, "HJ"), p(4, "H2"));
    View v = view(1, 0, Trump.HEART, 16, "SA H3 D2 D3 D4 S2 S3 C6", "",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(1)), List.of(first, second),
        List.of(p(0, "H9")));
    assertEquals(card("H3"), policy.choose(v).card());
  }

  @Test
  @DisplayName("숫자 탐색의 목표 T가 이미 나왔으면 숨기기를 멈추고 마이티로 잡는다")
  void numericProbeEndsWhenTargetAppears() {
    var first = row(1, Trump.HEART, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var second = row(2, Trump.HEART, p(0, "HA"), p(1, "HK"), p(2, "HQ"), p(3, "HJ"), p(4, "H2"));
    View v = view(2, 0, Trump.HEART, 16, "SA H3 D2 D3 D4 S2 S3 C6", "",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(2)), List.of(first, second),
        List.of(p(0, "H9"),
            p(1, "H10")));
    assertEquals(card("SA"), policy.choose(v).card());
  }

  @Test
  @DisplayName("마이티인 삽A를 뒤 야당의 일반 A로 가정해 QJT를 지원하지 않는다")
  void doesNotFeedPointsIntoDeclarerSpadeKing() {
    String[] hands = {
        "SQ S2 H2 H3 H4 D2 D3 D4 C2 C3",
        "SJ S3 H5 H6 H7 D5 D6 D7 C4 C5",
        "S10 S4 H8 H9 H10 D8 D9 D10 C6 C7"
    };
    List<PublicPlay> trick = new ArrayList<>(List.of(p(0, "SK")));
    for (int seat = 1; seat <= 3; seat++) {
      View v = view(seat, 0, Trump.HEART, 17, hands[seat - 1], "",
          new CardFriend(card("JK")), UnrevealedFriend.INSTANCE, List.of(), trick);
      Card chosen = policy.choose(v).card();
      assertEquals(0, chosen.points(), "자리 " + seat);
      trick.add(new PublicPlay(new Seat(seat), new StandardPlay((StandardCard) chosen, false)));
    }
  }

  @Test
  @DisplayName("풀노의 낮은 사이드 호출에는 일반 A보다 지정 마이티로 응답한다")
  void noTrumpCalledMightyPrecedesOrdinarySideAce() {
    View v = view(1, 0, Trump.NO_TRUMP, 20, "SA CA D6 D3 H5 HQ D10 DJ C2 HJ", "",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(), List.of(p(0, "C5")));
    assertEquals(card("SA"), policy.choose(v).card());
  }

  @Test
  @DisplayName("노기루 A프렌드는 초구에 무효 조커를 버린다")
  void noTrumpAceFriendDisposesDeadJokerBeforeGun() {
    View v = view(1, 0, Trump.NO_TRUMP, 17, "CA JK C2 D2 D3 H2 H3 H4 S2 S3", "",
        new CardFriend(card("CA")), UnrevealedFriend.INSTANCE, List.of(), List.of(p(0, "HK")));
    assertEquals(card("JK"), policy.choose(v).card());
  }

  @Test
  @DisplayName("노기루 주력 숫자는 흘리기용 사이드 물패로 취급하지 않는다")
  void protectsMainNumbersWhenSideWaterCanBeDiscarded() {
    var first = row(1, Trump.NO_TRUMP, p(0, "SK"), p(1, "S2"), p(2, "S4"), p(3, "S6"), p(4, "S9"));
    var second = row(2, Trump.NO_TRUMP, p(0, "D2"), p(1, "D3"), p(2, "DA"), p(3, "D4"), p(4, "D5"));
    View v = view(0, 0, Trump.NO_TRUMP, 17, "SQ SJ S10 S8 S7 C2 H2 JK", "C3 C4 C5",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(first, second),
        List.of(p(2, "D6"), p(3,
            "D7"), p(4, "D8")));
    assertFalse(List.of(card("SQ"), card("SJ"), card("S10"), card("S8"), card("S7"))
        .contains(policy.choose(v).card()));
  }

  @Test
  @DisplayName("자력 여덟 구가 가능한 곱노는 물패 호출부터 시작하지 않는다")
  void delayedFriendCallStartsWithOwnWinningRun() {
    View v = view(0, 0, Trump.NO_TRUMP, 17, "D4 D6 D7 DJ DQ DK DA S4 SA C3", "C4 H2 H3",
        new CardFriend(card("JK")), UnrevealedFriend.INSTANCE, List.of(), List.of());
    assertEquals(card("DA"), policy.choose(v).card());
  }

  @Test
  @DisplayName("조커 프렌드는 초구 A에 승격 탑카 K를 점수로 버리지 않는다")
  void jokerFriendKeepsPromotedTopOnOpeningAce() {
    View v = view(1, 0, Trump.NO_TRUMP, 17, "HK H2 JK D2 D3 C2 C3 S2 S3 S4", "",
        new CardFriend(card("JK")), UnrevealedFriend.INSTANCE, List.of(), List.of(p(0, "HA")));
    assertEquals(card("H2"), policy.choose(v).card());
  }

  @Test
  @DisplayName("순수 곱노에서도 실제 마이티 지원 문양을 반환한다")
  void returnsActualMightySupportWithoutSuitAnnouncement() {
    var first = row(1, Trump.NO_TRUMP, p(0, "DA"), p(1, "D2"), p(2, "D3"), p(3, "D4"), p(4, "D5"));
    var second = row(2, Trump.NO_TRUMP, p(0, "H2"), p(1, "SA"), p(2, "H5"), p(3, "H6"), p(4, "HA"));
    View v = view(1, 0, Trump.NO_TRUMP, 17, "D6 H10 HJ S2 S3 C2 C3 C4", "",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(1)), List.of(first, second),
        List.of());
    assertEquals(card("HJ"), policy.choose(v).card());
  }

  @Test
  @DisplayName("순수 곱노의 조커 지원도 같은 문양 반환 원칙을 사용한다")
  void returnsActualJokerSupportWithoutSuitAnnouncement() {
    var first = row(1, Trump.NO_TRUMP, p(0, "DA"), p(1, "D2"), p(2, "D3"), p(3, "D4"), p(4, "D5"));
    var second = row(2, Trump.NO_TRUMP, p(0, "H2"), p(1, "JK"), p(2, "H5"), p(3, "H6"), p(4, "HA"));
    View v = view(1, 0, Trump.NO_TRUMP, 17, "D6 H10 HJ S2 S3 C2 C3 C4", "",
        new CardFriend(card("JK")), new RevealedFriend(new Seat(1)), List.of(first, second),
        List.of());
    assertEquals(card("HJ"), policy.choose(v).card());
  }

  @Test
  @DisplayName("반환할 문양이 있어도 노기루의 다른 탑카를 먼저 해준다")
  void cashesTopBeforeReturningSupportedSuit() {
    var first = row(1, Trump.NO_TRUMP, p(0, "DA"), p(1, "D2"), p(2, "D3"), p(3, "D4"), p(4, "D5"));
    var second = row(2, Trump.NO_TRUMP, p(0, "H2"), p(1, "SA"), p(2, "H5"), p(3, "H6"), p(4, "HA"));
    View v = view(1, 0, Trump.NO_TRUMP, 17, "D6 H10 HJ S2 S3 CA C3 C4", "",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(1)), List.of(first, second),
        List.of());
    assertEquals(card("CA"), policy.choose(v).card());
  }

  @Test
  @DisplayName("회수 사이드가 준비된 긴 K 주력은 마이티를 먼저 찾는다")
  void callsSupportBeforeSpendingIndependentEntries() {
    View v = view(0, 0, Trump.NO_TRUMP, 17, "SK DA H2 H3 H4 H7 H8 H9 HK JK", "SQ DJ C2",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(), List.of());
    assertEquals(card("H2"), policy.choose(v).card());
  }

  @Test
  @DisplayName("지정 A 프렌드를 야당 상위 공격 카드로 가정하지 않는다")
  void designatedAceIsNotAnOppositionAttack() {
    View v = view(1, 0, Trump.DIAMOND, 16, "HQ H2 C2 C3 C4 S2 S3 S4 D2 D3", "",
        new CardFriend(card("HA")), UnrevealedFriend.INSTANCE, List.of(), List.of(p(0, "HK")));
    assertEquals(card("H2"), policy.choose(v).card());
  }

  @Test
  @DisplayName("주공이 흘렸고 뒤 야당이 문양을 잡을 수 있으면 작은 점수에 A간을 아낀다")
  void leavesRecoveryTrumpForLaterAlly() {
    var first = row(1, Trump.SPADE, p(2, "CA"), p(3, "C2"), p(4, "C3"), p(0, "DA"), p(1, "C4"));
    var second = row(2, Trump.SPADE, new PublicPlay(new Seat(0), new JokerLead(Suit.CLUB)),
        p(1, "C6"), p(2,
            "C7"), p(3, "C8"), p(4, "C9"));
    View v = view(3, 2, Trump.SPADE, 16, "SA S2 S3 H2 H3 H4 H5 H6", "",
        new CardFriend(card("DA")), new RevealedFriend(new Seat(0)), List.of(first, second),
        List.of(p(0, "D3"), p(1, "H10"), p(2, "C5")));
    assertFalse(v.contract().trump().matches(((StandardCard) policy.choose(v).card()).suit()));
  }

  @Test
  @DisplayName("같은 상황이라도 이미 3점이 실렸으면 간으로 확보한다")
  void capturesThreePointsInsteadOfDeferringRuff() {
    var first = row(1, Trump.SPADE, p(2, "CA"), p(3, "C2"), p(4, "C3"), p(0, "DA"), p(1, "C4"));
    var second = row(2, Trump.SPADE, new PublicPlay(new Seat(0), new JokerLead(Suit.CLUB)),
        p(1, "C6"), p(2,
            "C7"), p(3, "C8"), p(4, "C9"));
    View v = view(3, 2, Trump.SPADE, 16, "SA S2 S3 H2 H3 H4 H5 H6", "",
        new CardFriend(card("DA")), new RevealedFriend(new Seat(0)), List.of(first, second),
        List.of(p(0, "DJ"), p(1, "H10"), p(2, "CQ")));
    assertTrue(v.contract().trump().matches(((StandardCard) policy.choose(v).card()).suit()));
  }

  @Test
  @DisplayName("낮은 기루를 비탑으로 잡은 자리의 상위 보유 가능성을 올린다")
  void infersHigherTrumpFromNonTopCapture() {
    var first = row(1, Trump.HEART, p(0, "HA"), p(1, "H2"), p(2, "H3"), p(3, "H4"), p(4, "H5"));
    var second = row(2, Trump.HEART, p(0, "H6"), p(1, "HJ"), p(2, "H7"), p(3, "H8"), p(4, "H9"));
    View v = view(3, 0, Trump.HEART, 17, "SK S2 D2 D3 C2 C3 H10 HK", "",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(4)), List.of(first, second),
        List.of(p(1, "D4"),
            p(2, "D5")));
    var facts = new PlayFacts(v);
    // 관측자가 자기 카드가 아닌 Q를 추정할 때, 자기 자리의 실제 보유를 몰래 추가하지 않습니다.
    var probabilities = facts.cards().trumps().ownerProbabilities((StandardCard) card("HQ"), facts);
    assertTrue(probabilities.getOrDefault(new Seat(0), 0.0) < 0.25);
    assertTrue(probabilities.getOrDefault(new Seat(1), 0.0) > 0.7);
    assertTrue(probabilities.values().stream().mapToDouble(Double::doubleValue).sum() <= 1.00001);
    assertTrue(facts.cards().outsideCards().contains(card("HQ")));
  }

  @Test
  @DisplayName("잔여 기루와 주공 손패로 마지막 사이드 공격 기회를 추정한다")
  void estimatesLastSideFromRemainingTrumpAndHandSize() {
    String owned = "C10 C3 D2 D3";
    var current = List.of(p(0, "S2"), p(1, "S3"), p(2, "S4"), p(3, "C4"));
    var history = countedHistory(owned, "", "HA H2 H3 H4 H5 H6 H7 H8 H9 HJ", "HQ HK H10", current,
        null);
    View v = view(4, 0, Trump.HEART, 16, owned, "", new CardFriend(card("SA")),
        new RevealedFriend(new Seat(1)),
        history, current);
    var facts = new PlayFacts(v);
    assertEquals(3, facts.cards().trumps().remainingCount());
    assertEquals(3, facts.cards().trumps().remainingHand(new Seat(0)));
    assertTrue(facts.cards().trumps().likelyLastSide(new Seat(0), facts));
    assertEquals(3, facts.cards().trumps().outside().size());
    assertEquals(card("C10"), policy.choose(v).card());
    assertTrue(policy.recommend(v).reason().contains("남은 공격 기회"));
  }

  @Test
  @DisplayName("닷노는 자력 클로버 대신 다리 물패에서 마이티를 찾는다")
  void noTrumpCallsMightyInWeakSide() {
    View v = view(0, 0, Trump.NO_TRUMP, 15, "SK D3 D6 DK C6 C7 CQ CK CA JK", "H2 H3 H4",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(), List.of());
    assertEquals(card("D3"), policy.choose(v).card());
    var first = row(1, Trump.NO_TRUMP, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    View after = view(0, 0, Trump.NO_TRUMP, 15, "SK D3 D6 DK C6 C7 CQ CK JK", "H2 H3 H4",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(first), List.of());
    assertEquals(card("D3"), policy.choose(after).card());
  }

  @Test
  @DisplayName("엿다리 초구의 끌은 마이티보다 끌A로 먼저 받는다")
  void friendUsesSideAceBeforeMighty() {
    View v = view(1, 0, Trump.DIAMOND, 16, "SA CA C2 D2 D3 D4 H2 H3 S2 S3", "",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(), List.of(p(0, "C4")));
    assertEquals(card("CA"), policy.choose(v).card());
  }

  @Test
  @DisplayName("야당이 비주력 문양을 내면 뒤 주공의 요청을 기다리며 일반 A로 먼저 지원한다")
  void ordinaryAceSupportsNonPrimaryOppositionLeadBeforeBehindDeclarer() {
    var first = row(1, Trump.NO_TRUMP, p(0, "C2"), p(1, "C3"), p(2, "CA"), p(3, "C4"), p(4, "C5"));
    View v = view(3, 0, Trump.NO_TRUMP, 15, "SA HA H2 D2 D3 C6 S2 S3 S4", "",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(first), List.of(p(2, "H3")));
    assertEquals(card("HA"), policy.choose(v).card());
  }

  @Test
  @DisplayName("마지막 자리에서 K를 잡고 Q가 승격되면 일반 A를 쓸 수 있다")
  void friendKeepsMightyWhenCapturePromotesQueen() {
    var first = row(1, Trump.HEART, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    View v = view(4, 0, Trump.HEART, 16, "SA HA HQ H3 D2 D3 S2 S3 C6", "",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(first),
        List.of(p(0, "H2"), p(1, "H4"),
            p(2, "H5"), p(3, "HK")));
    assertEquals(card("HA"), policy.choose(v).card());
  }

  @Test
  @DisplayName("앞자리에서도 기루 AKQ 연속 승리 계획이면 일반 A를 쓸 수 있다")
  void friendCanStartContinuousTrumpRun() {
    var first = row(1, Trump.HEART, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    View v = view(1, 0, Trump.HEART, 16, "SA HA HK HQ D2 D3 S2 S3 C6", "",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(first), List.of(p(0, "H2")));
    assertEquals(card("HA"), policy.choose(v).card());
  }

  @Test
  @DisplayName("노기루 마이티 대기 중에는 반환용 삽 한 장을 버리지 않는다")
  void reservesOrdinarySpadeBesideMighty() {
    View v = view(1, 0, Trump.NO_TRUMP, 15, "SA S2 D2 H2 H3 H4 H5 H6 H7 H8", "",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(), List.of(p(0, "CA")));
    assertTrue(mighty.ai.play.friend.FriendReturnReserve.protects(v, card("S2")));
    assertNotEquals(card("S2"), policy.choose(v).card());
    assertNotEquals(card("D2"), policy.choose(v).card());
  }

  @Test
  @DisplayName("기루 게임에는 문양별 노기루 반환 보존을 적용하지 않는다")
  void returnReserveIsNoTrumpOnly() {
    View v = view(1, 0, Trump.DIAMOND, 16, "SA S2 D2 H2 H3 H4 H5 H6 H7 H8", "",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(), List.of(p(0, "CA")));
    assertFalse(mighty.ai.play.friend.FriendReturnReserve.protects(v, card("S2")));
    assertFalse(mighty.ai.play.friend.FriendReturnReserve.protects(v, card("D2")));
  }

  @Test
  @DisplayName("일곱 구의 호출 대기는 강제 보존을 풀고 실제 운영을 재평가한다")
  void releasesReturnReserveNearEnd() {
    String owned = "SA S2 D2 C2";
    var current = List.of(p(0, "CA"));
    var history = countedHistory(owned, "", "", "", current, null);
    View v = view(1, 0, Trump.NO_TRUMP, 15, owned, "", new CardFriend(card("SA")),
        UnrevealedFriend.INSTANCE,
        history, current);
    assertEquals(7, v.trickNumber().value());
    assertFalse(mighty.ai.play.friend.FriendReturnReserve.protects(v, card("S2")));
  }

  @Test
  @DisplayName("반환 보존도 합법적인 강제 따라내기를 막지 않는다")
  void reserveDoesNotBreakForcedFollow() {
    View v = view(1, 0, Trump.NO_TRUMP, 15, "SA S2 D2 C2 H2 H3 H4 H5 H6 H7", "",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(), List.of(p(0, "CA")));
    assertEquals(card("C2"), policy.choose(v).card());
  }

  @Test
  @DisplayName("앞의 점수 압박에 뒤 야당 Q와 A를 중복하지 않는다")
  void avoidsStackingHonoursOnOpeningPressure() {
    for (String[] sample : List.of(new String[]{
        "DJ", "DQ"
    }, new String[]{
        "DK", "DA"
    })) {
      View v = view(2, 0, Trump.HEART, 16, sample[1] + " D3 D4 C2 C3 C4 S2 S3 H2 H3", "",
          new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(),
          List.of(p(0, "D2"), p(1, sample[0])));
      assertEquals(card("D3"), policy.choose(v).card());
    }
  }

  @Test
  @DisplayName("짧은 부기루의 첫 야당은 숫자가 아닌 최소 점수로 압박한다")
  void firstShortSuitPressureUsesPoint() {
    View v = view(1, 0, Trump.HEART, 16, "DJ D3 D4 C2 C3 C4 S2 S3 H2 H3", "",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(), List.of(p(0, "D2")));
    assertEquals(card("DJ"), policy.choose(v).card());
  }

  @Test
  @DisplayName("앞에 두 점이 실린 기루는 A 세 장 보존보다 세 점 확보를 우선한다")
  void takesTwoLoadedPointsWithTrumpAce() {
    var first = row(1, Trump.HEART, p(2, "C2"), p(3, "C3"), p(4, "C4"), p(0, "SA"), p(1, "C5"));
    View v = view(3, 2, Trump.HEART, 16, "HA H3 H4 D2 D3 D4 S2 S3 C6", "",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(0)), List.of(first),
        List.of(p(0, "H10"), p(1,
            "H2"), p(2, "HK")));
    assertEquals(card("HA"), policy.choose(v).card());
  }

  @Test
  @DisplayName("미공개 기루 반환 프렌드만 남았다면 추가 기루 교환을 멈춘다")
  void stopsTrumpExchangeWithInferredFriendAndShortOpposition() {
    var first = row(1, Trump.HEART, p(0, "C2"), p(1, "CK"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var second = row(2, Trump.HEART, p(1, "H2"), p(2, "H3"), p(3, "H4"), p(4, "H5"), p(0, "HA"));
    var third = row(3, Trump.HEART, p(0, "HK"), p(1, "H6"), p(2, "HQ"), p(3, "HJ"), p(4, "H10"));
    View v = view(0, 0, Trump.HEART, 16, "H8 H9 D2 D3 S2 S3 C6", "D4 D5 D6",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(first, second, third),
        List.of());
    var facts = new PlayFacts(v);
    assertEquals(new Seat(1), facts.teams().friendSeat().orElseThrow());
    assertTrue(facts.cards().trumps().avoidExchange(facts));
    assertTrue(facts.cards().outsideCards().contains(card("H7")));
    assertFalse(v.contract().trump().matches(((StandardCard) policy.choose(v).card()).suit()));
  }

  @Test
  @DisplayName("초구 점수의 마이티 지원 문양을 동료 간 공격에 사용한다")
  void attacksOpeningDevelopmentWhenAllyCanRuff() {
    var first = row(1, Trump.SPADE, p(0, "D10"), p(1, "DA"), p(2, "D2"), p(3, "D3"), p(4, "H2"));
    var second = row(2, Trump.SPADE, p(1, "C2"), p(2, "JK"), p(3, "C3"), p(4, "C4"), p(0, "C5"));
    View v = view(2, 0, Trump.SPADE, 16, "S2 S3 D4 D5 D6 C6 C7 H3", "",
        new CardFriend(card("DA")), new RevealedFriend(new Seat(1)), List.of(first, second),
        List.of());
    var facts = new PlayFacts(v);
    assertTrue(DeclarerOperations.contains(v, facts, Suit.DIAMOND));
    assertEquals(card("D4"), policy.choose(v).card());
  }

  @Test
  @DisplayName("간이 부족하고 맞을 카드가 많은 주공에게 부기루를 돌려주지 않는다")
  void doesNotDevelopDeclarerSideWhenTrumpIsGone() {
    var first = row(1, Trump.SPADE, p(0, "D10"), p(1, "DA"), p(2, "D2"), p(3, "D3"), p(4, "H2"));
    var second = row(2, Trump.SPADE, p(1, "C2"), p(2, "JK"), p(3, "C3"), p(4, "C4"), p(0, "C5"));
    var third = row(3, Trump.SPADE, p(2, "SA"), p(3, "SK"), p(4, "SQ"), p(0, "SJ"), p(1, "S2"));
    var fourth = row(4, Trump.SPADE, p(2, "S9"), p(3, "S3"), p(4, "S4"), p(0, "H4"), p(1, "S5"));
    View v = view(2, 0, Trump.SPADE, 16, "D4 D5 D6 C6 C7 H3", "",
        new CardFriend(card("DA")), new RevealedFriend(new Seat(1)),
        List.of(first, second, third, fourth), List.of());
    assertNotEquals(Suit.DIAMOND, ((StandardCard) policy.choose(v).card()).suit());
  }

  @Test
  @DisplayName("연결된 기루는 낮은 호출 대신 유일한 상위부터 탐색한다")
  void declarerProbesOnlyMissingHighTrumpBeforeCallingMighty() {
    var first = row(1, Trump.HEART, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    View v = view(0, 0, Trump.HEART, 16, "HK HQ HJ H10 H2 C6 D2 D3 S2", "D4 D5 D6",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(first), List.of());
    assertEquals(card("HK"), policy.choose(v).card());
  }

  @Test
  @DisplayName("압박 중복 방지도 이미 두 점이 실린 실제 승리를 막지 않는다")
  void openingPressureDoesNotPassLoadedCapture() {
    View v = view(3, 0, Trump.HEART, 16, "DA D3 D4 C2 C3 C4 S2 S3 H2 H3", "",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(),
        List.of(p(0, "D2"), p(1, "DK"), p(2,
            "S10")));
    assertEquals(card("DA"), policy.choose(v).card());
  }

  @Test
  @DisplayName("노기루 마지막 자리의 K 회수는 보이지 않는 A 때문에 조커로 대체하지 않는다")
  void takesKnownLastSeatWinWithoutSpendingJoker() {
    var first = row(1, Trump.NO_TRUMP, p(0, "D2"), p(1, "SA"), p(2, "D4"), p(3, "D8"), p(4, "D9"));
    View v = view(0, 0, Trump.NO_TRUMP, 15, "DK C6 C7 CQ CK CA JK SK H2", "H3 H4 H5",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(1)), List.of(first),
        List.of(p(1, "D3"), p(2, "D5"), p(3, "D6"), p(4, "D7")));
    assertEquals(card("DK"), policy.choose(v).card());
  }

  @Test
  @DisplayName("새 기루 J 요청은 상위 탐색으로 오해하지 않고 조커로 받는다")
  void freshTrumpRequestUsesJoker() {
    var first = row(1, Trump.DIAMOND, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    View v = view(1, 0, Trump.DIAMOND, 16, "JK D2 D3 D4 D5 H2 H6 C6 S2", "",
        new CardFriend(card("JK")), UnrevealedFriend.INSTANCE, List.of(first), List.of(p(0, "DJ")));
    assertEquals(card("JK"), policy.choose(v).card());
  }

  @Test
  @DisplayName("새 기루 J 요청은 A보다 마이티로 먼저 지원한다")
  void freshTrumpRequestUsesMightyBeforeAce() {
    var first = row(1, Trump.DIAMOND, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    View v = view(1, 0, Trump.DIAMOND, 16, "SA DA D2 D3 D4 H2 H6 C6 S2", "",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(first), List.of(p(0, "DJ")));
    assertEquals(card("SA"), policy.choose(v).card());
  }

  @Test
  @DisplayName("기루에 마이티를 쓴 뒤 최고 일반 기루를 조커보다 먼저 연결한다")
  void friendConnectsTopTrumpBeforeJoker() {
    var first = row(1, Trump.DIAMOND, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var second = row(2, Trump.DIAMOND, p(0, "D2"), p(1, "SA"), p(2, "D3"), p(3, "D4"), p(4, "D5"));
    View v = view(1, 0, Trump.DIAMOND, 16, "DA JK D6 D7 H2 H6 C6 S2", "",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(1)), List.of(first, second),
        List.of());
    assertEquals(card("DA"), policy.choose(v).card());
  }

  @Test
  @DisplayName("친구가 A K Q를 빼줬으면 조커 없이도 승격 J로 연결한다")
  void declarerContinuesPromotedTrumpWithoutOwnJoker() {
    var first = row(1, Trump.DIAMOND, p(0, "C2"), p(1, "CK"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var second = row(2, Trump.DIAMOND, p(1, "DA"), p(2, "DK"), p(3, "DQ"), p(4, "D2"), p(0, "D3"));
    var third = row(3, Trump.DIAMOND, p(1, "D4"), p(2, "C7"), p(3, "D5"), p(4, "D6"), p(0, "D10"));
    View v = view(0, 0, Trump.DIAMOND, 16, "DJ D8 D9 S2 S3 H2 C6", "H3 H4 H5",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(first, second, third),
        List.of());
    assertEquals(card("DJ"), policy.choose(v).card());
  }

  @Test
  @DisplayName("프렌드의 물패에 두 번째 야당은 미소모 최고 기루로 바로 간치지 않는다")
  void keepsTopTrumpOnLowFriendLead() {
    var first = row(1, Trump.HEART, p(0, "CA"), p(1, "SA"), p(2, "C2"), p(3, "C3"), p(4, "C4"));
    var second = row(2, Trump.HEART, new PublicPlay(new Seat(1), new JokerLead(Suit.CLUB)),
        p(2, "C5"), p(3, "C6"), p(4, "C7"), p(0, "C8"));
    View v = view(2, 0, Trump.HEART, 16, "HA H2 H3 S2 S3 S4 C9 C10", "",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(1)), List.of(first, second),
        List.of(p(1, "D2")));
    assertEquals(card("S2"), policy.choose(v).card());
  }

  @Test
  @DisplayName("주공 바로 앞 야당은 앞에 두 점이 모일 때만 프렌드 물패에 탑 기루로 간친다")
  void spendsTopTrumpBeforeDeclarerOnlyForTwoPointsAlreadyPlayed() {
    var first = row(1, Trump.HEART, p(0, "CA"), p(1, "SA"), p(2, "C2"), p(3, "C3"), p(4, "C4"));
    var second = row(2, Trump.HEART, new PublicPlay(new Seat(1), new JokerLead(Suit.CLUB)),
        p(2, "C5"), p(3, "C6"), p(4, "C7"), p(0, "C8"));
    for (int points = 0; points <= 2; points++) {
      View v = view(4, 0, Trump.HEART, 16, "HA S2 S3 S4 S5 C9 C10 CJ", "",
          new CardFriend(card("SA")), new RevealedFriend(new Seat(1)), List.of(first, second),
          List.of(p(1, "D2"), p(2, points >= 1 ? "S10" : "S6"), p(3, points == 2 ? "SJ" : "S7")));
      assertEquals(card(points == 2 ? "HA" : "S2"), policy.choose(v).card());
    }
  }

  @Test
  @DisplayName("다른 야당이 무문양이고 뒤 주공의 운영 문양이면 한 점에도 탑 기루로 차단한다")
  void cutsOnePointTrickWhenDeclarerLikelyHoldsSideTop() {
    var first = row(1, Trump.HEART, p(0, "D10"), p(1, "SA"), p(2, "D3"), p(3, "D4"), p(4, "D5"));
    var second = row(2, Trump.HEART, new PublicPlay(new Seat(1), new JokerLead(Suit.CLUB)),
        p(2, "C5"), p(3, "C6"), p(4, "C7"), p(0, "C8"));
    View v = view(4, 0, Trump.HEART, 16, "HA S2 S3 S4 S5 C9 C10 CJ", "",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(1)), List.of(first, second),
        List.of(p(1, "D2"), p(2, "S10"), p(3, "S7")));
    assertEquals(card("HA"), policy.choose(v).card());
  }

  @Test
  @DisplayName("두 점이 있어도 뒤 주공이 간칠 수 있으면 야당 탑 기루를 남긴다")
  void leavesTwoPointTrickForDeclarerToRuff() {
    var first = row(1, Trump.HEART, p(0, "CA"), p(1, "SA"), p(2, "C2"), p(3, "C3"), p(4, "C4"));
    var second = row(2, Trump.HEART, new PublicPlay(new Seat(1), new JokerLead(Suit.DIAMOND)),
        p(2, "D5"), p(3, "D6"), p(4, "D7"), p(0, "C8"));
    View v = view(4, 0, Trump.HEART, 16, "HA S2 S3 S4 S5 C9 C10 CJ", "",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(1)), List.of(first, second),
        List.of(p(1, "D2"), p(2, "S10"), p(3, "SJ")));
    assertEquals(card("S2"), policy.choose(v).card());
  }

  @Test
  @DisplayName("주공이 낮게 맞으러 오고 친구도 흘렸으면 뒤 야당을 두고 A간 하지 않는다")
  void defersRuffAfterWeakDeclarerLeadAndFriendDiscard() {
    View v = view(2, 0, Trump.HEART, 16, "HA H2 D2 D3 D4 D5 C6 C7 C8 C9", "",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(1)), List.of(),
        List.of(p(0, "S2"), p(1, "C2")));
    assertFalse(Trump.HEART.matches(((StandardCard) policy.choose(v).card()).suit()));
  }

  @Test
  @DisplayName("주공이 흘린 뒤에는 수비 근거 없는 점수로 친구의 낮은 선두를 잡는다")
  void capturesWithPointWhenDeclarerHasDiscarded() {
    var first = row(1, Trump.HEART, p(2, "D2"), p(3, "D3"), p(4, "D4"), p(0, "SA"), p(1, "D6"));
    View v = view(2, 1, Trump.HEART, 17, "C10 C5 H2 H3 H4 S2 S3 D7 D8", "",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(0)), List.of(first),
        List.of(p(0, "C4"), p(1,
            "D5")));
    assertEquals(card("C10"), policy.choose(v).card());
  }

  @Test
  @DisplayName("노기루 주력 반환은 T 4 2의 높은 순서로 시작한다")
  void returnsNoTrumpPrimaryFromHighCard() {
    var first = row(1, Trump.NO_TRUMP, p(0, "S2"), p(1, "SK"), p(2, "S3"), p(3, "S4"), p(4, "S5"));
    View base = view(1, 0, Trump.NO_TRUMP, 20, "D2 D4 D10 C2 C3 C4 H2 H3 S6", "",
        new CardFriend(card("SK")), new RevealedFriend(new Seat(1)), List.of(first), List.of());
    var c = base.context();
    View v = ObservationFactory.create(base.self(), base.privateInfo(), base.contract(),
        base.friendSelection(),
        new Context(c.playOrder(), c.declarer(), c.friendStatus(), c.history(),
            List.of(new Bid(20, Trump.DIAMOND), new Bid(20, Trump.NO_TRUMP))), List.of());
    assertEquals(card("D10"), policy.choose(v).card());
  }

  @Test
  @DisplayName("K에서 시작한 상위 탐색은 A가 남아 있으면 Q에서도 계속 숨는다")
  void continuesEstablishedProbeWithoutSpendingMighty() {
    var first = row(1, Trump.DIAMOND, p(0, "DK"), p(1, "D2"), p(2, "D3"), p(3, "D4"), p(4, "D5"));
    View v = view(1, 0, Trump.DIAMOND, 17, "SA D6 H2 H3 H4 C2 C3 S2 S3", "",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(first), List.of(p(0, "DQ")));
    assertEquals(card("D6"), policy.choose(v).card());
  }

  @Test
  @DisplayName("주공의 낮은 기루에 뒤 야당의 남은 탑 기루로 점수를 지원한다")
  void feedsOppositionTopTrumpAfterDeclarerLowersLead() {
    var first = row(1, Trump.SPADE, p(0, "DA"), p(1, "JK"), p(2, "C2"), p(3, "C3"), p(4, "C4"));
    var second = row(2, Trump.SPADE, p(0, "SA"), p(1, "SK"), p(2, "SJ"), p(3, "S10"), p(4, "S2"));
    var friend = new RevealedFriend(new Seat(1));
    var history = List.of(first, second);
    View secondDefender = view(2, 0, Trump.SPADE, 16, "C10 C5 C6 H2 H3 H4 D3 D4", "",
        new CardFriend(card("JK")), friend, history, List.of(p(0, "S5"), p(1, "D2")));
    View thirdDefender = view(3, 0, Trump.SPADE, 16, "HJ H5 H6 H7 C7 C8 D6 D7", "",
        new CardFriend(card("JK")), friend, history, List.of(p(0, "S5"), p(1, "D2"), p(2, "C10")));
    assertEquals(card("C10"), policy.choose(secondDefender).card());
    assertEquals(card("HJ"), policy.choose(thirdDefender).card());
  }

  @Test
  @DisplayName("노기루 주력 반환을 Q로 받으면 조커컴 뒤 낮은 주력으로 상위를 빼고 사이드로 회수한다")
  void developsBlockedPrimaryBeforeCashingSideEntries() {
    var first = row(1, Trump.NO_TRUMP, p(0, "H5"), p(1, "SA"), p(2, "H2"), p(3, "H3"), p(4, "H4"));
    var second = row(2, Trump.NO_TRUMP, p(1, "H10"), p(2, "HJ"), p(3, "H6"), p(4, "D2"),
        p(0, "HQ"));
    var selection = new CardFriend(card("SA"));
    var friend = new RevealedFriend(new Seat(1));
    View afterReturn = view(0, 0, Trump.NO_TRUMP, 17, "SK DA DK H7 H8 H9 CA JK", "D3 S9 C6",
        selection, friend, List.of(first, second), List.of());
    assertEquals(new JokerLead(Suit.HEART), policy.choose(afterReturn));
    var third = row(3, Trump.NO_TRUMP, new PublicPlay(new Seat(0), new JokerLead(Suit.HEART)),
        p(1, "D4"), p(2, "HK"), p(3, "C2"), p(4, "S2"));
    View afterJoker = view(0, 0, Trump.NO_TRUMP, 17, "SK DA DK H7 H8 H9 CA", "D3 S9 C6",
        selection, friend, List.of(first, second, third), List.of());
    assertEquals(card("H7"), policy.choose(afterJoker).card());
    var fourth = row(4, Trump.NO_TRUMP, p(0, "H7"), p(1, "D5"), p(2, "HA"), p(3, "C3"), p(4, "S3"));
    View recovery = view(0, 0, Trump.NO_TRUMP, 17, "SK DA DK H8 H9 CA", "D3 S9 C6",
        selection, friend, List.of(first, second, third, fourth),
        List.of(p(2, "S4"), p(3, "S5"), p(4, "S6")));
    assertEquals(card("SK"), policy.choose(recovery).card());
    var fifth = row(5, Trump.NO_TRUMP, p(2, "S4"), p(3, "S5"), p(4, "S6"), p(0, "SK"), p(1, "S7"));
    View afterRecovery = view(0, 0, Trump.NO_TRUMP, 17, "DA DK H8 H9 CA", "D3 S9 C6",
        selection, friend, List.of(first, second, third, fourth, fifth), List.of());
    assertEquals(card("H9"), policy.choose(afterRecovery).card());
  }

  @Test
  @DisplayName("노기루 하트 A 프렌드는 삽Q로 잡았어도 A 사용 뒤 남은 하트를 반환한다")
  void returnsDesignatedSuitAfterCashingAce() {
    var first = row(1, Trump.NO_TRUMP, p(0, "S9"), p(1, "SQ"), p(2, "S2"), p(3, "S3"), p(4, "S4"));
    var second = row(2, Trump.NO_TRUMP, p(1, "HA"), p(2, "H2"), p(3, "H3"), p(4, "H4"), p(0, "H5"));
    View v = view(1, 0, Trump.NO_TRUMP, 16, "H8 H6 D2 D3 D4 C2 C4 C5", "",
        new CardFriend(card("HA")), new RevealedFriend(new Seat(1)), List.of(first, second),
        List.of());
    assertEquals(card("H8"), policy.choose(v).card());
  }

  @Test
  @DisplayName("마이티와 K로 삽이 굳으면 조커 삽컴 대신 하트 상위를 빼고 조커로 회수한다")
  void reservesJokerAfterPrimarySuitIsEstablished() {
    var first = row(1, Trump.NO_TRUMP, p(0, "SA"), p(1, "S4"), p(2, "S5"), p(3, "S6"), p(4, "S8"));
    var second = row(2, Trump.NO_TRUMP, p(0, "SK"), p(1, "S9"), p(2, "S10"), p(3, "SJ"),
        p(4, "SQ"));
    var selection = new CardFriend(card("HA"));
    var history = List.of(first, second);
    View afterSpades = view(0, 0, Trump.NO_TRUMP, 17, "S2 S3 S7 H10 HJ HQ CA JK", "D3 D4 C6",
        selection, UnrevealedFriend.INSTANCE, history, List.of());
    assertEquals(card("HQ"), policy.choose(afterSpades).card());
    // A 프렌드는 낮게 따르고 야당 K가 선을 잡습니다.
    var third = row(3, Trump.NO_TRUMP, p(0, "HQ"), p(1, "H4"), p(2, "HK"), p(3, "H2"), p(4, "H3"));
    View regain = view(0, 0, Trump.NO_TRUMP, 17, "S2 S3 S7 H10 HJ CA JK", "D3 D4 C6",
        selection, UnrevealedFriend.INSTANCE, List.of(first, second, third),
        List.of(p(2, "D5"), p(3, "D6"), p(4, "D7")));
    assertEquals(card("JK"), policy.choose(regain).card());
  }

  @Test
  @DisplayName("마이티 소모 후 여당 최고 기루에 짧은 점수나 여유 점수를 조커 지원으로 쓴다")
  void createsOppositionJokerOpportunityAfterMighty() {
    var first = row(1, Trump.HEART, p(0, "C2"), p(1, "C3"), p(2, "C4"), p(3, "C5"), p(4, "SA"));
    var selection = new CardFriend(card("SA"));
    var friend = new RevealedFriend(new Seat(4));
    var current = List.of(p(4, "H10"), p(0, "HA"));
    View voidTrump = view(1, 0, Trump.HEART, 16, "C10 D10 S10 C6 C7 D2 D3 S2 S3", "",
        selection, friend, List.of(first), current);
    View shortTrump = view(1, 0, Trump.HEART, 16, "HJ H2 C6 C7 D2 D3 S2 S3 S4", "",
        selection, friend, List.of(first), current);
    View ownsJoker = view(1, 0, Trump.HEART, 16, "JK H2 C6 C7 D2 D3 S2 S3 S4", "",
        selection, friend, List.of(first), current);
    assertEquals(card("C10"), policy.choose(voidTrump).card());
    assertEquals(card("HJ"), policy.choose(shortTrump).card());
    assertEquals(card("JK"), policy.choose(ownsJoker).card());
  }

  @Test
  @DisplayName("점수를 직접 싣지 않은 야당도 공개 진행을 보고 다음 선에서 조커콜한다")
  void sharesJokerCallEvidenceWithOtherDefenders() {
    var first = row(1, Trump.HEART, p(0, "C2"), p(1, "C4"), p(2, "C5"), p(3, "C6"), p(4, "SA"));
    var second = row(2, Trump.HEART, p(4, "H10"), p(0, "HA"), p(1, "C10"), p(2, "H2"), p(3, "H3"));
    var third = row(3, Trump.HEART, p(0, "D2"), p(1, "D3"), p(2, "DA"), p(3, "D4"), p(4, "D5"));
    View v = view(2, 0, Trump.HEART, 16, "C3 C7 C8 C9 S2 S3 S4", "",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(4)), List.of(first, second, third),
        List.of());
    assertEquals(new StandardPlay((StandardCard) card("C3"), true), policy.choose(v));
  }

  @Test
  @DisplayName("노기루 지정 A 프렌드는 마지막 자리에서도 AK를 처리하고 높은 물패로 반환한다")
  void aceFriendCashesRunAndReturnsLow() {
    var first = row(1, Trump.NO_TRUMP, p(0, "SA"), p(1, "S4"), p(2, "S5"), p(3, "S6"), p(4, "S8"));
    var selection = new CardFriend(card("HA"));
    var request = List.of(p(0, "H10"), p(1, "H2"), p(2, "H3"), p(3, "H4"));
    View answering = view(4, 0, Trump.NO_TRUMP, 17, "HA HK H5 H8 D2 D3 C2 C4 C5", "",
        selection, UnrevealedFriend.INSTANCE, List.of(first), request);
    assertEquals(card("HA"), policy.choose(answering).card());
    var second = row(2, Trump.NO_TRUMP, p(0, "H10"), p(1, "H2"), p(2, "H3"), p(3, "H4"),
        p(4, "HA"));
    var friend = new RevealedFriend(new Seat(4));
    View cashing = view(4, 0, Trump.NO_TRUMP, 17, "HK H5 H8 D2 D3 C2 C4 C5", "",
        selection, friend, List.of(first, second), List.of());
    assertEquals(card("HK"), policy.choose(cashing).card());
    var third = row(3, Trump.NO_TRUMP, p(4, "HK"), p(0, "HJ"), p(1, "H6"), p(2, "H7"), p(3, "H9"));
    View returning = view(4, 0, Trump.NO_TRUMP, 17, "H5 H8 D2 D3 C2 C4 C5", "",
        selection, friend, List.of(first, second, third), List.of());
    assertEquals(card("H8"), policy.choose(returning).card());
  }

  @Test
  @DisplayName("노기루 주공이 반복해서 돌린 문양의 Q를 지킬 숫자 카드를 버리지 않는다")
  void keepsLowGuardForRepeatedNoTrumpPrimarySuit() {
    var first = row(1, Trump.NO_TRUMP, p(0, "C2"), p(1, "CA"), p(2, "C4"), p(3, "C5"), p(4, "C6"));
    var second = row(2, Trump.NO_TRUMP, p(1, "D2"), p(2, "D3"), p(3, "D4"), p(4, "D5"), p(0, "SA"));
    var third = row(3, Trump.NO_TRUMP, p(0, "CK"), p(1, "S2"), p(2, "C8"), p(3, "C9"), p(4, "S3"));
    var fourth = row(4, Trump.NO_TRUMP, p(0, "C3"), p(1, "S4"), p(2, "CJ"), p(3, "S5"), p(4, "S6"));
    View v = view(2, 0, Trump.NO_TRUMP, 16, "CQ C7 D6 D7 D8 D9", "",
        SpecialFriend.FIRST_TRICK, new RevealedFriend(new Seat(1)),
        List.of(first, second, third, fourth),
        List.of());
    assertTrue(new CardResources(v, new PlayFacts(v)).reserved(card("C7")));
    var fourthByOtherDefender = row(4, Trump.NO_TRUMP, p(0, "C3"), p(1, "S4"), p(2, "C10"),
        p(3, "CJ"), p(4,
            "S6"));
    View discarding = view(2, 0, Trump.NO_TRUMP, 16, "CQ C7 D6 D7 D8 D9", "",
        SpecialFriend.FIRST_TRICK, new RevealedFriend(new Seat(1)),
        List.of(first, second, third, fourthByOtherDefender),
        List.of(p(3, "H2"), p(4, "H3"), p(0, "H4"), p(1, "H5")));
    assertEquals(Suit.DIAMOND, ((StandardCard) policy.choose(discarding).card()).suit());
  }

  @Test
  @DisplayName("닷다리에서 마쪼가 빠진 뒤 주공의 낮은 기루에 프렌드는 T 대신 9를 낸다")
  void friendKeepsPointWhenDeclarerLeadsLowIntoUnseenTop() {
    var first = row(1, Trump.DIAMOND, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var second = row(2, Trump.DIAMOND, p(0, "D2"), p(1, "SA"), p(2, "D3"), p(3, "D4"), p(4, "D5"));
    var third = row(3, Trump.DIAMOND, p(1, "DK"), p(2, "DA"), p(3, "DJ"), p(4, "D6"), p(0, "JK"));
    View v = view(1, 0, Trump.DIAMOND, 15, "D8 D9 D10 H2 H3 S2 S3", "",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(1)), List.of(first, second, third),
        List.of(p(0,
            "D7")));
    assertEquals(card("D9"), policy.choose(v).card());
  }

  @Test
  @DisplayName("주공의 작은 리드에 바로 다음 야당도 유일한 탑 기루를 남긴다")
  void holdsTopTrumpWhenDeclarerLeadsWaterBeforeFriendActs() {
    var first = row(1, Trump.HEART, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "SA"));
    var second = row(2, Trump.HEART, p(4, "D2"), p(0, "JK"), p(1, "D3"), p(2, "D4"), p(3, "D5"));
    View v = view(1, 0, Trump.HEART, 16, "HA S2 S3 S4 D6 D7 D8 D9", "",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(4)), List.of(first, second),
        List.of(p(0, "C6")));
    assertFalse(Trump.HEART.matches(((StandardCard) policy.choose(v).card()).suit()));
  }

  @Test
  @DisplayName("프렌드는 마이티 뒤 기루 AK를 처리했으면 사이드 A를 하고 조커를 남긴다")
  void friendCashesSideAceAfterTrumpRunInsteadOfJokerLead() {
    var first = row(1, Trump.DIAMOND, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var second = row(2, Trump.DIAMOND, p(0, "D2"), p(1, "SA"), p(2, "D3"), p(3, "D4"), p(4, "D5"));
    var third = row(3, Trump.DIAMOND, p(1, "DA"), p(2, "D6"), p(3, "D7"), p(4, "C6"), p(0, "D8"));
    var fourth = row(4, Trump.DIAMOND, p(1, "DK"), p(2, "D9"), p(3, "C7"), p(4, "C8"), p(0, "C9"));
    View v = view(1, 0, Trump.DIAMOND, 16, "JK HA H2 H3 S2 S3", "",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(1)),
        List.of(first, second, third, fourth), List.of());
    assertEquals(card("HA"), policy.choose(v).card());
  }

  @Test
  @DisplayName("야당이 앞서도 주공의 기루에 지는 비점수 카드는 높은 것부터 따른다")
  void signalsDescendingTrumpEvenWhenOppositionIsWinning() {
    var first = row(1, Trump.DIAMOND, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    View v = view(2, 0, Trump.DIAMOND, 15, "D3 D8 D10 H2 H3 H4 S2 S3 S4", "",
        new CardFriend(card("C3")), new RevealedFriend(new Seat(2)), List.of(first),
        List.of(p(0, "D2"), p(1,
            "DJ")));
    assertEquals(card("D8"), policy.choose(v).card());
  }

  @Test
  @DisplayName("엿노의 다리 K 프렌드로 다리를 맡기고 끌 반환은 마이티와 A로 연결한다")
  void splitNoTrumpSupportUsesMightyInOtherSuit() {
    var selection = new CardFriend(card("DK"));
    View opening = view(0, 0, Trump.NO_TRUMP, 16, "SA C4 C5 C8 CA D3 DQ DA HJ JK", "S2 S3 H2",
        selection, UnrevealedFriend.INSTANCE, List.of(), List.of());
    assertEquals(card("D3"), policy.choose(opening).card());
    var first = row(1, Trump.NO_TRUMP, p(0, "D3"), p(1, "DK"), p(2, "D4"), p(3, "D5"), p(4, "D6"));
    var friend = new RevealedFriend(new Seat(1));
    View returning = view(0, 0, Trump.NO_TRUMP, 16, "SA C4 C5 C8 CA DQ DA HJ JK", "S2 S3 H2",
        selection, friend, List.of(first), List.of(p(1, "C2"), p(2, "C3"), p(3, "C6"), p(4, "C7")));
    assertEquals(card("SA"), policy.choose(returning).card());
    var second = row(2, Trump.NO_TRUMP, p(1, "C2"), p(2, "C3"), p(3, "C6"), p(4, "C7"), p(0, "SA"));
    View next = view(0, 0, Trump.NO_TRUMP, 16, "C4 C5 C8 CA DQ DA HJ JK", "S2 S3 H2",
        selection, friend, List.of(first, second), List.of());
    assertEquals(card("CA"), policy.choose(next).card());
    var clearable = row(3, Trump.NO_TRUMP, p(0, "CA"), p(1, "C9"), p(2, "C10"), p(3, "CJ"),
        p(4, "CK"));
    View clearLast = view(0, 0, Trump.NO_TRUMP, 16, "C4 C5 C8 DQ DA HJ JK", "S2 S3 H2",
        selection, friend, List.of(first, second, clearable), List.of());
    assertEquals(new JokerLead(Suit.CLUB), policy.choose(clearLast));
    var crowded = row(3, Trump.NO_TRUMP, p(0, "CA"), p(1, "C9"), p(2, "C10"), p(3, "D7"),
        p(4, "D8"));
    View saveJoker = view(0, 0, Trump.NO_TRUMP, 16, "C4 C5 C8 DQ DA HJ JK", "S2 S3 H2",
        selection, friend, List.of(first, second, crowded), List.of());
    assertEquals(card("C4"), policy.choose(saveJoker).card());
  }

  @Test
  @DisplayName("초구프 앞자리에서는 7로도 앞설 수 있으면 불확실한 T를 쓰지 않는다")
  void firstFriendKeepsTenWhenSevenCanCompete() {
    View v = view(1, 0, Trump.DIAMOND, 16, "C7 C10 H2 H3 H4 D2 D3 D4 S2 S3", "",
        SpecialFriend.FIRST_TRICK, UnrevealedFriend.INSTANCE, List.of(), List.of(p(0, "C2")));
    assertEquals(card("C7"), policy.choose(v).card());
  }

  @Test
  @DisplayName("주공 마이티에 조커를 내고 KQ와 낮은 보호 기루를 남긴다")
  void jokerProtectsConnectedTrumpGuardOnMighty() {
    View afterMighty = view(1, 0, Trump.HEART, 16, "H3 HQ HK JK D2 D3 S2 S3 C7", "",
        SpecialFriend.FIRST_TRICK, new RevealedFriend(new Seat(2)),
        List.of(row(1, Trump.HEART, p(0, "C2"), p(1, "C4"), p(2, "CA"), p(3, "C5"), p(4, "C6"))),
        List.of(p(2, "H2"), p(3, "H4"), p(4, "H5"), p(0, "SA")));
    assertEquals(card("JK"), policy.choose(afterMighty).card());
    for (String hand : List.of("H3 HJ HK JK D2 D3 S2 S3 C7", "H3 H6 H7 HQ JK D2 D3 S2 S3")) {
      View guard = view(1, 0, Trump.HEART, 16, hand, "",
          SpecialFriend.FIRST_TRICK, new RevealedFriend(new Seat(2)),
          afterMighty.context().history(), afterMighty.trick());
      assertEquals(card("JK"), policy.choose(guard).card());
    }
  }

  @Test
  @DisplayName("여당이 이미 졌으면 야당의 승리에 간을 겹치지 않는다")
  void doesNotRuffWhenBothDeclarerAndFriendAlreadyLost() {
    View voidSide = view(4, 1, Trump.HEART, 16, "HA H2 S10 S2 S3 S4 C8 C9 C7", "",
        SpecialFriend.FIRST_TRICK, new RevealedFriend(new Seat(0)), List.of(
            row(1, Trump.HEART, p(1, "C2"), p(2, "C4"), p(3, "C5"), p(4, "C6"), p(0, "CA"))),
        List.of(p(0, "D4"), p(1, "D6"), p(2, "DA"), p(3, "D5")));
    assertEquals(card("S10"), policy.choose(voidSide).card());
  }

  @Test
  @DisplayName("곱삽 주공은 프렌드의 다리 물패를 일반 탑카 K로 회수한다")
  void takesFriendDiamondReturnWithKingInSpadeContract() {
    var first = row(1, Trump.SPADE, p(0, "C2"), p(1, "DA"), p(2, "C4"), p(3, "C5"), p(4, "C6"));
    var second = row(2, Trump.SPADE, p(1, "S2"), p(2, "C7"), p(3, "C8"), p(4, "C9"), p(0, "S3"));
    var third = row(3, Trump.SPADE, p(0, "SA"), p(1, "JK"), p(2, "D3"), p(3, "D4"), p(4, "D5"));
    var fourth = row(4, Trump.SPADE, p(0, "H2"), p(1, "HA"), p(2, "H3"), p(3, "H4"), p(4, "H5"));
    View v = view(0, 0, Trump.SPADE, 17, "DK D2 S4 H6 H7 H8", "D7 D8 D9",
        new CardFriend(card("DA")), new RevealedFriend(new Seat(1)),
        List.of(first, second, third, fourth),
        List.of(p(1, "D6"), p(2, "D10"), p(3, "DJ"), p(4, "DQ")));
    assertEquals(card("DK"), policy.choose(v).card());
  }

  @Test
  void callsMiddleFriendBeforeCashingOwnTops() {
    View v = view(0, 0, Trump.NO_TRUMP, 15, "SA SK S2 JK DA D3 H3 H4 C4 C5", "D4 D5 D6",
        new CardFriend(card("SQ")), UnrevealedFriend.INSTANCE, List.of(), List.of());
    assertEquals(card("S2"), policy.choose(v).card());
  }

  @Test
  void avoidsGivingPointsToHigherTrumpBehind() {
    var first = row(1, Trump.DIAMOND, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var second = row(2, Trump.DIAMOND, p(0, "D2"), p(1, "SA"), p(2, "D3"), p(3, "D5"), p(4, "D6"));
    View v = view(0, 0, Trump.DIAMOND, 16, "DK D8 D10 DJ H2 H3 S2 S3", "C6 C7 C8",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(1)), List.of(first, second),
        List.of(p(1, "D4"), p(2, "D7"), p(3, "D9"), p(4, "S4")));
    // 마지막 자리에서는 실제로 이길 수 있는 카드를 쓸 수 있습니다.
    assertTrue(new PlayFacts(v).winningActions().contains(policy.choose(v)));
    View earlier = view(0, 0, Trump.DIAMOND, 16, "DK D8 D10 DJ H2 H3 S2 S3", "C6 C7 C8",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(4)), List.of(first,
            row(2, Trump.DIAMOND, p(0, "D2"), p(1, "D3"), p(2, "D5"), p(3, "D6"), p(4, "SA"))),
        List.of(p(4, "D4")));
    assertEquals(card("D8"), policy.choose(earlier).card());
  }

  @Test
  void leadsRemainingSuitPointsIntoDeclarerVoid() {
    var first = row(1, Trump.HEART, p(0, "SA"), p(1, "S2"), p(2, "S3"), p(3, "S4"), p(4, "S5"));
    var second = row(2, Trump.HEART, p(0, "D2"), p(1, "DA"), p(2, "D3"), p(3, "D4"), p(4, "D5"));
    var third = row(3, Trump.HEART, p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"), p(0, "H2"));
    var fourth = row(4, Trump.HEART, p(0, "H3"), p(1, "HA"), p(2, "H4"), p(3, "H5"), p(4, "H6"));
    View v = view(1, 0, Trump.HEART, 16, "C6 C10 CJ CQ CK CA", "",
        new CardFriend(card("S5")), new RevealedFriend(new Seat(4)),
        List.of(first, second, third, fourth), List.of());
    assertEquals(card("CA"), policy.choose(v).card());
  }

  @Test
  void cashesFourTopDiamondsBeforeCallingMightyWithHeartTen() {
    var selection = new CardFriend(card("SA"));
    var buried = "S2 S3 C2";
    View opening = view(0, 0, Trump.NO_TRUMP, 17, "DA DK DQ DJ D2 H2 H8 H10 HK JK", buried,
        selection, UnrevealedFriend.INSTANCE, List.of(), List.of());
    assertEquals(card("DA"), policy.choose(opening).card());
    var first = row(1, Trump.NO_TRUMP, p(0, "DA"), p(1, "D3"), p(2, "D4"), p(3, "D5"), p(4, "D6"));
    var second = row(2, Trump.NO_TRUMP, p(0, "DK"), p(1, "D7"), p(2, "D8"), p(3, "D9"), p(4, "C3"));
    var third = row(3, Trump.NO_TRUMP, p(0, "DQ"), p(1, "D10"), p(2, "C4"), p(3, "C5"), p(4, "C6"));
    var fourth = row(4, Trump.NO_TRUMP, p(0, "DJ"), p(1, "C7"), p(2, "C8"), p(3, "C9"),
        p(4, "C10"));
    View afterRun = view(0, 0, Trump.NO_TRUMP, 17, "D2 H2 H8 H10 HK JK", buried,
        selection, UnrevealedFriend.INSTANCE, List.of(first, second, third, fourth), List.of());
    assertEquals(card("H10"), policy.choose(afterRun).card());
    var aceDiscarded = row(4, Trump.NO_TRUMP, p(0, "DJ"), p(1, "HA"), p(2, "C8"), p(3, "C9"),
        p(4, "C10"));
    View promotedKing = view(0, 0, Trump.NO_TRUMP, 17, "D2 H2 H8 H10 HK JK", buried,
        selection, UnrevealedFriend.INSTANCE, List.of(first, second, third, aceDiscarded),
        List.of());
    assertEquals(card("HK"), policy.choose(promotedKing).card());
  }

  @Test
  void recoversFriendTrumpReturnWithJokerWhenAceCanOvertakeKing() {
    var first = row(1, Trump.HEART, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var second = row(2, Trump.HEART, p(0, "H2"), p(1, "H3"), p(2, "H4"), p(3, "H5"), p(4, "SA"));
    var third = row(3, Trump.HEART, p(4, "HQ"), p(0, "H10"), p(1, "H7"), p(2, "H8"), p(3, "H9"));
    View v = view(0, 0, Trump.HEART, 16, "HJ HK JK D2 D3 D4 C6", "S2 S3 S4",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(4)), List.of(first, second, third),
        List.of(p(4, "H6")));
    assertEquals(card("JK"), policy.choose(v).card());
  }

  @Test
  void callsHeartAceAndShedsDiamondBeforeCashingRecoveryCards() {
    var selection = new CardFriend(card("HA"));
    View opening = view(0, 0, Trump.NO_TRUMP, 17, "SK SA D8 DA H6 H8 H9 HJ HK JK", "D4 C7 C9",
        selection, UnrevealedFriend.INSTANCE, List.of(), List.of());
    assertEquals(card("H6"), policy.choose(opening).card());
    var first = row(1, Trump.NO_TRUMP, p(0, "H6"), p(1, "HA"), p(2, "HQ"), p(3, "H10"), p(4, "H2"));
    var second = row(2, Trump.NO_TRUMP, p(1, "H3"), p(2, "H4"), p(3, "H5"), p(4, "H7"), p(0, "HK"));
    View earlyLoss = view(0, 0, Trump.NO_TRUMP, 17, "SK SA D8 DA H8 H9 HJ JK", "D4 C7 C9",
        selection, new RevealedFriend(new Seat(1)), List.of(first, second), List.of());
    assertEquals(card("D8"), policy.choose(earlyLoss).card());
  }

  @Test
  void keepsJokerForHigherTrumpOnlyWhenGunAndSeatsAreSafe() {
    var first = row(1, Trump.CLUB, p(0, "SK"), p(1, "S2"), p(2, "S5"), p(3, "S4"), p(4, "CJ"));
    var second = row(2, Trump.CLUB, p(4, "D2"), p(0, "D3"), p(1, "D4"), p(2, "DA"), p(3, "D5"));
    var third = row(3, Trump.CLUB, p(2, "CQ"), p(3, "C2"), p(4, "C4"), p(0, "SA"), p(1, "C5"));
    for (boolean safeGun : List.of(false, true)) {
      for (boolean voidBehind : List.of(false, true)) {
        var fourth = row(4, Trump.CLUB, p(0, "CA"), p(1, "C7"), p(2, "C8"),
            p(3, voidBehind ? "H2" : "C10"), p(4, "H3"));
        View v = view(2, 0, Trump.CLUB, 15, "JK H4 H5 D6 D7 " + (safeGun ? "S3" : "H6"), "",
            new CardFriend(card("JK")), UnrevealedFriend.INSTANCE,
            List.of(first, second, third, fourth),
            List.of(p(0, "C6"), p(1, "C9")));
        assertEquals(safeGun && voidBehind ? 0 : 1,
            policy.choose(v).card() instanceof Joker ? 1 : 0);
      }
    }
  }

  @Test
  void avoidsMightySuitAfterCashingLastTrump() {
    var first = row(1, Trump.HEART, p(0, "D2"), p(1, "DA"), p(2, "D3"), p(3, "D4"), p(4, "D5"));
    var second = row(2, Trump.HEART, p(1, "HA"), p(2, "H2"), p(3, "H3"), p(4, "H4"), p(0, "H5"));
    var third = row(3, Trump.HEART, p(1, "HK"), p(2, "H6"), p(3, "H7"), p(4, "H8"), p(0, "H9"));
    View v = view(1, 0, Trump.HEART, 15, "SK S2 D6 D7 C6 C7 C8", "",
        SpecialFriend.FIRST_TRICK, new RevealedFriend(new Seat(1)), List.of(first, second, third),
        List.of());
    assertNotEquals(Suit.SPADE, ((StandardCard) policy.choose(v).card()).suit());
  }

  @Test
  void ruffsWithKingOnNinthToAvoidFeedingItToOutstandingAceOnTenth() {
    String owned = "S8 SK", buried = "D8 D9 D10";
    var current = List.of(p(1, "HA"), p(2, "H2"));
    var last = row(8, Trump.SPADE, p(0, "C2"), p(1, "CA"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var history = countedHistory(owned, buried, "DA JK", "SA H3 H4 H5", current, last);
    View v = view(3, 3, Trump.SPADE, 17, owned, buried, SpecialFriend.NONE, NoFriend.INSTANCE,
        history, current);
    assertEquals(card("SK"), policy.choose(v).card());
  }

  @Test
  void firstFriendBalancesOpeningPressureAgainstLaterDefense() {
    for (String[] example : List.of(
        new String[]{
            "C2 C3 C7 C8 CK D2 D3 H2 H3 S2", "C8"
        },
        new String[]{
            "C3 C5 CJ CQ D2 D3 H2 H3 S2 S3", "CQ"
        })) {
      View v = view(1, 0, Trump.NO_TRUMP, 20, example[0], "", SpecialFriend.FIRST_TRICK,
          UnrevealedFriend.INSTANCE, List.of(), List.of(p(0, "C6")));
      assertEquals(card(example[1]), policy.choose(v).card());
    }
  }

  @Test
  void callsDiamondAceBeforeSpendingMightyOnClubOperatingHand() {
    View v = view(0, 0, Trump.NO_TRUMP, 17, "SA D3 D5 C3 C4 C8 C10 CK CA JK", "C2 C5 C6",
        new CardFriend(card("DA")), UnrevealedFriend.INSTANCE, List.of(), List.of());
    assertEquals(card("D3"), policy.choose(v).card());
  }


  @Test
  @DisplayName("두 점이 필요한 야당은 주공 뒤의 추가 득점을 보고 한 점으로 선출한다")
  void leadsOnePointWhenAnotherCanComeAfterDeclarer() {
    var view = twoPointPressure(0, 17, "C10 C7 H8 H9 H7 D3 S2 D4", List.of());
    assertEquals(card("C10"), policy.choose(view).card());

    var needsThree = twoPointPressure(0, 16, "C10 C7 H8 H9 H7 D3 S2 D4", List.of());
    assertNotEquals(card("C10"), policy.choose(needsThree).card());
  }

  @Test
  @DisplayName("앞 야당이 점수를 냈고 주공 뒤에 추가 점수가 가능하면 숫자로 따른다")
  void leavesSecondPointThreatBehindDeclarer() {
    var view = twoPointPressure(1, 17, "CQ C8 HQ H9 S3 D3 D4 D5", List.of(p(0, "CA")));
    assertEquals(card("C8"), policy.choose(view).card());

    var noPointBehind =
        twoPointPressure(1, 17, "C10 CJ CQ CK C8 D3 D4 S3", List.of(p(0, "CA")));
    assertEquals(card("C10"), policy.choose(noPointBehind).card());
  }

  @Test
  @DisplayName("앞 야당이 물패를 내면 후수가 부기루 수비 여부에 따라 첫 압박을 맡는다")
  void choosesWhetherToStartPressureFromTheSecondSeat() {
    var current = List.of(p(0, "C7"));
    var expendable = twoPointPressure(1, 17, "D10 D4 H6 H7 H8 H9 S3 S4", current);
    assertEquals(card("D10"), policy.choose(expendable).card());

    var guard = twoPointPressure(1, 17, "HA HQ HJ H9 H8 H7 D4 S3", current);
    assertEquals(card("D4"), policy.choose(guard).card());
  }

  private static View twoPointPressure(int self, int contract, String hand,
      List<PublicPlay> trick) {
    var opening = row(1, Trump.SPADE, p(2, "H10"), p(3, "H3"), p(4, "H2"),
        p(0, "HK"), p(1, "H4"));
    var establishesVoid = row(2, Trump.SPADE, p(0, "C9"), p(1, "C4"), p(2, "D2"),
        p(3, "C5"), p(4, "C6"));
    // 주공이 비딩에서 공개한 하트는 부기루 수비 여부를 판단하는 근거입니다.
    View observation = view(self, 2, Trump.SPADE, contract, hand, "", SpecialFriend.NONE,
        NoFriend.INSTANCE, List.of(opening, establishesVoid), trick);
    var context = new Context(observation.context().playOrder(), new Seat(2), NoFriend.INSTANCE,
        observation.context().history(), List.of(new Bid(15, Trump.HEART), observation.contract()));
    return ObservationFactory.create(observation.self(), observation.privateInfo(),
        observation.contract(), observation.friendSelection(), context, trick);
  }
}
