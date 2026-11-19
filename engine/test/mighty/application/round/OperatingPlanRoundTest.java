package mighty.application.round;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.stream.IntStream;
import mighty.ai.bidding.BiddingHand;
import mighty.ai.planning.NoTrumpPlanner;
import mighty.ai.play.RulePlayPolicy;
import mighty.ai.preparation.*;
import mighty.data.CardParser;
import mighty.game.*;
import mighty.game.action.StandardPlay;
import mighty.game.bidding.*;
import mighty.game.deal.DeckFactory;
import mighty.game.friend.*;
import mighty.game.observation.FriendView;
import mighty.game.round.RoundState;
import mighty.game.trick.PublicPlay;
import mighty.model.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * 실제 규칙으로 50장을 진행하며 역할별 공개 관측만 봇에 전달합니다.
 */
class OperatingPlanRoundTest {

  @Test
  void jointSpecialFriendReturnsSpadeAndUsesJokerForAnotherSuit() {
    var declarerHand = CardParser.parseList("SK SQ SJ S10 S9 S8 CA CK C5 H5");
    var friendHand = CardParser.parseList("SA JK S2 H3 C2 H4 HJ DJ D6 DK");
    var buried = CardParser.parseList("D2 D3 D4");
    var rest = new ArrayList<>(DeckFactory.create());
    rest.removeAll(declarerHand);
    rest.removeAll(friendHand);
    rest.removeAll(buried);
    Collections.shuffle(rest, new Random(7));
    var seats = IntStream.range(0, 5).mapToObj(Seat::new).toList();
    var hands = new LinkedHashMap<Seat, List<Card>>();
    hands.put(seats.get(0), declarerHand);
    hands.put(seats.get(1), friendHand);
    for (int seat = 2; seat < 5; seat++) {
      hands.put(seats.get(seat), List.copyOf(rest.subList((seat - 2) * 10, (seat - 1) * 10)));
    }
    var order = PlayOrder.from(new TableLayout(GameMode.FIVE_PLAYER, seats), new HashSet<>(seats));
    var round = RoundState.start(order,
        new BiddingResult(seats.get(0), new Bid(17, Trump.NO_TRUMP)),
        hands, buried);
    round.takeKitty(seats.get(0));
    round.discard(seats.get(0), buried);
    round.chooseFriend(seats.get(0), new CardFriend(Joker.INSTANCE));
    var policy = new RulePlayPolicy();
    boolean jokerUsed = false;
    var trace = new ArrayList<String>();
    while (round.phase() != GamePhase.FINISHED) {
      var view = round.currentView();
      var action = policy.choose(view);
      if (view.trickNumber().isFirst() && view.self().equals(seats.get(0))) {
        action = new StandardPlay((StandardCard) CardParser.parse("SK"), false);
      }
      if (view.trickNumber().value() == 3 && view.self().equals(seats.get(0)) && view.trick()
          .isEmpty()) {
        assertEquals(CardParser.parse("SQ"), action.card());
      }
      if (view.self().equals(seats.get(1))) {
        if (view.trickNumber().isFirst()) {
          assertEquals(CardParser.parse("SA"), action.card());
        }
        if (view.trickNumber().value() == 2) {
          assertEquals(CardParser.parse("S2"), action.card());
        }
        if (action.card() instanceof Joker) {
          jokerUsed = true;
          Suit declared = action instanceof mighty.game.action.JokerLead lead
              ? lead.declaredSuit() : new mighty.ai.play.PlayFacts(view).conditions().leadSuit();
          assertNotEquals(Suit.SPADE, declared);
          assertEquals(CardParser.parse("C5"), view.trick().get(0).action().card());
        }
      }
      trace.add(view.trickNumber().value() + " seat=" + view.self().index() + " " + action);
      round.play(new PublicPlay(view.self(), action));
    }
    assertTrue(jokerUsed);
    assertTrue(round.result().orElseThrow().succeeded(), String.join("\n", trace));
  }

