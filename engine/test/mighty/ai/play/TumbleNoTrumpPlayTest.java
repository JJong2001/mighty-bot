package mighty.ai.play;

import static mighty.ai.play.PlayTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import mighty.ai.bidding.*;
import mighty.ai.planning.*;
import mighty.ai.preparation.*;
import mighty.data.CardParser;
import mighty.game.*;
import mighty.game.bidding.*;
import mighty.game.friend.*;
import mighty.game.observation.*;
import mighty.game.trick.PublicTrick;
import mighty.model.*;
import org.junit.jupiter.api.Test;

class TumbleNoTrumpPlayTest {

  private static final Trump NT = Trump.NO_TRUMP;
  private static final Bid CONTRACT = new Bid(15, NT);
  private static final CardFriend ACE = new CardFriend(card("DA"));
  private static final String INITIAL = "S4 SA D6 DQ DK C6 CK H3 H7 JK";
  private static final String SEEN = INITIAL + " SK D3 H2";
  private static final String KEPT = "S4 SK SA D3 D6 DQ DK C6 CK JK";
  private static final String BURIED = "H2 H3 H7";

  @Test
  void exampleConnectsBidDiscardFriendAndOpening() {
    var hand = BiddingHand.from(CardParser.parseList(INITIAL));
    var assessment = new BiddingHandEvaluator().assess(hand).stream()
        .filter(a -> a.trump() == NT).findFirst().orElseThrow();
    assertTrue(assessment.canCompete());
    assertEquals(15, assessment.openingPoints());
    var seats = java.util.stream.IntStream.range(0, 5).mapToObj(Seat::new).toList();
    var auction = BiddingState.start(new mighty.game.deal.DealParticipants(
        mighty.game.deal.DealFormat.FIVE_PLAYER_OPENING, seats), new Seat(0));
    assertEquals(new BidOffer(CONTRACT), new RuleBiddingPolicy().choose(new BiddingView(
        new Seat(0), CardParser.parseList(INITIAL), 0, auction)));
    var buried = new RuleDiscardPolicy().choose(new DiscardView(new Seat(0), CONTRACT,
        CardParser.parseList(SEEN)));
    assertEquals(new HashSet<>(CardParser.parseList(BURIED)), new HashSet<>(buried));
    var friend = new RuleFriendPolicy().choose(new FriendView(new Seat(0), CONTRACT,
        CardParser.parseList(KEPT), buried));
    assertEquals(ACE, friend);
    var first = view(0, 0, NT, 15, KEPT, BURIED, friend, UnrevealedFriend.INSTANCE, List.of(),
        List.of());
    assertEquals(card("C6"), new RulePlayPolicy().choose(first).card());
    var prepared = new PreparationPlanner().choose(new DiscardView(new Seat(0), CONTRACT,
        CardParser.parseList(SEEN)));
    assertEquals(CONTRACT, prepared.contract());
    assertEquals(ACE, prepared.friend());
    assertEquals(new HashSet<>(buried), new HashSet<>(prepared.discarded()));
  }

  @Test
  void modeRequiresControlsCoverAndLowContract() {
    assertTrue(
        TumbleNoTrumpPlan.find(BiddingHand.from(CardParser.parseList(INITIAL)), List.of(), CONTRACT)
            .isPresent());
    for (String cards : List.of(INITIAL.replace("JK", "H8"), INITIAL.replace("SA", "S8"),
        INITIAL.replace("S4", "H8"), INITIAL.replace("C6", "H8"))) {
      assertTrue(
          TumbleNoTrumpPlan.find(BiddingHand.from(CardParser.parseList(cards)), List.of(), CONTRACT)
              .isEmpty());
    }
    for (Bid bid : List.of(new Bid(17, NT), new Bid(15, Trump.DIAMOND))) {
      assertTrue(
          TumbleNoTrumpPlan.find(BiddingHand.from(CardParser.parseList(INITIAL)), List.of(), bid)
              .isEmpty());
    }
    assertTrue(
        TumbleNoTrumpPlan.find(BiddingHand.from(CardParser.parseList(KEPT.replace("C6", "CA"))),
            List.of(), CONTRACT).isEmpty());
  }

  @Test
  void yieldsLowSpadeAttackButRecoversLoadedPoints() {
    var first = row(1, NT, p(0, "C6"), p(1, "CA"), p(2, "C2"), p(3, "C4"), p(4, "C5"));
    String hand = "S4 SK SA D3 D6 DQ DK CK JK";
    var quiet = view(0, 0, NT, 15, hand, BURIED, ACE, UnrevealedFriend.INSTANCE, List.of(first),
        List.of(p(1, "S9"), p(2, "S2"), p(3, "S3"), p(4, "S5")));
    // 합의한 닷노 운영: 작은 마공은 흘리고 K는 점수가 실린 마공에 남깁니다.
    assertEquals(card("S4"), new RulePlayPolicy().choose(quiet).card());
    var spadePoints = view(0, 0, NT, 15, hand, BURIED, ACE, UnrevealedFriend.INSTANCE, List.of(first),
        List.of(p(1, "S9"), p(2, "SJ"), p(3, "S10"), p(4, "S5")));
    assertEquals(card("SK"), new RulePlayPolicy().choose(spadePoints).card());
    var loaded = view(0, 0, NT, 15, hand, BURIED, ACE, UnrevealedFriend.INSTANCE, List.of(first),
        List.of(p(1, "HA"), p(2, "HK"), p(3, "HQ"), p(4, "H4")));
    assertEquals(card("SA"), new RulePlayPolicy().choose(loaded).card());
  }

