package mighty.ai.play;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.stream.IntStream;
import mighty.data.CardParser;
import mighty.game.*;
import mighty.game.action.*;
import mighty.game.bidding.Bid;
import mighty.game.friend.*;
import mighty.game.observation.*;
import mighty.game.rule.Rules;
import mighty.game.trick.*;
import mighty.model.*;
import org.junit.jupiter.api.*;

class RoleFeedbackTest {

  private final RulePlayPolicy policy = new RulePlayPolicy();

  private static Card card(String s) {
    return CardParser.parse(s);
  }

  private static PublicPlay p(int seat, String code) {
    return new PublicPlay(new Seat(seat), code.equals("JK") ? new JokerFollow()
        : new StandardPlay((StandardCard) card(code), false));
  }

  private static PublicTrick row(Trump trump, PublicPlay... plays) {
    return new PublicTrick(new TrickNumber(1), List.of(plays),
        Rules.currentWinner(trump, new TrickNumber(1), List.of(plays)).orElseThrow().seat());
  }

  private static View v(int self, Trump trump, int contract, String owned,
      FriendSelection selection,
      FriendStatus status, List<PublicTrick> history, PublicPlay... trick) {
    var seats = IntStream.range(0, 5).mapToObj(Seat::new).toList();
    var order = PlayOrder.from(new TableLayout(GameMode.FIVE_PLAYER, seats), new HashSet<>(seats));
    PrivateInfo info =
        self == 0 ? new DeclarerInfo(CardParser.parseList(owned), CardParser.parseList("D8 D9 D10"))
            : new NonDeclarerInfo(CardParser.parseList(owned));
    var view = ObservationFactory.create(new Seat(self), info, new Bid(contract, trump), selection,
        new Context(order, new Seat(0), status, history), List.of(trick));
    var seen = new HashSet<Card>();
    for (Card c : view.hand()) {
      assertTrue(seen.add(c));
    }
    for (var t : history) {
      for (var play : t.cards()) {
        assertTrue(seen.add(play.action().card()));
      }
    }
    for (var play : trick) {
      assertTrue(seen.add(play.action().card()));
    }
    return view;
  }

