package mighty.ai.play;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
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

class RulePlayPolicyTest {

  private final RulePlayPolicy policy = new RulePlayPolicy();

  private static Card card(String code) {
    return code.equals("JK") ? Joker.INSTANCE
        : new StandardCard(Suit.parse(code.charAt(0)), Rank.parse(code.substring(1)));
  }

  private static PublicPlay play(int seat, String code) {
    return new PublicPlay(new Seat(seat), code.equals("JK") ? new JokerFollow()
        : new StandardPlay((StandardCard) card(code), false));
  }

  /**
   * 단위 테스트용으로 중복 없는 완료 카드 기록을 구성합니다.
   */
  private static List<PublicTrick> history(int count, Set<Card> reserved, Trump trump) {
    List<Card> available = DeckFactory.create().stream()
        .filter(c -> !(c instanceof Joker) && !reserved.contains(c)).toList();
    List<PublicTrick> history = new ArrayList<>();
    for (int number = 1; number <= count; number++) {
      List<PublicPlay> plays = new ArrayList<>();
      for (int seat = 0; seat < 5; seat++) {
        plays.add(new PublicPlay(new Seat(seat),
            new StandardPlay((StandardCard) available.get((number - 1) * 5 + seat),
                false)));
      }
      int winner = Rules.currentWinner(trump, new TrickNumber(number), plays).orElseThrow().seat()
          .index();
      var first = plays.get(0).action();
      plays.set(0, new PublicPlay(new Seat(0), plays.get(winner).action()));
      plays.set(winner, new PublicPlay(new Seat(winner), first));
      Seat actual = Rules.currentWinner(trump, new TrickNumber(number), plays).orElseThrow().seat();
      assertEquals(new Seat(0), actual);
      history.add(new PublicTrick(new TrickNumber(number), plays, actual));
    }
    return history;
  }

  private static View view(int self, int declarer, FriendStatus status, FriendSelection selection,
      Trump trump, String owned, List<PublicPlay> trick, List<PublicTrick> history,
      List<Bid> announcements) {
    List<Card> hand = new ArrayList<>();
    for (String code : owned.split(" ")) {
      if (!code.isBlank()) {
        hand.add(card(code));
      }
    }
    Set<Card> excluded = new HashSet<>(hand);
    trick.forEach(play -> excluded.add(play.action().card()));
    history.forEach(
        completed -> completed.cards().forEach(play -> excluded.add(play.action().card())));
    List<Card> low = DeckFactory.create().stream().filter(c -> c instanceof StandardCard standard
        && standard.rank().value() < 10 && !excluded.contains(c)).toList();
    for (Card candidate : low) {
      if (hand.size() == 10 - history.size()) {
        break;
      }
      hand.add(candidate);
    }
    assertEquals(10 - history.size(), hand.size());
    excluded.addAll(hand);
    List<Card> discards = DeckFactory.create().stream().filter(c -> !excluded.contains(c)).limit(3)
        .toList();
    List<Seat> seats = IntStream.range(0, 5).mapToObj(Seat::new).toList();
    PlayOrder order = PlayOrder.from(new TableLayout(GameMode.FIVE_PLAYER, seats),
        new HashSet<>(seats));
    PrivateInfo info =
        self == declarer ? new DeclarerInfo(hand, discards) : new NonDeclarerInfo(hand);
    return ObservationFactory.create(new Seat(self), info, new Bid(15, trump), selection,
        new Context(order, new Seat(declarer), status, history, announcements), trick);
  }

  private static Set<Card> reserved(String owned, List<PublicPlay> trick) {
    Set<Card> cards = new HashSet<>();
    for (String code : owned.split(" ")) {
      cards.add(card(code));
    }
    trick.forEach(play -> cards.add(play.action().card()));
    return cards;
  }

  @Test
  @DisplayName("주공은 프렌드의 기루 A에 마지막 자리 마이티를 낭비하지 않는다")
  void declarerProtectsFriendTrumpAce() {
    List<PublicPlay> trick = List.of(play(1, "SA"), play(2, "D2"), play(3, "H2"), play(4, "C2"));
    String owned = "DA S6";
    var history = history(1, reserved(owned, trick), Trump.SPADE);
    // 직전 구의 최고 카드를 1번에 배치해 현재 선도 1번이 되도록 구성합니다.
    var prior = history.get(0);
    List<PublicPlay> previousPlays = new ArrayList<>(prior.cards());
    var strongest = previousPlays.get(0).action();
    previousPlays.set(0, new PublicPlay(new Seat(0), previousPlays.get(1).action()));
    previousPlays.set(1, new PublicPlay(new Seat(1), strongest));
    assertEquals(new Seat(1),
        Rules.currentWinner(Trump.SPADE, prior.number(), previousPlays).orElseThrow().seat());
    history = List.of(new PublicTrick(prior.number(), previousPlays, new Seat(1)));
    View view = view(0, 0, new RevealedFriend(new Seat(1)), new CardFriend(card("D3")),
        Trump.SPADE, owned, trick, history, List.of());
    Action chosen = policy.choose(view);
    assertNotEquals(card("DA"), chosen.card());
    assertTrue(new PlayFacts(view).isSecured(new PlayFacts(view).currentWinner().orElseThrow()));
  }

