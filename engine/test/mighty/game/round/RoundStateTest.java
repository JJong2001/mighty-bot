package mighty.game.round;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;
import mighty.ai.play.BaselinePlayPolicy;
import mighty.ai.preparation.BaselineDiscardPolicy;
import mighty.application.round.RoundService;
import mighty.game.*;
import mighty.game.action.JokerFollow;
import mighty.game.action.StandardPlay;
import mighty.game.bidding.Bid;
import mighty.game.bidding.BiddingResult;
import mighty.game.deal.DeckFactory;
import mighty.game.friend.*;
import mighty.game.observation.*;
import mighty.game.rule.LegalActionContext;
import mighty.game.rule.LegalActions;
import mighty.game.rule.Rules;
import mighty.game.trick.PublicPlay;
import mighty.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RoundStateTest {

  private record Fixture(RoundState round, Map<Seat, List<Card>> hands, List<Card> kitty,
                         PlayOrder order) {

  }

  private static Fixture fixture(long seed, Trump trump, boolean sixSeatTable) {
    List<Seat> tableSeats = IntStream.range(0, sixSeatTable ? 6 : 5).mapToObj(Seat::new).toList();
    Set<Seat> active = new HashSet<>(tableSeats);
    if (sixSeatTable) {
      active.remove(new Seat(2));
    }
    PlayOrder order = PlayOrder.from(new TableLayout(
        sixSeatTable ? GameMode.SIX_PLAYER : GameMode.FIVE_PLAYER, tableSeats), active);
    List<Card> deck = new ArrayList<>(DeckFactory.create());
    Collections.shuffle(deck, new Random(seed));
    Map<Seat, List<Card>> hands = new HashMap<>();
    for (int index = 0; index < order.seats().size(); index++) {
      hands.put(order.seats().get(index),
          new ArrayList<>(deck.subList(index * 10, index * 10 + 10)));
    }
    List<Card> kitty = new ArrayList<>(deck.subList(50, 53));
    return new Fixture(RoundState.start(order,
        new BiddingResult(order.seats().get(0), new Bid(13, trump)), hands, kitty), hands, kitty,
        order);
  }

  private static FriendView prepare(Fixture fixture) {
    Seat declarer = fixture.order().seats().get(0);
    fixture.round().takeKitty(declarer);
    fixture.round().discard(declarer,
        new BaselineDiscardPolicy().choose(fixture.round().discardView(declarer)));
    return fixture.round().friendView(declarer);
  }

  @Test
  @DisplayName("잘못된 초기 배분을 거절하고 전달받은 원본 목록 변경에 영향받지 않는다")
  void validatesAndCopiesInitialDeal() {
    Fixture fixture = fixture(1, Trump.SPADE, false);
    Map<Seat, List<Card>> duplicate = new HashMap<>(fixture.hands());
    List<Card> replaced = new ArrayList<>(duplicate.get(new Seat(1)));
    replaced.set(0, duplicate.get(new Seat(0)).get(0));
    duplicate.put(new Seat(1), replaced);
    assertThrows(IllegalArgumentException.class, () -> RoundState.start(fixture.order(),
        new BiddingResult(new Seat(0), new Bid(13, Trump.SPADE)), duplicate, fixture.kitty()));
    fixture.hands().get(new Seat(0)).clear();
    fixture.kitty().clear();
    fixture.round().takeKitty(new Seat(0));
    assertEquals(13, fixture.round().discardView(new Seat(0)).hand().size());
  }

  @Test
  @DisplayName("바닥 수령 시점과 주공 전용 관측을 보호한다")
  void protectsKittyAndDiscards() {
    Fixture fixture = fixture(2, Trump.NO_TRUMP, false);
    RoundState round = fixture.round();
    assertThrows(IllegalStateException.class, () -> round.discardView(new Seat(0)));
    assertThrows(IllegalArgumentException.class, () -> round.takeKitty(new Seat(1)));
    assertEquals(GamePhase.BEFORE_KITTY, round.phase());
    round.takeKitty(new Seat(0));
    assertThrows(IllegalArgumentException.class, () -> round.discardView(new Seat(1)));
    DiscardView snapshot = round.discardView(new Seat(0));
    assertTrue(snapshot.hand().containsAll(fixture.kitty()));
    assertThrows(UnsupportedOperationException.class, () -> snapshot.hand().clear());
    List<Card> chosen = snapshot.hand().subList(0, 3);
    round.discard(new Seat(0), chosen);
    assertEquals(13, snapshot.hand().size());
    assertEquals(10, round.friendView(new Seat(0)).hand().size());
    assertThrows(IllegalArgumentException.class, () -> round.friendView(new Seat(1)));
    round.chooseFriend(new Seat(0), SpecialFriend.NONE);
    View first = round.currentView();
    assertInstanceOf(DeclarerInfo.class, first.privateInfo());
    round.play(new PublicPlay(first.self(), new BaselinePlayPolicy().choose(first)));
    View second = round.currentView();
    assertInstanceOf(NonDeclarerInfo.class, second.privateInfo());
    assertEquals(fixture.hands().get(second.self()), second.hand());
    assertEquals(10, first.hand().size());
  }

  @Test
  @DisplayName("잘못된 버리기와 차례 및 카드 행동을 거절할 때 상태를 보존한다")
  void rejectsInvalidActionsAtomically() {
    Fixture fixture = fixture(3, Trump.SPADE, false);
    RoundState round = fixture.round();
    round.takeKitty(new Seat(0));
    List<Card> hand = round.discardView(new Seat(0)).hand();
    assertThrows(IllegalArgumentException.class,
        () -> round.discard(new Seat(0), hand.subList(0, 2)));
    assertThrows(IllegalArgumentException.class, () -> round.discard(new Seat(0),
        List.of(hand.get(0), hand.get(0), hand.get(1))));
    Card outsider = fixture.hands().get(new Seat(1)).get(0);
    assertThrows(IllegalArgumentException.class, () -> round.discard(new Seat(0),
        List.of(hand.get(0), hand.get(1), outsider)));
    assertEquals(13, round.discardView(new Seat(0)).hand().size());
    round.discard(new Seat(0), hand.subList(0, 3));
    assertThrows(IllegalArgumentException.class,
        () -> round.chooseFriend(new Seat(1), SpecialFriend.NONE));
    round.chooseFriend(new Seat(0), SpecialFriend.NONE);
    View before = round.currentView();
    assertThrows(IllegalArgumentException.class,
        () -> round.play(new PublicPlay(new Seat(1), new BaselinePlayPolicy().choose(before))));
    assertThrows(IllegalArgumentException.class,
        () -> round.play(new PublicPlay(new Seat(0), new JokerFollow())));
    assertTrue(round.currentTrick().isEmpty());
    assertEquals(before.hand(), round.currentView().hand());
  }

  @Test
  @DisplayName("카드 프렌드는 지정 카드를 내기 전까지 공개되지 않고 본인만 자기 역할을 안다")
  void revealsFriendOnlyOnPublicPlay() {
    Fixture fixture = fixture(4, Trump.HEART, false);
    prepare(fixture);
    Seat friend = new Seat(1);
    Card designated = fixture.hands().get(friend).get(0);
    fixture.round().chooseFriend(new Seat(0), new CardFriend(designated));
    BaselinePlayPolicy policy = new BaselinePlayPolicy();
    boolean revealed = false;
    while (fixture.round().phase() != GamePhase.FINISHED) {
      View view = fixture.round().currentView();
      if (!revealed) {
        assertInstanceOf(UnrevealedFriend.class, view.context().friendStatus());
        if (view.self().equals(friend)) {
          assertEquals(Role.FRIEND, view.role());
        }
      } else {
        assertEquals(new RevealedFriend(friend), view.context().friendStatus());
      }
      var action = policy.choose(view);
      fixture.round().play(new PublicPlay(view.self(), action));
      if (action.card().equals(designated)) {
        revealed = true;
      }
    }
    assertTrue(revealed);
    assertEquals(friend, fixture.round().result().orElseThrow().friend().orElseThrow());
  }

  @Test
  @DisplayName("버린 카드와 자기 카드 프렌드는 실제 혼자이며 버린 카드 위치는 미리 공개하지 않는다")
  void handlesDiscardedAndOwnCardFriends() {
    for (boolean discardedFriend : new boolean[]{
        false, true
    }) {
      Fixture fixture = fixture(5, Trump.CLUB, false);
      FriendView view = prepare(fixture);
      Card card = discardedFriend ? view.discardedCards().get(0) : view.hand().get(0);
      fixture.round().chooseFriend(new Seat(0), new CardFriend(card));
      assertInstanceOf(UnrevealedFriend.class,
          fixture.round().currentView().context().friendStatus());
      RoundResult result = RoundService.baseline().playToEnd(fixture.round());
      assertTrue(result.friend().isEmpty());
    }
  }

  @Test
  @DisplayName("초구 프렌드는 첫 구 승자로 확정하고 주공 승리면 실제 혼자다")
  void firstTrickDeterminesFriend() {
    for (int seed = 0; seed < 20; seed++) {
      Fixture fixture = fixture(seed, Trump.NO_TRUMP, false);
      prepare(fixture);
      fixture.round().chooseFriend(new Seat(0), SpecialFriend.FIRST_TRICK);
      assertEquals(Role.DECLARER, fixture.round().currentView().role());
      BaselinePlayPolicy policy = new BaselinePlayPolicy();
      while (fixture.round().history().isEmpty()) {
        View view = fixture.round().currentView();
        if (!view.self().equals(new Seat(0))) {
          assertEquals(Role.UNDETERMINED, view.role());
        }
        fixture.round().play(new PublicPlay(view.self(), policy.choose(view)));
      }
      Seat winner = fixture.round().history().get(0).winner();
      FriendStatus expected = winner.equals(new Seat(0))
          ? NoFriend.INSTANCE : new RevealedFriend(winner);
      assertEquals(expected, fixture.round().currentView().context().friendStatus());
    }
  }

  @Test
  @DisplayName("모든 기루와 프렌드 방식에서 400판을 합법적으로 완주하고 카드와 점수를 보존한다")
  void completesAndConservesCardsAndPoints() {
    for (Trump trump : Trump.values()) {
      for (int seed = 0; seed < 20; seed++) {
        for (int friendMode = 0; friendMode < 4; friendMode++) {
          Fixture fixture = fixture(seed, trump, seed % 2 == 0);
          FriendView preparation = prepare(fixture);
          List<Card> discarded = preparation.discardedCards();
          FriendSelection selection = switch (friendMode) {
            case 0 -> SpecialFriend.NONE;
            case 1 -> SpecialFriend.FIRST_TRICK;
            case 2 -> new CardFriend(fixture.hands().get(fixture.order().seats().get(1)).get(0));
            default -> new CardFriend(discarded.get(0));
          };
          fixture.round().chooseFriend(fixture.order().seats().get(0), selection);
          assertTrue(fixture.round().result().isEmpty());
          int actions = 0;
          BaselinePlayPolicy policy = new BaselinePlayPolicy();
          while (fixture.round().phase() != GamePhase.FINISHED) {
            View view = fixture.round().currentView();
            assertEquals(10 - view.context().history().size(), view.hand().size());
            if (view.role() == Role.DECLARER) {
              assertEquals(discarded, ((DeclarerInfo) view.privateInfo()).discardedCards());
            } else {
              assertInstanceOf(NonDeclarerInfo.class, view.privateInfo());
            }
            var chosen = policy.choose(view);
            assertTrue(LegalActions.legalActions(new LegalActionContext(
                view.hand(), trump, view.trickNumber(), view.trick())).contains(chosen));
            fixture.round().play(new PublicPlay(view.self(), chosen));
            actions++;
            assertTrue(actions <= 50);
          }
          assertEquals(50, actions);
          assertEquals(10, fixture.round().history().size());
          List<Card> played = fixture.round().history().stream()
              .flatMap(trick -> trick.cards().stream()).map(play -> play.action().card()).toList();
          assertEquals(50, new HashSet<>(played).size());
          assertTrue(Collections.disjoint(played, discarded));
          Set<Card> all = new HashSet<>(played);
          all.addAll(discarded);
          assertEquals(new HashSet<>(DeckFactory.create()), all);
          for (var completed : fixture.round().history()) {
            assertEquals(completed.winner(), Rules.currentWinner(trump,
                completed.number(), completed.cards()).orElseThrow().seat());
            fixture.order().requireSequence(completed.leader(),
                completed.cards().stream().map(PublicPlay::seat).toList());
          }
          RoundResult result = fixture.round().result().orElseThrow();
          int expectedTeam = discarded.stream().mapToInt(Card::points).sum();
          for (var completed : fixture.round().history()) {
            if (completed.winner().equals(result.declarer())
                || result.friend().filter(completed.winner()::equals).isPresent()) {
              expectedTeam += completed.points();
            }
          }
          assertEquals(expectedTeam, result.teamPoints());
          assertEquals(20, result.teamPoints() + result.oppositionPoints());
          assertEquals(expectedTeam >= 13, result.succeeded());
          assertTrue(fixture.round().currentSeat().isEmpty());
          assertThrows(IllegalStateException.class, () -> fixture.round().currentView());
          assertThrows(IllegalStateException.class,
              () -> RoundService.baseline().advance(fixture.round()));
        }
      }
    }
  }

  @Test
  @DisplayName("조커콜 시 마이티와 조커가 없는 일반 카드로 강제 제출을 회피할 수 없다")
  void enforcesJokerCallAtSubmissionBoundary() {
    // 합법 규칙 단위 테스트와 별도로 실제 상태 경계에서도 불법 행동을 거절하는지 확인합니다.
    boolean checked = false;
    for (int seed = 0; seed < 80 && !checked; seed++) {
      Fixture fixture = fixture(seed, Trump.NO_TRUMP, false);
      prepare(fixture);
      fixture.round().chooseFriend(new Seat(0), SpecialFriend.NONE);
      while (fixture.round().phase() != GamePhase.FINISHED && !checked) {
        View view = fixture.round().currentView();
        List<mighty.game.action.Action> legal = LegalActions.legalActions(new LegalActionContext(
            view.hand(), view.contract().trump(), view.trickNumber(), view.trick()));
        var call = legal.stream()
            .filter(action -> action instanceof StandardPlay play && play.call()).findFirst();
        fixture.round().play(new PublicPlay(view.self(),
            call.orElseGet(() -> new BaselinePlayPolicy().choose(view))));
        if (fixture.round().phase() != GamePhase.PLAY) {
          continue;
        }
        View next = fixture.round().currentView();
        if (next.hand().contains(Joker.INSTANCE) && !next.trick().isEmpty()
            && next.trick().get(0).action() instanceof StandardPlay opening && opening.call()) {
          var forbidden = next.hand().stream().filter(card -> card instanceof StandardCard
              && !card.equals(Rules.mighty(next.contract().trump()))).findFirst();
          if (forbidden.isPresent()) {
            assertThrows(IllegalArgumentException.class, () -> fixture.round().play(
                new PublicPlay(next.self(),
                    new StandardPlay((StandardCard) forbidden.get(), false))));
            checked = true;
          }
        }
      }
    }
    assertTrue(checked, "실제 조커콜 상황을 검사해야 합니다.");
  }

  @Test
  @DisplayName("초구 프렌드를 지정한 주공이 마이티로 초구를 이기면 실제 프렌드가 없다")
  void declarerWinningFirstTrickHasNoFriend() {
    boolean checked = false;
    for (int seed = 0; seed < 100 && !checked; seed++) {
      Fixture fixture = fixture(seed, Trump.NO_TRUMP, false);
      FriendView preparation = prepare(fixture);
      StandardCard mighty = Rules.mighty(Trump.NO_TRUMP);
      if (!preparation.hand().contains(mighty)) {
        continue;
      }
      fixture.round().chooseFriend(new Seat(0), SpecialFriend.FIRST_TRICK);
      fixture.round().play(new PublicPlay(new Seat(0), new StandardPlay(mighty, false)));
      while (fixture.round().history().isEmpty()) {
        View view = fixture.round().currentView();
        fixture.round().play(new PublicPlay(view.self(), new BaselinePlayPolicy().choose(view)));
      }
      assertEquals(new Seat(0), fixture.round().history().get(0).winner());
      assertEquals(NoFriend.INSTANCE, fixture.round().currentView().context().friendStatus());
      assertTrue(RoundService.baseline().playToEnd(fixture.round()).friend().isEmpty());
      checked = true;
    }
    assertTrue(checked, "주공이 마이티를 보유한 상황을 확인해야 합니다.");
  }

  @Test
  void replaysAnAlternativeOpeningOnTheSameDeal() {
    var first = fixture(31, Trump.DIAMOND, false);
    var second = fixture(31, Trump.DIAMOND, false);
    prepare(first);
    prepare(second);
    var declarer = new Seat(0);
    first.round().chooseFriend(declarer, SpecialFriend.NONE);
    second.round().chooseFriend(declarer, SpecialFriend.NONE);
    var policy = new BaselinePlayPolicy();
    var view = first.round().currentView();
    var alternative = LegalActions.legalActions(new LegalActionContext(
            view.hand(), view.contract().trump(), view.trickNumber(), view.trick())).stream()
        .filter(action -> !action.equals(policy.choose(view))).findFirst().orElseThrow();
    var replay = mighty.simulation.RoundReplay.run(first.round(), policy, Map.of(1, alternative));
    second.round().play(new PublicPlay(declarer, alternative));
    var continuation = mighty.simulation.RoundReplay.run(second.round(), policy, Map.of());
    assertEquals(50, replay.plays().size());
    assertEquals(alternative, replay.plays().get(0).action());
    assertEquals(replay.plays().subList(1, 50), continuation.plays());
    assertEquals(replay.result(), continuation.result());
  }

}
