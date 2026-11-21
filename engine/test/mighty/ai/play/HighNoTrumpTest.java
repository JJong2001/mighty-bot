package mighty.ai.play;

import static mighty.ai.play.PlayTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import mighty.ai.preparation.*;
import mighty.data.CardParser;
import mighty.game.Seat;
import mighty.game.action.JokerLead;
import mighty.game.bidding.*;
import mighty.game.friend.*;
import mighty.game.observation.*;
import mighty.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HighNoTrumpTest {
  private static final String HAND = "SA SK S5 S3 D10 HA HK HQ H2 JK";
  private static final Bid AWARD = new Bid(17, Trump.NO_TRUMP);
  private static final Seat SELF = new Seat(0);
  private static final List<PublicBid> CLUB_BID = List.of(new PublicBid(new Seat(1),
      new BidOffer(new Bid(16, Trump.CLUB))));

  @ParameterizedTest
  @CsvSource({
      "D2 D3 D4, D10 D2 D3, 17, SQ, HA, ''",
      "D2 D3 H4, D10 D2 D3, 20, SQ, S3, HEART",
      "D2 D3 S9, D10 D2 D3, 20, SQ, S3, SPADE HEART",
      "D2 D3 C2, D10 D2 D3, 17, CA, C2, ''"
  })
  void plansFromActualKitty(String kitty, String buried, int points, String called,
      String lead, String signals) {
    var planner = new PreparationPlanner();
    assertEquals(AWARD, planner.beforeKittyContract(CardParser.parseList(HAND), AWARD, AWARD));
    var seen = CardParser.parseList(HAND + " " + kitty);
    var plan = planner.choose(new DiscardView(SELF, AWARD, seen, CLUB_BID), AWARD);
    var bid = new Bid(points, Trump.NO_TRUMP);
    assertEquals(bid, plan.contract());
    assertEquals(Set.copyOf(CardParser.parseList(buried)), Set.copyOf(plan.discarded()));
    var friend = new CardFriend(card(called));
    assertEquals(friend, plan.friend());
    var kept = new ArrayList<>(seen);
    kept.removeAll(plan.discarded());
    var observation = new FriendView(SELF, AWARD, kept, plan.discarded(), List.of(AWARD), CLUB_BID);
    var expected = new ArrayList<Bid>();
    if (!signals.isBlank()) {
      for (String suit : signals.split(" ")) {
        expected.add(new Bid(20, Trump.valueOf(suit)));
      }
      expected.add(bid);
    }
    assertEquals(expected, planner.declarations(observation, AWARD, List.of(AWARD)));
    var owned = String.join(" ", kept.stream().map(Card::toCode).toList());
    var opening = view(0, 0, Trump.NO_TRUMP, points, owned, buried, friend,
        UnrevealedFriend.INSTANCE, List.of(), List.of());
    assertEquals(card(lead), new RulePlayPolicy().choose(opening).card());
    if (called.equals("CA")) {
      assertEquals(new CardFriend(card("SQ")), new RuleFriendPolicy().choose(
          new FriendView(SELF, bid, kept, plan.discarded())));
    }
  }

  @Test
  void convertsOnlyBeforeTakingKittyWithJokerAndNoMighty() {
    var planner = new PreparationPlanner();
    var hand = CardParser.parseList("JK SK S5 S3 D10 HA HK HQ H2 CK");
    assertEquals(new Bid(18, Trump.HEART), planner.beforeKittyContract(hand, AWARD, AWARD));
    var sixteen = new Bid(16, Trump.HEART);
    assertEquals(sixteen, planner.beforeKittyContract(CardParser.parseList(HAND), sixteen, sixteen));
    var after = planner.choose(new DiscardView(SELF, sixteen, CardParser.parseList(HAND + " D2 D3 D4")));
    assertEquals(AWARD, after.contract());
  }

  @Test
  void opensWithSeparateEntryThenLooksForJokerWithLowHeart() {
    var friend = new CardFriend(Joker.INSTANCE);
    var first = view(0, 0, Trump.NO_TRUMP, 16, "SA SK S5 S3 D10 HA HK H3 H2 CK", "D2 D3 D4",
        friend, UnrevealedFriend.INSTANCE, List.of(), List.of());
    var policy = new RulePlayPolicy();
    assertEquals(card("SK"), policy.choose(first).card());
    var history = List.of(row(1, Trump.NO_TRUMP, p(0,"SK"), p(1,"S2"), p(2,"S6"), p(3,"S7"), p(4,"S8")));
    var next = view(0, 0, Trump.NO_TRUMP, 16, "SA S5 S3 D10 HA HK H3 H2 CK", "D2 D3 D4",
        friend, UnrevealedFriend.INSTANCE, history, List.of());
    assertEquals(card("H2"), policy.choose(next).card());
  }

  @ParameterizedTest
  @CsvSource({"H10, H4, HA", "S10, S9, SK"})
  void takesFriendsReturnWithOrdinaryWinner(String returned, String extra, String expected) {
    var opening = row(1, Trump.NO_TRUMP, p(0,"S3"), p(1,"SQ"), p(2,"S2"), p(3,"S4"), p(4,"S6"));
    var trick = returned.startsWith("H")
        ? List.of(p(1,returned), p(2,"H3"), p(3,"H5"), p(4,"H6"))
        : List.of(p(1,returned), p(2,"S7"), p(3,"S8"), p(4,"D4"));
    var current = view(0, 0, Trump.NO_TRUMP, 20, "SA SK S5 HA HK HQ H2 JK " + extra,
        "D10 D2 D3", new CardFriend(card("SQ")), new RevealedFriend(new Seat(1)), List.of(opening), trick);
    assertEquals(card(expected), new RulePlayPolicy().choose(current).card());
  }

  @Test
  void takesCalledQueenOnLowSpade() {
    var view = view(1, 0, Trump.NO_TRUMP, 20, "SQ SJ C2 C4 C5 C7 H5 H6 D4 D5", "",
        new CardFriend(card("SQ")), UnrevealedFriend.INSTANCE, List.of(), List.of(p(0, "S3")));
    assertEquals(card("SQ"), new RulePlayPolicy().choose(view).card());
  }

  @ParameterizedTest
  @CsvSource({"HJ, H2", "H10, JK"})
  void sweepsWithJokerOnlyIfHeartsStillBlocked(String lastHeart, String next) {
    var history = List.of(
        row(1, Trump.NO_TRUMP, p(0,"HA"), p(1,"H3"), p(2,"H4"), p(3,"H5"), p(4,"H6")),
        row(2, Trump.NO_TRUMP, p(0,"HK"), p(1,"H7"), p(2,"H8"), p(3,"C2"), p(4,"C3")),
        row(3, Trump.NO_TRUMP, p(0,"HQ"), p(1,"H9"), p(2,lastHeart), p(3,"C4"), p(4,"C5")));
    // H10 또는 HJ도 외부에 남으면 2가 탑이 아니므로, 비교할 나머지 상위는 첫 두 구에 소모합니다.
    if (lastHeart.equals("HJ")) {
      history = List.of(history.get(0),
          row(2, Trump.NO_TRUMP, p(0,"HK"), p(1,"H7"), p(2,"H8"), p(3,"H10"), p(4,"C3")),
          history.get(2));
    }
    var view = view(0, 0, Trump.NO_TRUMP, 17, "SA SK S5 S3 H2 D4 JK", "D10 D2 D3",
        new CardFriend(card("SQ")), UnrevealedFriend.INSTANCE, history, List.of());
    var action = new RulePlayPolicy().choose(view);
    assertEquals(card(next), action.card());
    if (action instanceof JokerLead joker) {
      assertEquals(Suit.HEART, joker.declaredSuit());
    }
  }

  @Test
  void cashesSideWinnerAndReadsDeclarerDiscard() {
    var opening = row(1, Trump.NO_TRUMP, p(0,"C2"), p(1,"CA"), p(2,"C3"), p(3,"C4"), p(4,"C5"));
    var friend = new CardFriend(card("CA"));
    var status = new RevealedFriend(new Seat(1));
    var cash = view(1, 0, Trump.NO_TRUMP, 17, "DA S10 S8 H3 H4 C7 C6 D5 D6", "", friend,
        status, List.of(opening), List.of());
    assertEquals(card("DA"), new RulePlayPolicy().choose(withAuction(cash)).card());
    var beforeDiscard = List.of(p(1,"DA"), p(2,"D4"), p(3,"D5"), p(4,"D6"));
    var declarer = view(0, 0, Trump.NO_TRUMP, 17, "SA SK S5 S3 HA HK HQ H2 JK", "D10 D2 D3",
        friend, status, List.of(opening), beforeDiscard);
    assertEquals(card("S3"), new RulePlayPolicy().choose(withAuction(declarer)).card());
    var second = row(2, Trump.NO_TRUMP, p(1,"DA"), p(2,"D4"), p(3,"D7"), p(4,"D8"), p(0,"S3"));
    var returning = view(1, 0, Trump.NO_TRUMP, 17, "S10 S8 H3 H4 C7 C6 D5 D6", "",
        friend, status, List.of(opening, second), List.of());
    assertEquals(card("S10"), new RulePlayPolicy().choose(withAuction(returning)).card());
    var noSignal = view(1, 0, Trump.NO_TRUMP, 17, "S10 S8 H3 H4 C7 C6 D5 D6 D8", "",
        friend, status, List.of(opening), List.of());
    var side = (StandardCard) new RulePlayPolicy().choose(withAuction(noSignal)).card();
    assertTrue(Set.of(Suit.HEART, Suit.DIAMOND).contains(side.suit()));
  }

  @Test
  void actualRoundCarriesFullAnnouncementsIntoFriendPlay() {
    var owned = CardParser.parseList(HAND);
    var kitty = CardParser.parseList("D2 D3 S9");
    var seats = java.util.stream.IntStream.range(0, 5).mapToObj(Seat::new).toList();
    var order = mighty.game.PlayOrder.from(new mighty.game.TableLayout(mighty.game.GameMode.FIVE_PLAYER,
        seats), Set.copyOf(seats));
    var others = mighty.game.deal.DeckFactory.create().stream()
        .filter(c -> !owned.contains(c) && !kitty.contains(c)).toList();
    var hands = new HashMap<Seat, List<Card>>();
    hands.put(SELF, owned);
    for (int i = 1; i < seats.size(); i++) {
      hands.put(seats.get(i), others.subList(10 * (i - 1), 10 * i));
    }
    var round = mighty.game.round.RoundState.start(order,
        new BiddingResult(SELF, AWARD), hands, kitty, CLUB_BID);
    var service = mighty.application.round.RoundService.strategic();
    service.advance(round);
    service.advance(round);
    service.advance(round);
    assertEquals(List.of(AWARD, new Bid(20, Trump.SPADE), new Bid(20, Trump.HEART),
        new Bid(20, Trump.NO_TRUMP)), round.contractHistory());
    assertEquals(CLUB_BID, round.currentView().context().auction());
    assertEquals(new CardFriend(card("SQ")), round.friendSelection().orElseThrow());
    service.playToEnd(round);
    assertTrue(round.result().isPresent());
  }

  private static View withAuction(View base) {
    var ctx = base.context();
    return new View(base.self(), base.privateInfo(), base.role(), base.contract(), base.friendSelection(),
        new Context(ctx.playOrder(), ctx.declarer(), ctx.friendStatus(), ctx.history(),
            List.of(base.contract()), CLUB_BID), base.trick());
  }

  @ParameterizedTest
  @CsvSource({"HEART, HK", "SPADE HEART, S10"})
  void friendReturnsFirstAvailablePublicSignal(String signals, String expected) {
    var trick = row(1, Trump.NO_TRUMP, p(0,"S3"), p(1,"SQ"), p(2,"S2"), p(3,"S4"), p(4,"S6"));
    var base = view(1, 0, Trump.NO_TRUMP, 20, "S10 S8 HK H2 D2 D4 D6 C2 C3", "",
        new CardFriend(card("SQ")), new RevealedFriend(new Seat(1)), List.of(trick), List.of());
    var contracts = new ArrayList<Bid>();
    for (String s : signals.split(" ")) {
      contracts.add(new Bid(20, Trump.valueOf(s)));
    }
    contracts.add(base.contract());
    var ctx = base.context();
    var v = new View(base.self(), base.privateInfo(), base.role(), base.contract(), base.friendSelection(),
        new Context(ctx.playOrder(), ctx.declarer(), ctx.friendStatus(), ctx.history(), contracts), List.of());
    assertEquals(card(expected), new RulePlayPolicy().choose(v).card());
  }
}
