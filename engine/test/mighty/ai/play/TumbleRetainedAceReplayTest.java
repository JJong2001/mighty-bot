package mighty.ai.play;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.stream.IntStream;
import mighty.ai.preparation.*;
import mighty.data.CardParser;
import mighty.game.*;
import mighty.game.action.StandardPlay;
import mighty.game.action.Action;
import mighty.game.bidding.*;
import mighty.game.deal.DeckFactory;
import mighty.game.round.RoundState;
import mighty.game.round.RoundSettlement;
import mighty.game.trick.PublicPlay;
import mighty.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/** 전략의 정답을 고정하지 않고, 조커 뒤에도 사이드 A가 남는 반례를 재현합니다. */
class TumbleRetainedAceReplayTest {
  @ParameterizedTest
  @ValueSource(ints = {6, 7})
  void comparesDefenderRepliesWithoutAssumingClubReturn(int trick) {
    var reference = afterWaterLead(trick);
    var observation = reference.currentView();
    assertEquals(trick + 1, observation.trickNumber().value());
    assertEquals(Role.OPPOSITION, observation.role());
    var legal = new PlayFacts(observation).legalActions();
    for (Action reply : legal) {
      var round = afterWaterLead(trick);
      assertEquals(reference.history(), round.history());
      assertEquals(observation.hand(), round.currentView().hand());
      // 야당 본인이 아는 손패에서 합법 응수만 고릅니다. 주공에게는 전달하지 않습니다.
      round.play(new PublicPlay(observation.self(), reply));
      var policy = new RulePlayPolicy();
      int actions = trick * 5 + 1;
      while (round.phase() == GamePhase.PLAY) {
        var view = round.currentView();
        var action = policy.choose(view);
        assertTrue(new PlayFacts(view).legalActions().contains(action));
        round.play(new PublicPlay(view.self(), action));
        assertTrue(++actions <= 50);
      }
      assertEquals(50, actions);
      var result = round.result().orElseThrow();
      System.out.println("COUNTER trick=" + trick + " seat=" + observation.self().index()
          + " reply=" + reply + " points=" + result.teamPoints()
          + " nominal=" + RoundSettlement.nominal(result, false).get(result.declarer()));
      round.history().stream().skip(trick).forEach(t -> System.out.println("REPLY " + t));
    }
  }

  private static RoundState afterWaterLead(int trick) {
    var round = beforeLead(trick);
    round.play(new PublicPlay(round.currentView().self(),
        new StandardPlay((StandardCard) CardParser.parse("S4"), false)));
    var policy = new RulePlayPolicy();
    while (round.history().size() < trick) {
      var view = round.currentView();
      round.play(new PublicPlay(view.self(), policy.choose(view)));
    }
    return round;
  }

  @ParameterizedTest
  @ValueSource(ints = {6, 7})
  void comparesLegalLeadsBeforeSpendingJoker(int trick) {
    var reference = beforeLead(trick);
    var observation = reference.currentView();
    var legal = new PlayFacts(observation).legalActions();
    var usual = new RulePlayPolicy().recommend(observation);
    System.out.println("BEFORE " + trick + " hand=" + observation.hand()
        + " friend=" + observation.context().friendStatus() + " usual=" + usual);
    reference.history().forEach(t -> System.out.println("PUBLIC " + t));
    for (Action candidate : legal) {
      var round = beforeLead(trick);
      assertEquals(reference.history(), round.history());
      assertEquals(observation.hand(), round.currentView().hand());
      assertEquals(legal, new PlayFacts(round.currentView()).legalActions());
      // 후보는 자기 관측에서 열거하고 상대 손패는 정책에 전달하지 않습니다.
      round.play(new PublicPlay(observation.self(), candidate));
      var policy = new RulePlayPolicy();
      int actions = (trick - 1) * 5 + 1;
      while (round.phase() == GamePhase.PLAY) {
        var view = round.currentView();
        var action = policy.choose(view);
        assertTrue(new PlayFacts(view).legalActions().contains(action));
        round.play(new PublicPlay(view.self(), action));
        assertTrue(++actions <= 50);
      }
      assertEquals(50, actions);
      var result = round.result().orElseThrow();
      var balances = new HashMap<Seat, Integer>();
      result.captured().keySet().forEach(s -> balances.put(s, 40));
      var collection = RoundSettlement.collect(result, false, balances);
      System.out.println("PAIRED trick=" + trick + " candidate=" + candidate
          + " baseline=" + candidate.equals(usual.action()) + " points=" + result.teamPoints()
          + " success=" + result.succeeded() + " nominal="
          + RoundSettlement.nominal(result, false).get(result.declarer())
          + " collection40=" + collection);
      round.history().stream().skip(trick - 1).forEach(t -> System.out.println("AFTER " + t));
    }
  }