  @Test
  @DisplayName("프렌드는 조커가 미출현이어도 주공의 노기루 A에 마이티를 쓰지 않는다")
  void friendProtectsDeclarerAceBeforeLastSeat() {
    View view = view(1, 0, new RevealedFriend(new Seat(1)), new CardFriend(card("SA")),
        Trump.NO_TRUMP, "SA H2", List.of(play(0, "HA")),
        history(1, reserved("SA H2", List.of(play(0, "HA"))), Trump.NO_TRUMP), List.of());
    assertFalse(new PlayFacts(view).isSecured(new PlayFacts(view).currentWinner().orElseThrow()));
    assertEquals(card("H2"), policy.choose(view).card());
  }

  @Test
  @DisplayName("낮은 기루 호출에는 10으로 점수만 얹지 않고 기루 A로 응답한다")
  void answersLowTrumpRequestWithAce() {
    List<PublicPlay> trick = List.of(play(0, "S2"));
    String owned = "SA S10 S8 S3";
    View view = view(1, 0, new RevealedFriend(new Seat(1)), new CardFriend(card("H4")),
        Trump.SPADE, owned, trick, history(1, reserved(owned, trick), Trump.SPADE), List.of());
    assertEquals(card("SA"), policy.choose(view).card());
  }

  @Test
  @DisplayName("주공의 기루 K에는 마이티를 아끼지만 실제 야당 A가 나오면 잡는다")
  void distinguishesKingPlanFromActualEnemyAce() {
    String owned = "DA S2";
    List<PublicPlay> king = List.of(play(0, "SK"));
    View quiet = view(1, 0, new RevealedFriend(new Seat(1)), new CardFriend(card("DA")),
        Trump.SPADE,
        owned, king, history(1, reserved(owned, king), Trump.SPADE), List.of());
    assertNotEquals(card("DA"), policy.choose(quiet).card());
    List<PublicPlay> ace = List.of(play(0, "SK"), play(1, "SA"));
    View rescue = view(2, 0, new RevealedFriend(new Seat(2)), new CardFriend(card("DA")),
        Trump.SPADE,
        owned, ace, history(1, reserved(owned, ace), Trump.SPADE), List.of());
    assertEquals(card("DA"), policy.choose(rescue).card());
  }

  @Test
  @DisplayName("야당은 일반 사이드 대안이 있으면 기루 리드를 피한다")
  void oppositionAvoidsUnhelpfulTrumpLead() {
    String owned = "SK H2";
    var history = history(1, reserved(owned, List.of()), Trump.SPADE);
    View view = view(0, 1, NoFriend.INSTANCE, SpecialFriend.NONE, Trump.SPADE,
        owned, List.of(), history, List.of());
    Action chosen = policy.choose(view);
    assertInstanceOf(StandardCard.class, chosen.card());
    assertNotEquals(Suit.SPADE, ((StandardCard) chosen.card()).suit());
  }

  @Test
  @DisplayName("프렌드는 선에서 높은 기루부터 반환한다")
  void friendReturnsHighTrump() {
    View view = view(0, 1, new RevealedFriend(new Seat(0)), new CardFriend(card("C4")), Trump.SPADE,
        "SA SK S2", List.of(), history(1, reserved("SA SK S2", List.of()), Trump.SPADE), List.of());
    assertEquals(card("SA"), policy.choose(view).card());
  }

  @Test
  @DisplayName("노기루 프렌드는 공개 공약 문양을 반환하고 조커콜을 하지 않는다")
  void returnsAnnouncedSuitWithoutCallingJoker() {
    View view = view(0, 1, new RevealedFriend(new Seat(0)), new CardFriend(card("D4")),
        Trump.NO_TRUMP,
        "HK H5 C3", List.of(), history(1, reserved("HK H5 C3", List.of()), Trump.NO_TRUMP),
        List.of(new Bid(14, Trump.HEART), new Bid(15, Trump.NO_TRUMP)));
    Action action = policy.choose(view);
    assertEquals(card("HK"), action.card());
    assertFalse(((StandardPlay) action).call());
  }