  @Test
  void keepsMightyOnAnUnloadedAttackWhenNoOrdinaryTopCanRecover() {
    var first = row(1, NT, p(0, "C6"), p(1, "CA"), p(2, "C2"), p(3, "C4"), p(4, "C5"));
    var v = view(0, 0, NT, 15, "S4 SA D6 DQ DK CK H3 H7 JK", "S2 D2 H2", ACE,
        UnrevealedFriend.INSTANCE, List.of(first),
        List.of(p(1, "S9"), p(2, "S3"), p(3, "S6"), p(4, "S5")));
    assertEquals(card("S4"), new RulePlayPolicy().choose(v).card());
  }

  @Test
  void mustRecoverFewerThanThreePointsWhenPassingWouldDefeatTheContract() {
    var first = row(1, NT, p(0, "C6"), p(1, "CA"), p(2, "CQ"), p(3, "CJ"), p(4, "C10"));
    var v = view(0, 0, NT, 15, "S4 SK SA D3 D6 DQ DK CK JK", BURIED, ACE,
        UnrevealedFriend.INSTANCE, List.of(first),
        List.of(p(1, "HA"), p(2, "HK"), p(3, "H4"), p(4, "H5")));
    assertEquals(card("SA"), new RulePlayPolicy().choose(v).card());
  }

  @Test
  void rotatesPrimaryAndDevelopmentSuitsWithoutHardcodedCards() {
    var hand = CardParser.parseList("S4 SA H6 HQ HK D6 DK C3 C7 JK");
    var selected = new RuleFriendPolicy().choose(new FriendView(new Seat(0), CONTRACT, hand,
        CardParser.parseList("C2 C4 S2")));
    assertEquals(new CardFriend(card("HA")), selected);
    var v = view(0, 0, NT, 15, "S4 SA H6 HQ HK D6 DK C3 C7 JK", "C2 C4 S2", selected,
        UnrevealedFriend.INSTANCE, List.of(), List.of());
    assertEquals(card("D6"), new RulePlayPolicy().choose(v).card());
    var ordinary = view(0, 0, NT, 17, "S4 SA H6 HQ HK D6 DK C3 C7 JK", "C2 C4 S2", selected,
        UnrevealedFriend.INSTANCE, List.of(), List.of());
    assertFalse(new RulePlayPolicy().recommend(ordinary).reason().contains("우당탕탕"));
  }

  @Test
  void completesTenTricksAcrossDifferentPartnerPositionsAndDefenderHoldings() {
    var seats = java.util.stream.IntStream.range(0, 5).mapToObj(Seat::new).toList();
    var order = PlayOrder.from(new TableLayout(GameMode.FIVE_PLAYER, seats), Set.copyOf(seats));
    var own = CardParser.parseList(INITIAL);
    var kitty = CardParser.parseList("SK D3 H2");
    for (int seed : List.of(0, 1, 6, 7)) {
      var pool = new ArrayList<>(mighty.game.deal.DeckFactory.create());
      pool.removeAll(own);
      pool.removeAll(kitty);
      Collections.shuffle(pool, new Random(seed));
      var hands = new HashMap<Seat, List<Card>>();
      hands.put(seats.get(0), own);
      for (int index = 1; index < 5; index++) {
        hands.put(seats.get(index), List.copyOf(pool.subList((index - 1) * 10, index * 10)));
      }
      var round = mighty.game.round.RoundState.start(order,
          new BiddingResult(seats.get(0), CONTRACT), hands, kitty);
      round.takeKitty(seats.get(0));
      round.discard(seats.get(0), new RuleDiscardPolicy().choose(round.discardView(seats.get(0))));
      round.chooseFriend(seats.get(0),
          new RuleFriendPolicy().choose(round.friendView(seats.get(0))));
      var policy = new RulePlayPolicy();
      assertEquals(card("C6"), policy.choose(round.currentView()).card());
      int turns = 0;
      while (round.phase() == GamePhase.PLAY) {
        var current = round.currentView();
        round.play(new mighty.game.trick.PublicPlay(current.self(), policy.choose(current)));
        assertTrue(++turns <= 50);
      }
      assertEquals(50, turns);
      assertEquals(10, round.history().size());
      assertTrue(round.result().isPresent());
    }
  }
}