  private static RoundState beforeLead(int trick) {
    var own = CardParser.parseList("S4 SA D6 DQ DK C6 CK H3 H7 JK");
    var kitty = CardParser.parseList("SK D3 H2");
    var seats = IntStream.range(0, 5).mapToObj(Seat::new).toList();
    var order = PlayOrder.from(new TableLayout(GameMode.FIVE_PLAYER, seats), Set.copyOf(seats));
    var pool = new ArrayList<>(DeckFactory.create());
    pool.removeAll(own);
    pool.removeAll(kitty);
    Collections.shuffle(pool, new Random(0));
    var hands = new HashMap<Seat, List<Card>>();
    hands.put(seats.get(0), own);
    for (int s = 1; s < 5; s++) {
      hands.put(seats.get(s), List.copyOf(pool.subList((s - 1) * 10, s * 10)));
    }
    var round = RoundState.start(order,
        new BiddingResult(seats.get(0), new Bid(15, Trump.NO_TRUMP)), hands, kitty);
    round.takeKitty(seats.get(0));
    round.discard(seats.get(0), new RuleDiscardPolicy().choose(round.discardView(seats.get(0))));
    round.chooseFriend(seats.get(0),
        new RuleFriendPolicy().choose(round.friendView(seats.get(0))));
    var policy = new RulePlayPolicy();
    while (round.history().size() < trick - 1) {
      var view = round.currentView();
      round.play(new PublicPlay(view.self(), policy.choose(view)));
    }
    assertEquals(seats.get(0), round.currentView().self());
    return round;
  }

  @Test
  void comparesThreeLegalContinuationsAfterTheSamePublicPrefix() {
    List<PublicPlay> prefix = null;
    for (String continuation : List.of("S4", "CK", "SA")) {
      var own = CardParser.parseList("S4 SA D6 DQ DK C6 CK H3 H7 JK");
      var kitty = CardParser.parseList("SK D3 H2");
      var seats = IntStream.range(0, 5).mapToObj(Seat::new).toList();
      var order = PlayOrder.from(new TableLayout(GameMode.FIVE_PLAYER, seats), Set.copyOf(seats));
      var pool = new ArrayList<>(DeckFactory.create());
      pool.removeAll(own);
      pool.removeAll(kitty);
      Collections.shuffle(pool, new Random(0));
      var hands = new HashMap<Seat, List<Card>>();
      hands.put(seats.get(0), own);
      for (int s = 1; s < 5; s++) {
        hands.put(seats.get(s), List.copyOf(pool.subList((s - 1) * 10, s * 10)));
      }
      var round = RoundState.start(order,
          new BiddingResult(seats.get(0), new Bid(15, Trump.NO_TRUMP)), hands, kitty);
      round.takeKitty(seats.get(0));
      round.discard(seats.get(0), new RuleDiscardPolicy().choose(round.discardView(seats.get(0))));
      round.chooseFriend(seats.get(0),
          new RuleFriendPolicy().choose(round.friendView(seats.get(0))));
      var policy = new RulePlayPolicy();
      boolean compared = false;
      int actions = 0;
      while (round.phase() == GamePhase.PLAY) {
        var view = round.currentView();
        var decision = policy.recommend(view);
        var action = decision.action();
        if (view.trickNumber().value() == 8 && view.trick().isEmpty()
            && view.self().equals(seats.get(0))) {
          var publicPrefix = round.history().stream().flatMap(t -> t.cards().stream()).toList();
          if (prefix == null) {
            prefix = publicPrefix;
            round.history().forEach(t -> System.out.println("PUBLIC " + t));
            System.out.println("OBSERVED hand=" + view.hand()
                + " friend=" + view.context().friendStatus());
          } else {
            assertEquals(prefix, publicPrefix);
          }
          assertEquals(Set.copyOf(CardParser.parseList("S4 SA CK")), Set.copyOf(view.hand()));
          var facts = new PlayFacts(view);
          assertFalse(facts.cards().publicHistory().cards().contains(CardParser.parse("CA")));
          assertFalse(facts.isSuitTop(CardParser.parse("CK")));
          // 상대 손패를 선택에 전달하지 않고 같은 관측에서 합법 선출만 비교합니다.
          action = new StandardPlay((StandardCard) CardParser.parse(continuation), false);
          assertTrue(facts.legalActions().contains(action));
          System.out.println("continuation=" + continuation + " usual=" + decision);
          compared = true;
        }
        if (view.trickNumber().value() >= 8) {
          System.out.println(continuation + " " + view.trickNumber().value()
              + ":" + view.self().index() + " " + action);
        }
        round.play(new PublicPlay(view.self(), action));
        assertTrue(++actions <= 50);
      }
      assertTrue(compared);
      assertEquals(50, actions);
      System.out.println(continuation + " RESULT " + round.result().orElseThrow());
    }
  }
}