  @Test
  @DisplayName("노기루 프렌드는 확보된 클럽 A와 K를 먼저 사용한 뒤 반환할 수 있다")
  void takesOwnMastersBeforeReturn() {
    List<PublicPlay> plays = List.of(play(0, "C3"), play(1, "SA"), play(2, "JK"), play(3, "D2"),
        play(4, "H4"));
    var history = List.of(new PublicTrick(new TrickNumber(1), plays, new Seat(1)));
    View view = view(1, 0, new RevealedFriend(new Seat(1)), new CardFriend(card("SA")),
        Trump.NO_TRUMP,
        "CA CK S6", List.of(), history,
        List.of(new Bid(14, Trump.SPADE), new Bid(15, Trump.NO_TRUMP)));
    assertEquals(card("CA"), policy.choose(view).card());
  }

  @Test
  @DisplayName("프렌드는 야당의 사이드 잔여와 관계없이 지는 물패를 높은 것부터 따른다")
  void signalsDescendingLowCards() {
    View view = view(1, 0, new RevealedFriend(new Seat(1)), new CardFriend(card("C4")),
        Trump.NO_TRUMP,
        "H4 H6", List.of(play(0, "HA")), List.of(), List.of());
    assertEquals(card("H6"), policy.choose(view).card());
  }

  @Test
  @DisplayName("9구 조커를 일반 승리 카드보다 먼저 사용해 만료를 피한다")
  void usesJokerBeforeLastTrick() {
    String owned = "JK C2";
    View view = view(0, 0, NoFriend.INSTANCE, SpecialFriend.NONE, Trump.NO_TRUMP,
        owned, List.of(), history(8, reserved(owned, List.of()), Trump.NO_TRUMP), List.of());
    assertInstanceOf(JokerLead.class, policy.choose(view));
  }

  @Test
  @DisplayName("마이티나 조커의 자유 제출을 무문양 확정 증거로 쓰지 않는다")
  void doesNotInferVoidFromSpecialCards() {
    View mighty = view(2, 0, NoFriend.INSTANCE, SpecialFriend.NONE, Trump.NO_TRUMP,
        "H2", List.of(play(0, "HA"), play(1, "SA")), List.of(), List.of());
    assertFalse(new CardKnowledge(mighty).isVoid(new Seat(1), Suit.HEART));
    View ordinary = view(2, 0, NoFriend.INSTANCE, SpecialFriend.NONE, Trump.NO_TRUMP,
        "H2", List.of(play(0, "HA"), play(1, "D3")), List.of(), List.of());
    assertTrue(new CardKnowledge(ordinary).isVoid(new Seat(1), Suit.HEART));
  }

  @Test
  @DisplayName("조커가 실제로 선두를 잡았다면 마이티로 회수할 수 있다")
  void capturesActuallyPlayedJoker() {
    List<PublicPlay> trick = List.of(play(0, "HA"), play(1, "JK"));
    String owned = "SA H2";
    View view = view(2, 0, new RevealedFriend(new Seat(2)), new CardFriend(card("SA")),
        Trump.NO_TRUMP, owned, trick, history(1, reserved(owned, trick), Trump.NO_TRUMP),
        List.of());
    assertEquals(card("SA"), policy.choose(view).card());
  }

  @Test
  @DisplayName("9구 따라내기에서도 일반 승리 카드 대신 만료 직전 조커를 사용한다")
  void usesJokerOnNinthTrickWhileFollowing() {
    List<PublicPlay> trick = List.of(play(0, "H2"));
    String owned = "JK HA";
    View view = view(1, 0, NoFriend.INSTANCE, SpecialFriend.NONE, Trump.NO_TRUMP,
        owned, trick, history(8, reserved(owned, trick), Trump.NO_TRUMP), List.of());
    assertInstanceOf(JokerFollow.class, policy.choose(view));
  }

  @Test
  @DisplayName("기루 K의 확정 승리를 인식하면서 복수 물패 처리용 회수 자원으로 남긴다")
  void countsConsumedSpecialCardsAndTrumpAce() {
    List<PublicPlay> completed = List.of(play(0, "S2"), play(1, "SA"), play(2, "DA"),
        play(3, "JK"), play(4, "C3"));
    Set<Card> reserved = reserved("SK H2", completed);
    var first = history(1, reserved, Trump.SPADE).get(0);
    var history = List.of(first, new PublicTrick(new TrickNumber(2), completed, new Seat(2)));
    View view = view(2, 2, NoFriend.INSTANCE, SpecialFriend.NONE, Trump.SPADE,
        "SK H2", List.of(), history, List.of());
    var action = new StandardPlay((StandardCard) card("SK"), false);
    assertTrue(new PlayFacts(view).isMasterLead(action));
    assertNotEquals(card("SK"), policy.choose(view).card());
    assertTrue(policy.recommend(view).reason().contains("물패"));
  }