  @ParameterizedTest
  @CsvSource({
      "A,0", "A,1", "A,2", "A,3",
      "B,0", "B,1", "B,2", "B,3",
      "C,0", "E,0", "D,0", "D,1", "D,2", "D,3"
  })
  void followsTheOperatingPlanAcrossFriendSeats(String name, int seed) {
    String codes = switch (name) {
      case "A", "E" -> "D4 D6 D7 DJ DQ DK DA S4 SA C3";
      case "B" -> "H2 H3 H4 H6 H7 HJ HK HA D6 SA";
      case "C" -> "S3 S5 S7 S8 S10 SQ SK JK DA C5";
      default -> "SJ SQ SK SA D8 DJ DQ DK CK CA";
    };
    var own = CardParser.parseList(codes);
    var plan = NoTrumpPlanner.analyze(BiddingHand.from(own));
    assertTrue(plan.available());
    int partner = 1 + seed % 4;
    Set<Card> forced = new LinkedHashSet<>(List.of(plan.friend()));
    if (name.equals("A") || name.equals("E")) {
      forced.add(CardParser.parse("SK"));
    }
    var rest = new ArrayList<>(DeckFactory.create().stream()
        .filter(c -> !own.contains(c) && !forced.contains(c)).toList());
    Collections.shuffle(rest, new Random(seed));
    var buried = new ArrayList<>(rest.subList(0, 3));
    rest.subList(0, 3).clear();
    var assessed = new mighty.ai.bidding.BiddingHandEvaluator().assess(BiddingHand.from(own))
        .stream()
        .filter(a -> a.trump() == Trump.NO_TRUMP).findFirst().orElseThrow();
    var contract = new Bid(assessed.ceilingPoints(), Trump.NO_TRUMP);
    var selection = new RuleFriendPolicy().choose(
        new FriendView(new Seat(0), contract, own, buried));
    assertEquals(new CardFriend(plan.friend()), selection);
    Map<Seat, List<Card>> hands = new LinkedHashMap<>();
    hands.put(new Seat(0), own);
    int offset = 0;
    for (int seat = 1; seat < 5; seat++) {
      List<Card> hand = new ArrayList<>();
      if (seat == partner) {
        hand.addAll(forced);
      }
      int take = 10 - hand.size();
      hand.addAll(rest.subList(offset, offset + take));
      offset += take;
      hands.put(new Seat(seat), hand);
    }
    var seats = IntStream.range(0, 5).mapToObj(Seat::new).toList();
    var order = PlayOrder.from(new TableLayout(GameMode.FIVE_PLAYER, seats), new HashSet<>(seats));
    Bid original = new Bid(16, name.equals("B") ? Trump.HEART : Trump.DIAMOND);
    var round = RoundState.start(order, new BiddingResult(new Seat(0), original), hands, buried);
    round.takeKitty(new Seat(0));
    round.discard(new Seat(0), buried);
    round.reviseContract(new Seat(0), contract);
    round.chooseFriend(new Seat(0), selection);
    var policy = new RulePlayPolicy();
    int jokerAt = 0;
    while (round.phase() != GamePhase.FINISHED) {
      var view = round.currentView();
      var action = policy.choose(view);
      if (name.equals("E") && view.self().index() == 0 && view.trick().isEmpty()) {
        if (view.trickNumber().value() == 1) {
          action = new mighty.game.action.StandardPlay(
              (StandardCard) CardParser.parse("DJ"), false);
        }
        if (view.trickNumber().value() == 2) {
          action = new mighty.game.action.StandardPlay(
              (StandardCard) CardParser.parse("S4"), false);
        }
      }
      if ((name.equals("A") || name.equals("E")) && action.card() instanceof Joker) {
        jokerAt = view.trickNumber().value();
      }
      round.play(new PublicPlay(view.self(), action));
    }
    var result = round.result().orElseThrow();
    assertTrue(result.succeeded(), name + seed + ": " + result.teamPoints());
    if (name.equals("A")) {
      assertEquals(9, jokerAt, "자력 순회를 새 호출로 오해하지 않아야 합니다.");
    }
    if (name.equals("E")) {
      assertEquals(10, jokerAt, "일반 탑카로 이미 도움을 줬다면 조커가 선을 빼앗을 필요가 없습니다.");
    }
  }

}