  private static List<PublicTrick> opening() {
    return List.of(row(Trump.SPADE, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5")));
  }

  private static FriendSelection mighty() {
    return new CardFriend(card("DA"));
  }

  private static FriendStatus friend() {
    return new RevealedFriend(new Seat(4));
  }

  @Test
  @DisplayName("앞 야당 J가 이미 압박하면 뒤 Q는 숫자를 내고 보존한다")
  void noQueenStacking() {
    View view = v(2, Trump.SPADE, 17, "SQ S5 H2 H3 H4 H5 D2 D3 D4", mighty(), friend(), opening(),
        p(0, "S2"),
        p(1, "SJ"));
    assertEquals(card("S5"), policy.choose(view).card());
  }

  @Test
  @DisplayName("J Q가 나와도 네 장 보유 야당 A는 겹쳐 내지 않는다")
  void noAceStacking() {
    View view = v(3, Trump.SPADE, 17, "SA S4 S6 S7 H2 H3 H4 D2 D3", mighty(), friend(), opening(),
        p(0, "S2"),
        p(1, "SJ"), p(2, "SQ"));
    assertEquals(card("S4"), policy.choose(view).card());
  }

  @Test
  @DisplayName("J와 숫자 두 장 보유자는 첫 압박을 만들 수 있다")
  void shortHoldingPressures() {
    View view = v(1, Trump.SPADE, 17, "SJ S5 H2 H3 H4 H5 D2 D3 D4", mighty(), friend(), opening(),
        p(0, "S2"));
    assertEquals(card("SJ"), policy.choose(view).card());
  }

  @Test
  @DisplayName("A와 숫자 두 장은 A 압박 대신 숫자를 낸다")
  void shortAceStillReserved() {
    View view = v(1, Trump.SPADE, 17, "SA S5 H2 H3 H4 H5 D2 D3 D4", mighty(), friend(), opening(),
        p(0, "S2"));
    assertEquals(card("S5"), policy.choose(view).card());
  }

  @Test
  @DisplayName("네 장 K 수비는 특수카드가 남은 동안 보존한다")
  void longKingDefense() {
    View view = v(1, Trump.SPADE, 17, "SK SJ S5 S4 H2 H3 H4 D2 D3", mighty(), friend(), opening(),
        p(0, "S2"));
    assertEquals(card("S4"), policy.choose(view).card());
  }

  @Test
  @DisplayName("KJ 수비는 T에 J를 대응시킨다")
  void usesJackAgainstTen() {
    View view = v(1, Trump.SPADE, 17, "SK SJ DA JK H2 H3 H4 D2 D3", new CardFriend(card("H8")),
        friend(),
        opening(), p(0, "S10"));
    assertEquals(card("SJ"), policy.choose(view).card());
  }

  @Test
  @DisplayName("KJ 수비는 Q에 K를 대응시킨다")
  void usesKingAgainstQueen() {
    View view = v(1, Trump.SPADE, 17, "SK SJ DA JK H2 H3 H4 D2 D3", new CardFriend(card("H8")),
        friend(),
        opening(), p(0, "SQ"));
    assertEquals(card("SK"), policy.choose(view).card());
  }

  @Test
  @DisplayName("초구를 K로 먹은 프렌드는 마이티보다 기루 반환이 먼저다")
  void friendReturnsBeforeMighty() {
    var history = List.of(
        row(Trump.DIAMOND, p(0, "S2"), p(1, "SK"), p(2, "S3"), p(3, "S4"), p(4, "S5")));
    View view = v(1, Trump.DIAMOND, 17, "D2 D7 SA JK C2 C3 H2 H3 H4", new CardFriend(card("SA")),
        UnrevealedFriend.INSTANCE,
        history);
    assertEquals(card("D7"), policy.choose(view).card());
  }

  @Test
  @DisplayName("초반 한 장뿐인 프렌드 기루도 주공에게 반환한다")
  void earlyReturnNotBlockedByOwnLastTrump() {
    var history = List.of(
        row(Trump.DIAMOND, p(0, "S2"), p(1, "SK"), p(2, "S3"), p(3, "S4"), p(4, "S5")));
    View view = v(1, Trump.DIAMOND, 17, "D2 SA C2 C3 C4 H2 H3 H4 H5", new CardFriend(card("SA")),
        UnrevealedFriend.INSTANCE,
        history);
    assertEquals(card("D2"), policy.choose(view).card());
  }

  @Test
  @DisplayName("문양 공약의 사이드는 일반 A로 지원한다")
  void friendCapturesReturnedSuit() {
    View view = v(1, Trump.DIAMOND, 17, "HA H3 SA C2 C3 C4 D2 D3 D4 D5", new CardFriend(card("SA")),
        UnrevealedFriend.INSTANCE,
        List.of(), p(0, "H2"));
    assertEquals(card("HA"), policy.choose(view).card());
  }

  @Test
  @DisplayName("초구프는 이길 수 있는 K를 아끼며 프렌드를 양보하지 않는다")
  void joinsFirstFriend() {
    View view = v(1, Trump.DIAMOND, 17, "SK S3 C2 C3 C4 H2 H3 D2 D3 D4", SpecialFriend.FIRST_TRICK,
        UnrevealedFriend.INSTANCE,
        List.of(), p(0, "S2"));
    assertEquals(card("SK"), policy.choose(view).card());
  }

  @Test
  @DisplayName("확보된 야당 승리의 실패 결정점은 미래 탑카라도 지원한다")
  void feedsDecisivePoint() {
    View view = v(4, Trump.HEART, 19, "CK C5 D2 D3 D4 H2 H3 H4 S2 S3", SpecialFriend.NONE,
        NoFriend.INSTANCE,
        List.of(), p(0, "C2"), p(1, "CA"), p(2, "C3"), p(3, "C4"));
    assertEquals(card("CK"), policy.choose(view).card());
  }

  @Test
  @DisplayName("마이티 프렌드의 주공 AK 초구는 K 대신 A다")
  void declarerAceOpening() {
    View view = v(0, Trump.HEART, 17, "H2 H3 H4 H5 H6 H7 CA CK D2 D3", new CardFriend(card("SA")),
        UnrevealedFriend.INSTANCE,
        List.of());
    assertEquals(card("CA"), policy.choose(view).card());
  }

  @Test
  @DisplayName("친구 반환을 야당 Q가 덮으면 주공은 기루 A로 잡는다")
  void declarerRecoversActualEnemyWinner() {
    var history = List.of(
        row(Trump.SPADE, p(0, "C2"), p(1, "CA"), p(2, "C3"), p(3, "C4"), p(4, "C5")));
    View view = v(0, Trump.SPADE, 17, "SA S4 H2 H3 H4 H5 D2 D3 D4", mighty(),
        new RevealedFriend(new Seat(1)),
        history,
        p(1, "S2"), p(2, "SQ"), p(3, "S6"), p(4, "S7"));
    assertEquals(card("SA"), policy.choose(view).card());
  }

  @Test
  @DisplayName("노기루 빵의 마이티 친구는 불확실한 C9로 잠시 앞서는 대신 마이티로 연결한다")
  void mightyFriendSecuresGunConnection() {
    var history = List.of(
        row(Trump.NO_TRUMP, p(0, "CA"), p(1, "C2"), p(2, "C4"), p(3, "C5"), p(4, "C6")));
    var gun = new PublicPlay(new Seat(0), new StandardPlay((StandardCard) card("C3"), true));
    View view = v(1, Trump.NO_TRUMP, 20, "SA C9 C7 S2 S3 D2 D3 H2 H3", new CardFriend(card("SA")),
        UnrevealedFriend.INSTANCE, history, gun);
    assertEquals(card("SA"), policy.choose(view).card());
  }

  @Test
  @DisplayName("뒤 마이티가 남을 수 있는 첫 점수 압박에는 Q를 겹치지 않는다")
  void avoidsUnsecuredSidePointStacking() {
    View view = v(2, Trump.DIAMOND, 17, "HQ H5 S2 S3 S4 D2 D3 D4 C2 C3", new CardFriend(card("SA")),
        UnrevealedFriend.INSTANCE, List.of(), p(0, "H2"), p(1, "HJ"));
    assertEquals(card("H5"), policy.choose(view).card());
  }

  @Test
  @DisplayName("점수가 많은 야당은 확보된 동료 마이티에 사이드 점수를 지원한다")
  void pointHeavyOppositionFeedsSecuredAlly() {
    View view = v(4, Trump.HEART, 15, "CA CK CQ C2 HJ H2 H3 S2 S3 S4", SpecialFriend.NONE,
        NoFriend.INSTANCE, List.of(), p(0, "D2"), p(1, "SA"), p(2, "D3"), p(3, "D4"));
    Card chosen = policy.choose(view).card();
    assertEquals(1, chosen.points());
    assertNotEquals(card("HJ"), chosen);
  }

  @Test
  @DisplayName("친구의 낮은 반환이 아직 선두여도 주공 K로 받아쓴다")
  void declarerTakesUnsecuredFriendReturn() {
    var first = row(Trump.SPADE, p(0, "DA"), p(1, "D2"), p(2, "D3"), p(3, "D4"), p(4, "D5"));
    var second = new PublicTrick(new TrickNumber(2),
        List.of(p(0, "S3"), p(1, "S4"), p(2, "S5"), p(3, "S7"),
            p(4, "SA")), new Seat(4));
    View view = v(0, Trump.SPADE, 17, "SK S2 H2 H3 H4 C2 C3 C4", new CardFriend(card("JK")),
        UnrevealedFriend.INSTANCE, List.of(first, second), p(4, "S6"));
    assertEquals(new Seat(4), new PlayFacts(view).teams().friendSeat().orElseThrow());
    assertEquals(card("SK"), policy.choose(view).card());
  }

  @Test
  @DisplayName("초구 앞 야당 J 뒤 프렌드 Q 대신 마이티로 물패를 받아준다")
  void mightyCapturesOpeningAfterOpponentJack() {
    View view = v(2, Trump.DIAMOND, 17, "SA CQ C3 H2 H3 H4 D2 D3 D4 S2", new CardFriend(card("SA")),
        UnrevealedFriend.INSTANCE, List.of(), p(0, "C2"), p(1, "CJ"));
    assertEquals(card("SA"), policy.choose(view).card());
  }

  @Test
  @DisplayName("초구 주공 A에 낮은 기루로 간치지 않고 사이드 점수를 싣는다")
  void doesNotRuffDeclarerAce() {
    View view = v(1, Trump.DIAMOND, 17, "D2 H10 HJ HQ HK HA D10 DJ DQ DK",
        new CardFriend(card("H10")),
        UnrevealedFriend.INSTANCE, List.of(), p(0, "CA"));
    assertEquals(card("H10"), policy.choose(view).card());
  }

  @Test
  @DisplayName("비승리 카드가 전혀 없으면 강제 간은 합법적으로 허용한다")
  void allowsForcedRuff() {
    View view = v(1, Trump.DIAMOND, 17, "D2 D3 D4 D5 D6 D7 D8 D9 D10 DJ",
        new CardFriend(card("D10")),
        UnrevealedFriend.INSTANCE, List.of(), p(0, "CA"));
    assertTrue(Trump.DIAMOND.matches(((StandardCard) policy.choose(view).card()).suit()));
  }

  @Test
  @DisplayName("주공 A에 일반 물패가 있어도 소비 가능한 점수를 지원한다")
  void friendFeedsPointBeforeWater() {
    View view = v(1, Trump.DIAMOND, 17, "D2 H10 H2 H3 H4 S2 S3 S4 S5 S6",
        new CardFriend(card("H10")),
        UnrevealedFriend.INSTANCE, List.of(), p(0, "CA"));
    assertEquals(card("H10"), policy.choose(view).card());
  }

  @Test
  @DisplayName("기루에 마이티를 쓴 프렌드는 7보다 조커 기루 순회를 우선한다")
  void friendUsesJokerAfterMightyOnTrump() {
    var first = row(Trump.SPADE, p(0, "CA"), p(1, "C4"), p(2, "C5"), p(3, "C6"), p(4, "C7"));
    var second = new PublicTrick(new TrickNumber(2),
        List.of(p(0, "S2"), p(1, "DA"), p(2, "S3"), p(3, "S4"),
            p(4, "S5")), new Seat(1));
    View view = v(1, Trump.SPADE, 17, "JK S7 C2 C3 H2 H3 D2 D3", mighty(),
        new RevealedFriend(new Seat(1)),
        List.of(first, second));
    assertEquals(new JokerLead(Suit.SPADE), policy.choose(view));
  }

  @Test
  @DisplayName("초반 기루 지원에서 조커가 있으면 불확실한 반환보다 먼저 쓰고 없으면 마지막 기루를 반환한다")
  void earlyTrumpSupportUsesJokerBeforeUncertainReturn() {
    var first = row(Trump.SPADE, p(0, "C2"), p(1, "CA"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var second = new PublicTrick(new TrickNumber(2),
        List.of(p(1, "HA"), p(2, "H2"), p(3, "H3"), p(4, "H4"),
            p(0, "H5")), new Seat(1));
    var third = new PublicTrick(new TrickNumber(3),
        List.of(p(1, "DA"), p(2, "D2"), p(3, "D3"), p(4, "D4"),
            p(0, "D5")), new Seat(1));
    View view = v(1, Trump.SPADE, 17, "S7 JK C6 C7 H6 D6 D7", mighty(),
        new RevealedFriend(new Seat(1)), List.of(first,
            second, third));
    assertEquals(new JokerLead(Suit.SPADE), policy.choose(view));
    View withoutJoker = v(1, Trump.SPADE, 17, "S7 D8 C6 C7 H6 D6 D7", mighty(),
        new RevealedFriend(new Seat(1)), List.of(first, second, third));
    assertEquals(card("S7"), policy.choose(withoutJoker).card());
  }

  @Test
  @DisplayName("야당은 마이티에 조커를 버려 유일한 기루 A를 남긴다")
  void oppositionDumpsJokerUnderMighty() {
    View view = v(1, Trump.SPADE, 17, "SA JK H2 H3 H4 H5 C2 C3 C4 C5", SpecialFriend.NONE,
        NoFriend.INSTANCE, List.of(), p(0, "DA"));
    assertEquals(card("JK"), policy.choose(view).card());
  }

  @Test
  @DisplayName("다른 숫자 기루도 있으면 A 조커 두 장 수비 예외를 강제하지 않는다")
  void extraTrumpDoesNotForceJokerDump() {
    View view = v(1, Trump.SPADE, 17, "SA S2 JK H2 H3 H4 C2 C3 C4 C5", SpecialFriend.NONE,
        NoFriend.INSTANCE, List.of(), p(0, "DA"));
    assertNotEquals(card("JK"), policy.choose(view).card());
  }

  @Test
  @DisplayName("직접 잡을 수 있는 주공 K는 뒤 A를 가정한 점수 헌납보다 간으로 잡는다")
  void feedsIntoPotentialAceAttack() {
    View view = v(1, Trump.SPADE, 17, "C10 C2 CA C3 D2 D3 S2 S3 S4 S5", SpecialFriend.NONE,
        NoFriend.INSTANCE, List.of(), p(0, "HK"));
    assertEquals(card("S2"), policy.choose(view).card());
  }

  @Test
  @DisplayName("주공이 운영한 부기루 KJ 장수 수비를 남기기 위해 다른 문양 A를 지원한다")
  void keepsOperatingSuitDefenseOverSideAce() {
    View base = v(2, Trump.SPADE, 17, "CK CJ C7 C6 HA H2 H3 H4 S2", SpecialFriend.NONE,
        NoFriend.INSTANCE, opening(), p(0, "D2"), p(1, "DA"));
    var context = base.context();
    View view = ObservationFactory.create(base.self(), base.privateInfo(), base.contract(),
        base.friendSelection(),
        new Context(context.playOrder(), context.declarer(), context.friendStatus(),
            context.history(),
            List.of(new Bid(15, Trump.CLUB), new Bid(17, Trump.SPADE))), base.trick());
    assertEquals(card("HA"), policy.choose(view).card());
  }

  @Test
  @DisplayName("긴 AKQJ와 마이티의 주공은 낮은 기루 대신 물패로 조커 프렌드를 찾는다")
  void solidTrumpsCallJokerOnWater() {
    var first = row(Trump.SPADE, p(0, "DA"), p(1, "D3"), p(2, "D4"), p(3, "D5"), p(4, "D6"));
    View view = v(0, Trump.SPADE, 16, "SA SK SQ SJ S6 S3 H2 C2 D2", new CardFriend(card("JK")),
        UnrevealedFriend.INSTANCE, List.of(first));
    Card chosen = policy.choose(view).card();
    assertInstanceOf(StandardCard.class, chosen);
    assertFalse(Trump.SPADE.matches(((StandardCard) chosen).suit()));
    assertEquals(0, chosen.points());
  }

  @Test
  @DisplayName("친구 반환은 T의 임시 역전보다 외부 Q를 막을 K로 받아쓴다")
  void declarerUsesTopInsteadOfTemporaryWinner() {
    var first = row(Trump.SPADE, p(0, "DA"), p(1, "D2"), p(2, "D3"), p(3, "D4"), p(4, "D5"));
    var second = new PublicTrick(new TrickNumber(2),
        List.of(p(0, "S3"), p(1, "S4"), p(2, "S5"), p(3, "S7"),
            p(4, "SA")), new Seat(4));
    View view = v(0, Trump.SPADE, 17, "SK SJ S10 S2 H2 H3 C2 C3", new CardFriend(card("JK")),
        UnrevealedFriend.INSTANCE, List.of(first, second), p(4, "S6"));
    assertEquals(card("SK"), policy.choose(view).card());
  }

  @Test
  @DisplayName("초구프 마지막 자리는 A 대신 이기는 Q로 프렌드에 탄다")
  void firstFriendLastSeatUsesQueen() {
    View view = v(4, Trump.DIAMOND, 17, "HQ HA H3 S2 S3 D2 D3 C2 C3 C4", SpecialFriend.FIRST_TRICK,
        UnrevealedFriend.INSTANCE, List.of(), p(0, "H2"), p(1, "HJ"), p(2, "H4"), p(3, "H5"));
    assertEquals(card("HQ"), policy.choose(view).card());
  }

  @Test
  @DisplayName("공개 프렌드도 마지막 자리는 문양 A가 아닌 최소 승리 Q를 쓴다")
  void revealedFriendLastSeatUsesQueen() {
    View view = v(4, Trump.DIAMOND, 17, "HQ HA H3 S2 S3 D2 D3 C2 C3 C4", new CardFriend(card("C2")),
        new RevealedFriend(new Seat(4)), List.of(), p(0, "H2"), p(1, "HJ"), p(2, "H4"), p(3, "H5"));
    assertEquals(card("HQ"), policy.choose(view).card());
  }

  @Test
  @DisplayName("마이티 프렌드에게 Q 초구로 KQ 연결을 불필요하게 넘기지 않는다")
  void openingDoesNotGiveKingQueenToMightyFriend() {
    View view = v(0, Trump.SPADE, 16, "SA S2 S3 S4 S5 DK DQ H2 H3 C2", new CardFriend(card("DA")),
        UnrevealedFriend.INSTANCE, List.of());
    assertNotEquals(card("DQ"), policy.choose(view).card());
    assertNotEquals(card("DK"), policy.choose(view).card());
  }

  @Test
  @DisplayName("간에 A를 쓴 뒤 Q를 외부 K에 먼저 내주지 않는다")
  void queenDoesNotLeadIntoOutstandingKing() {
    var first = row(Trump.SPADE, p(0, "C2"), p(1, "CA"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var second = new PublicTrick(new TrickNumber(2),
        List.of(p(1, "H2"), p(2, "H3"), p(3, "H4"), p(4, "H5"),
            p(0, "SA")), new Seat(0));
    View view = v(0, Trump.SPADE, 16, "SQ S3 C6 C7 C8 D2 D3 H6", SpecialFriend.NONE,
        NoFriend.INSTANCE, List.of(first, second));
    assertNotEquals(card("SQ"), policy.choose(view).card());
  }

  @Test
  @DisplayName("7장 중 점수4장인 야당은 동료 J에 사이드 점수를 지원한다")
  void pointHeavyOppositionDoesNotWaitForAbsoluteCertainty() {
    var first = row(Trump.SPADE, p(0, "DA"), p(1, "D2"), p(2, "D3"), p(3, "D4"), p(4, "D5"));
    var second = new PublicTrick(new TrickNumber(2),
        List.of(p(0, "C2"), p(1, "C3"), p(2, "C4"), p(3, "CA"),
            p(4, "C5")), new Seat(3));
    var third = new PublicTrick(new TrickNumber(3),
        List.of(p(3, "HA"), p(4, "H2"), p(0, "H3"), p(1, "H4"),
            p(2, "H5")), new Seat(3));
    View view = v(4, Trump.SPADE, 17, "CJ CQ DJ DQ C6 C7 S2", SpecialFriend.NONE,
        NoFriend.INSTANCE, List.of(first, second, third), p(3, "HJ"));
    assertEquals(1, policy.choose(view).card().points());
  }

  @Test
  @DisplayName("앞자리에서 뒤 K가 미소모라면 Q 대신 A로 확보한다")
  void friendFrontSeatStillUsesAce() {
    View view = v(2, Trump.DIAMOND, 17, "HQ HA H3 S2 S3 D2 D3 C2 C3 C4", new CardFriend(card("C2")),
        new RevealedFriend(new Seat(2)), List.of(), p(0, "H2"), p(1, "HJ"));
    assertEquals(card("HA"), policy.choose(view).card());
  }

  @Test
  @DisplayName("최고 바로 아래의 상위 확인에는 A를 보여주고 새 낮은 요청에는 마이티를 쓴다")
  void showsAceOnAllHonourProbes() {
    for (String lead : List.of("SK", "SQ", "SJ")) {
      View view = v(1, Trump.SPADE, 17, "DA SA S2 H2 H3 C2 C3 C4 D2 D3", mighty(),
          UnrevealedFriend.INSTANCE, List.of(), p(0, lead));
      assertEquals(card(lead.equals("SK") ? "SA" : "DA"), policy.choose(view).card(), lead);
    }
  }

  @Test
  @DisplayName("A 없는 친구는 바로 아래 탐색에만 숨고 새 낮은 요청에는 응답한다")
  void preservesMightyOnAllHonourProbes() {
    for (String lead : List.of("SK", "SQ", "SJ")) {
      View view = v(1, Trump.SPADE, 17, "DA S2 H2 H3 H4 H5 C2 C3 D2 D3", mighty(),
          UnrevealedFriend.INSTANCE, List.of(), p(0, lead));
      assertEquals(card(lead.equals("SK") ? "S2" : "DA"), policy.choose(view).card(), lead);
    }
  }

  @Test
  @DisplayName("주공이 다른 문양을 버렸으면 뒤 프렌드 가능성만으로 야당 지원을 막지 않는다")
  void feedsWhenDeclarerHasAlreadyShed() {
    var first = row(Trump.SPADE, p(0, "D2"), p(1, "D3"), p(2, "D4"), p(3, "D5"), p(4, "DA"));
    View view = v(2, Trump.SPADE, 17, "CJ C6 D7 D8 S2 S3 C7 C8 D6", new CardFriend(card("H8")),
        new RevealedFriend(new Seat(3)), List.of(first), p(4, "H2"), p(0, "C2"), p(1, "H3"));
    // 주공 0은 H를 못 따라 C2를 버렸고 프렌드 3은 아직 뒤에 남아 있습니다.
    assertEquals(card("CJ"), policy.choose(view).card());
  }

  @Test
  @DisplayName("풀노의 낮은 다리 호출은 숫자의 임시 역전 대신 마이티로 받는다")
  void noTrumpCallUsesMightyInsteadOfNumber() {
    View view = v(1, Trump.NO_TRUMP, 20, "SA D6 D2 H2 H3 H4 C2 C3 C4 S2",
        new CardFriend(card("SA")),
        UnrevealedFriend.INSTANCE, List.of(), p(0, "D4"));
    assertEquals(card("SA"), policy.choose(view).card());
  }

  @Test
  @DisplayName("곱노 초구 삽2에 삽 숫자와 마이티 둘뿐이면 마이티로 받는다")
  void twoSpadeMightyFriendAnswersCall() {
    View view = v(1, Trump.NO_TRUMP, 17, "SA S3 D2 D3 D4 H2 H3 H4 C2 C3",
        new CardFriend(card("SA")),
        UnrevealedFriend.INSTANCE, List.of(), p(0, "S2"));
    assertEquals(card("SA"), policy.choose(view).card());
  }

  @Test
  @DisplayName("주공이 이미 탑카로 이기면 마이티로 덮지 않는다")
  void establishedTopStillProtected() {
    View view = v(1, Trump.NO_TRUMP, 20, "SA D6 D2 H2 H3 H4 C2 C3 C4 S2",
        new CardFriend(card("SA")),
        UnrevealedFriend.INSTANCE, List.of(), p(0, "DA"));
    assertNotEquals(card("SA"), policy.choose(view).card());
  }

  @Test
  @DisplayName("노기루 프렌드는 긴 AKQJ 문양도 탑카 선취를 미루지 않는다")
  void noTrumpFriendCashesLongTopSuit() {
    var first = row(Trump.NO_TRUMP, p(0, "C2"), p(1, "SA"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    View view = v(1, Trump.NO_TRUMP, 17, "HA HK HQ HJ H2 D2 D3 C6 C7", new CardFriend(card("SA")),
        new RevealedFriend(new Seat(1)), List.of(first));
    assertEquals(card("HA"), policy.choose(view).card());
  }

  @Test
  @DisplayName("야당의 주공 K 공격은 A로 마이티 사용을 요구한다")
  void oppositionPressuresMightyFriendWithAce() {
    View view = v(1, Trump.DIAMOND, 17, "HA H2 H3 S2 S3 C2 C3 C4 D2 D3", new CardFriend(card("SA")),
        UnrevealedFriend.INSTANCE, List.of(), p(0, "HK"));
    assertEquals(card("HA"), policy.choose(view).card());
  }

  @Test
  @DisplayName("야당은 모든 사이드 문양에서 여당을 최소 승리 카드로 공격한다")
  void attacksAcrossSuitsAndRanks() {
    for (String suit : List.of("H", "C", "D")) {
      for (String[] ranks : List.of(new String[]{
          "10", "J"
      }, new String[]{
          "J", "Q"
      }, new String[]{
          "Q", "K"
      })) {
        String lead = suit + ranks[0], win = suit + ranks[1];
        View view = v(1, Trump.SPADE, 16, win + " " + suit + "2 S2 S3 S4 H5 H6 C7 D6 D7",
            new CardFriend(card("DA")), new RevealedFriend(new Seat(4)), List.of(), p(0, lead));
        assertEquals(card(win), policy.choose(view).card(), lead);
      }
    }
  }

  @Test
  @DisplayName("야당은 초구 T에 JKA 중 최소 승리 J를 낸다")
  void attacksOpeningTenWithSmallestWinner() {
    View view = v(1, Trump.SPADE, 16, "HJ HK HA H2 S2 S3 C2 C3 D2 D3",
        mighty(), friend(), List.of(), p(0, "H10"));
    assertEquals(card("HJ"), policy.choose(view).card());
  }

  @Test
  @DisplayName("야당은 주공 초구 K에 간이 가능하면 점수를 헌납하지 않는다")
  void ruffsDeclarerInsteadOfDonatingPoint() {
    View view = v(1, Trump.HEART, 16, "H2 H10 D2 D3 D4 C2 C3 C4 C5 C6",
        new CardFriend(card("SA")), friend(), List.of(), p(0, "SK"));
    assertEquals(card("H10"), policy.choose(view).card());
  }

  @Test
  @DisplayName("낮은 야당 선두는 압박 완료가 아니므로 짧은 기루 T를 낸다")
  void shortTrumpPressuresLowAllyLead() {
    View view = v(2, Trump.SPADE, 16, "S2 S10 H2 H3 H4 D2 D3 D4 C6", mighty(), friend(),
        opening(), p(0, "S3"), p(1, "S5"));
    assertEquals(card("S10"), policy.choose(view).card());
  }

  @Test
  @DisplayName("주공의 마지막 자리 K는 야당 A로 확실히 잡는다")
  void capturesDeclarerKingAtLastSeat() {
    View view = v(4, Trump.SPADE, 16, "SA S2 S3 S4 H2 H3 D2 D3 C6", mighty(),
        new RevealedFriend(new Seat(1)), opening(), p(0, "SK"), p(1, "S5"), p(2, "S6"), p(3, "S7"));
    assertEquals(card("SA"), policy.choose(view).card());
  }

  @Test
  @DisplayName("야당은 기루 Q9를 보존하며 조커로 주공 A를 잡는다")
  void jokerPreservesTwoTrumps() {
    View view = v(1, Trump.SPADE, 16, "SQ S9 JK H2 H3 H4 D2 D3 C6", mighty(), friend(), opening(),
        p(0, "SA"));
    assertEquals(card("JK"), policy.choose(view).card());
  }

  @Test
  @DisplayName("여당 조커에 야당은 불필요한 간 대신 물패를 버린다")
  void doesNotRuffEnemyJoker() {
    var first = row(Trump.SPADE, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    View view = v(2, Trump.SPADE, 16, "S2 S3 D2 D3 D4 C6 C7 C8 C9", new CardFriend(card("JK")),
        new RevealedFriend(new Seat(1)), List.of(first), p(0, "H2"), p(1, "JK"));
    assertFalse(Trump.SPADE.matches(((StandardCard) policy.choose(view).card()).suit()));
  }

  @Test
  @DisplayName("야당 동료의 간에는 탑카 여부와 관계없이 사이드 점수를 지원한다")
  void feedsAlliedRuff() {
    View view = v(2, Trump.SPADE, 16, "HQ HJ D2 D3 D4 C2 C3 C4 C5 C6", mighty(), friend(),
        List.of(), p(0, "H2"), p(1, "S3"));
    assertEquals(1, policy.choose(view).card().points());
  }

  @Test
  @DisplayName("야당 선두의 사이드에도 짧은 문양의 점수를 실어 주공의 값싼 흘리기를 막는다")
  void supportsSideBeforeDeclarerActs() {
    View view = v(2, Trump.SPADE, 16, "HJ HQ H2 D2 D3 D4 C2 C3 C4 C5", mighty(), friend(),
        List.of(), p(0, "H3"), p(1, "H5"));
    assertEquals(card("HQ"), policy.choose(view).card());
  }

  @Test
  @DisplayName("프렌드는 불확실한 Q 대신 조커로 주공의 낮은 사이드를 회수한다")
  void jokerFriendAnswersSideCall() {
    View view = v(1, Trump.NO_TRUMP, 17, "JK HQ H2 SK SQ D2 D3 C6 C7", new CardFriend(card("JK")),
        new RevealedFriend(new Seat(1)), opening(), p(0, "H3"));
    assertEquals(card("JK"), policy.choose(view).card());
  }

}