  @Test
  @DisplayName("초구 주공의 낮은 삽에는 무효 조커를 버리지 않고 마이티로 지원한다")
  void supportsUnsecuredOpeningInsteadOfThrowingDeadJoker() {
    View view = view(1, 0, UnrevealedFriend.INSTANCE, new CardFriend(card("SA")),
        Trump.DIAMOND, "H3 JK DJ SA C5 H8 D3 H9 D5 CA",
        List.of(play(0, "S5")), List.of(), List.of());
    assertEquals(2, new PlayFacts(view).legalActions().size());
    assertEquals(card("SA"), policy.choose(view).card());
  }

  @Test
  @DisplayName("비점수 카드의 임시 역전으로 주공의 마이티 호출을 대체하지 않는다")
  void supportsUnsecuredDeclarerWithOrdinaryWinner() {
    View view = view(1, 0, UnrevealedFriend.INSTANCE, new CardFriend(card("SA")),
        Trump.DIAMOND, "SA H7 D2 D3 D4 D5 C2 C3 C4 C5",
        List.of(play(0, "H5")), List.of(), List.of());
    assertEquals(card("SA"), policy.choose(view).card());
  }

  @Test
  @DisplayName("같은 손패라도 주공의 A에는 마이티를 덮지 않는다")
  void preservesMightyOnDeclarerTopWithSameHand() {
    View view = view(1, 0, UnrevealedFriend.INSTANCE, new CardFriend(card("SA")),
        Trump.DIAMOND, "SA H7 D2 D3 D4 D5 C2 C3 C4 C5",
        List.of(play(0, "HA")), List.of(), List.of());
    assertEquals(card("H7"), policy.choose(view).card());
  }

  @Test
  @DisplayName("9구 주공이 불안하게 선두이면 조커를 남겨 10구에 버리지 않는다")
  void usesNinthJokerOnUnsecuredDeclarerLead() {
    List<PublicPlay> trick = List.of(play(0, "H2"));
    Set<Card> reserved = reserved("JK CA SA", trick);
    // 모두 같은 문양을 따른 기록으로 구성해 가짜 무문양 증거를 만들지 않습니다.
    List<PublicTrick> previous = new ArrayList<>();
    for (Suit suit : Suit.values()) {
      List<StandardCard> available = DeckFactory.create().stream()
          .filter(c -> c instanceof StandardCard standard && standard.suit() == suit)
          .filter(c -> !reserved.contains(c)).map(StandardCard.class::cast)
          .sorted(
              java.util.Comparator.comparingInt((StandardCard c) -> c.rank().value()).reversed())
          .toList();
      for (int offset : List.of(0, 5)) {
        List<PublicPlay> row = new ArrayList<>();
        for (int seat = 0; seat < 5; seat++) {
          row.add(new PublicPlay(new Seat(seat),
              new StandardPlay(available.get(offset + seat), false)));
        }
        previous.add(new PublicTrick(new TrickNumber(previous.size() + 1), row, new Seat(0)));
      }
    }
    View view = view(1, 0, new RevealedFriend(new Seat(1)), new CardFriend(card("C4")),
        Trump.NO_TRUMP, "JK CA", trick, previous, List.of());
    PlayFacts facts = new PlayFacts(view);
    assertTrue(facts.allyWinning());
    assertFalse(facts.isSecured(facts.currentWinner().orElseThrow()));
    assertEquals(card("JK"), policy.choose(view).card());
  }

  @Test
  @DisplayName("주공의 기루 K에는 따라낼 카드가 점수뿐이어도 마이티를 아낀다")
  void preservesKingPlanWithOnlyPointTrumps() {
    List<PublicPlay> trick = List.of(play(0, "SK"));
    String owned = "DA SQ SJ S10 CA CK CQ CJ H10";
    View view = view(1, 0, UnrevealedFriend.INSTANCE, new CardFriend(card("DA")),
        Trump.SPADE, owned, trick, history(1, reserved(owned, trick), Trump.SPADE), List.of());
    assertEquals(card("S10"), policy.choose(view).card());
  }

}
