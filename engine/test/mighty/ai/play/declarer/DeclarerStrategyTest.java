package mighty.ai.play.declarer;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import mighty.ai.play.PlayFacts;
import mighty.ai.play.RulePlayPolicy;
import mighty.data.CardParser;
import mighty.game.*;
import mighty.game.action.*;
import mighty.game.bidding.Bid;
import mighty.game.friend.*;
import mighty.game.observation.*;
import mighty.game.rule.Rules;
import mighty.game.trick.*;
import mighty.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DeclarerStrategyTest {

  private final RulePlayPolicy policy = new RulePlayPolicy();

  private static Card card(String text) {
    return CardParser.parse(text);
  }

  private static Action action(String text) {
    Card card = card(text);
    return card instanceof Joker ? new JokerFollow() : new StandardPlay((StandardCard) card, false);
  }

  /**
   * 지정 순서의 공개 카드로 실제 승자를 계산합니다. 상대 손패는 만들지 않습니다.
   */
  private static PublicTrick completed(int number, int leader, Trump trump, String codes) {
    var cards = CardParser.parseList(codes);
    assertEquals(5, cards.size());
    List<PublicPlay> plays = new ArrayList<>();
    for (int index = 0; index < 5; index++) {
      plays.add(new PublicPlay(new Seat((leader + index) % 5), action(cards.get(index).toCode())));
    }
    Seat winner = Rules.currentWinner(trump, new TrickNumber(number), plays).orElseThrow().seat();
    return new PublicTrick(new TrickNumber(number), plays, winner);
  }

  private static List<PublicPlay> current(int leader, String codes) {
    List<PublicPlay> plays = new ArrayList<>();
    List<Card> cards = CardParser.parseList(codes);
    for (int index = 0; index < cards.size(); index++) {
      plays.add(new PublicPlay(new Seat((leader + index) % 5), action(cards.get(index).toCode())));
    }
    return plays;
  }

  private static View view(Trump trump, int points, FriendSelection friend, FriendStatus status,
      String hand, String discards, List<PublicTrick> history, List<PublicPlay> trick) {
    // 자기 패·버리기·공개 카드가 서로 겹치지 않는 완전한 관측만 사용합니다.
    Set<Card> unique = new HashSet<>();
    List<Card> owned = CardParser.parseList(hand);
    List<Card> buried = CardParser.parseList(discards);
    List<Card> all = new ArrayList<>(owned);
    all.addAll(buried);
    history.forEach(row -> row.cards().forEach(play -> all.add(play.action().card())));
    trick.forEach(play -> all.add(play.action().card()));
    all.forEach(card -> assertTrue(unique.add(card), "중복 카드: " + card));
    List<Seat> seats = IntStream.range(0, 5).mapToObj(Seat::new).toList();
    PlayOrder order = PlayOrder.from(new TableLayout(GameMode.FIVE_PLAYER, seats),
        new HashSet<>(seats));
    return ObservationFactory.create(new Seat(0), new DeclarerInfo(owned, buried),
        new Bid(points, trump),
        friend,
        new Context(order, new Seat(0), status, history), trick);
  }

  @Test
  @DisplayName("조커프인데 다른 초구가 없으면 마이티로 먼저 선을 확보한다")
  void securesOpeningBeforeJokerCall() {
    View view = view(Trump.CLUB, 16, new CardFriend(card("JK")), UnrevealedFriend.INSTANCE,
        "SA S2 H7 H9 C10 C8 C6 C5 C3 C2", "D2 D3 D4", List.of(), List.of());
    assertEquals(card("SA"), policy.choose(view).card());
  }

  @Test
  @DisplayName("자력 초구가 있으면 마이티 대신 사이드 K로 반환 경로를 알린다")
  void usesNaturalOpening() {
    View view = view(Trump.DIAMOND, 16, new CardFriend(card("JK")), UnrevealedFriend.INSTANCE,
        "SA SK S2 DQ DJ D6 D3 H8 H6 C2", "C3 C4 C5", List.of(), List.of());
    assertEquals(card("SK"), policy.choose(view).card());
  }

  @Test
  @DisplayName("초구프 지정이면 자기 마이티로 잡지 않고 낮은 사이드로 지원받는다")
  void firstTrickFriendOverridesOwnOpening() {
    View view = view(Trump.DIAMOND, 16, SpecialFriend.FIRST_TRICK, UnrevealedFriend.INSTANCE,
        "SA SK S2 DQ DJ D6 D3 H8 H6 C2", "C3 C4 C5", List.of(), List.of());
    assertEquals(card("C2"), policy.choose(view).card());
  }

  @Test
  @DisplayName("A26 풀노는 클럽 물패를 초구 마이티 프렌드에게 맡긴다")
  void opensWithSideWaterOnFullNoTrump() {
    View view = view(Trump.NO_TRUMP, 20, new CardFriend(card("SA")), UnrevealedFriend.INSTANCE,
        "SK SQ SJ S7 S6 S2 DA CA C2 JK", "H9 H8 H4", List.of(), List.of());
    assertEquals(card("C2"), policy.choose(view).card());
  }

  @Test
  @DisplayName("같은 패도 실제 스페이드 기루면 풀노의 마이티 호출을 적용하지 않는다")
  void doesNotConfuseTrumpAceWithMighty() {
    View view = view(Trump.SPADE, 16, new CardFriend(card("SA")), UnrevealedFriend.INSTANCE,
        "SK SQ SJ S7 S6 S2 DA CA C2 JK", "H9 H8 H4", List.of(), List.of());
    assertEquals(card("DA"), policy.choose(view).card());
    assertNotEquals(card("C2"), policy.choose(view).card());
  }

  @Test
  @DisplayName("두 순회 뒤에도 상대 고기루가 남으면 부기루로 전환한다")
  void switchesAwayFromWeakenedTrump() {
    var history = List.of(
        completed(1, 0, Trump.SPADE, "HA H2 H3 H4 H5"),
        completed(2, 0, Trump.SPADE, "SK S2 S3 S4 S5"),
        completed(3, 0, Trump.SPADE, "SQ S6 S9 S10 H6"));
    View view = view(Trump.SPADE, 16, SpecialFriend.NONE, NoFriend.INSTANCE,
        "SJ S8 S7 CA C9 D8 D7", "DA H7 H8", history, List.of());
    assertEquals(card("CA"), policy.choose(view).card());
    assertTrue(policy.recommend(view).reason().contains("전환"));
  }

  @Test
  @DisplayName("공개 야당 무기루 뒤에는 친구 기루를 빼지 않고 조커 사이드를 준비한다")
  void clearsSideForFriendRuffAfterOpponentsShowVoid() {
    var history = List.of(
        completed(1, 0, Trump.SPADE, "DA D2 D3 D4 D5"),
        completed(2, 0, Trump.SPADE, "SK S2 C3 H4 D6"));
    View view = view(Trump.SPADE, 16, new CardFriend(card("S2")), new RevealedFriend(new Seat(1)),
        "SQ SJ S8 S7 C2 JK D8 H6", "C4 C5 C6", history, List.of());
    Action action = policy.choose(view);
    assertInstanceOf(JokerLead.class, action);
    assertEquals(Suit.CLUB, ((JokerLead) action).declaredSuit());
  }

  @Test
  @DisplayName("공개 무기루 증거가 하나라도 없으면 친구에게만 기루가 있다고 단정하지 않는다")
  void doesNotAssumeOpponentsAreVoid() {
    var history = List.of(
        completed(1, 0, Trump.SPADE, "DA D2 D3 D4 D5"),
        completed(2, 0, Trump.SPADE, "SK S2 S3 H4 D6"));
    View view = view(Trump.SPADE, 16, new CardFriend(card("S2")), new RevealedFriend(new Seat(1)),
        "SQ SJ S8 S7 C2 JK D8 H6", "C4 C5 C6", history, List.of());
    assertEquals(new JokerLead(Suit.SPADE), policy.choose(view));
    assertFalse(policy.recommend(view).reason().contains("야당은 공개 무기루"));
  }

  @Test
  @DisplayName("친구의 낮은 기루 반환은 마이티로 회수하되 안전한 A는 덮지 않는다")
  void distinguishesLowReturnFromSafeAce() {
    var history = List.of(
        completed(1, 0, Trump.SPADE, "HA H2 H3 H4 H5"),
        completed(2, 0, Trump.SPADE, "S2 SK S3 S4 S5"));
    for (String lead : List.of("S6", "SA")) {
      View view = view(Trump.SPADE, 16, new CardFriend(card("SK")), new RevealedFriend(new Seat(1)),
          "DA SQ SJ S8 C2 CA D8 H8", "C4 C5 C6", history, current(1, lead + " S9 S10 H6"));
      if (lead.equals("S6")) {
        assertEquals(card("DA"), policy.choose(view).card());
      } else {
        assertNotEquals(card("DA"), policy.choose(view).card());
      }
    }
  }

  @Test
  @DisplayName("약한 기루로 점수 트릭을 간칠 때 비점수보다 점수 기루를 먼저 쓴다")
  void ruffsWithFragilePointTrump() {
    var history = List.of(
        completed(1, 0, Trump.SPADE, "HA H2 H3 H4 H5"),
        completed(2, 0, Trump.SPADE, "SK S2 S3 S4 S5"),
        completed(3, 0, Trump.SPADE, "S6 SQ S10 S9 H6"));
    View view = view(Trump.SPADE, 16, SpecialFriend.NONE, NoFriend.INSTANCE,
        "SJ S8 S7 CA C9 H9 H8", "DQ D8 D7", history, current(1, "DK D4 D5 D6"));
    assertEquals(card("SJ"), policy.choose(view).card());
  }

  @Test
  @DisplayName("초구에 쓴 친구 마이티는 이후 호출 자원으로 다시 계산하지 않는다")
  void doesNotReuseSpentFriendMighty() {
    var history = List.of(
        completed(1, 0, Trump.NO_TRUMP, "C2 SA C3 C4 C5"),
        completed(2, 1, Trump.NO_TRUMP, "S4 S8 S9 S10 SQ"));
    View view = view(Trump.NO_TRUMP, 20, new CardFriend(card("SA")),
        new RevealedFriend(new Seat(1)),
        "SK SJ S7 S6 S2 DA CA JK", "H9 H8 H4", history, List.of());
    assertEquals(card("SK"), policy.choose(view).card());
    assertFalse(policy.recommend(view).reason().contains("호출"));
  }

  @Test
  @DisplayName("동료가 확보한 9구에는 조커를 처리하고 마지막 구의 A를 남긴다")
  void disposesNinthJokerOnSecuredFriendWin() {
    Set<Card> reserved = new HashSet<>(
        CardParser.parseList("JK HA C5 C6 C7 SA C2 C3 C4 DA DK DQ DJ D10"));
    List<Card> available = mighty.game.deal.DeckFactory.create().stream()
        .filter(card -> !reserved.contains(card)).toList();
    List<PublicTrick> history = new ArrayList<>();
    int leader = 0;
    for (int number = 1; number <= 7; number++) {
      String codes = available.subList((number - 1) * 5, number * 5).stream()
          .map(Card::toCode).collect(java.util.stream.Collectors.joining(" "));
      PublicTrick trick = completed(number, leader, Trump.NO_TRUMP, codes);
      history.add(trick);
      leader = trick.winner().index();
    }
    List<String> lastCards = new ArrayList<>(List.of("DK", "DQ", "DJ", "D10"));
    List<String> row = new ArrayList<>();
    for (int index = 0; index < 5; index++) {
      row.add((leader + index) % 5 == 1 ? "DA" : lastCards.remove(0));
    }
    history.add(completed(8, leader, Trump.NO_TRUMP, String.join(" ", row)));
    View view = view(Trump.NO_TRUMP, 17, new CardFriend(card("SA")),
        new RevealedFriend(new Seat(1)),
        "JK HA", "C5 C6 C7", history, current(1, "SA C2 C3 C4"));
    assertEquals(card("JK"), policy.choose(view).card());
  }

}
