package mighty.ai.planning.distribution;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import mighty.data.CardParser;
import mighty.game.Seat;
import mighty.game.deal.DeckFactory;
import mighty.game.friend.CardFriend;
import mighty.model.*;
import org.junit.jupiter.api.Test;

class ConditionalDealTest {

  @Test
  void consumedMightyIsNotAvailableForAnotherSupportRound() {
    var own = CardParser.parseList("D3 D4 D5 D6 D9 DQ DK CA JK H3");
    var buried = CardParser.parseList("S4 S8 C10");
    var mighty = CardParser.parse("SA");
    for (int seed = 0; seed < 32; seed++) {
      var before = ConditionalDeal.sampleKnownBuried(own, buried, new CardFriend(mighty),
          new Random(seed));
      var after = before.afterOpening((StandardCard) CardParser.parse("D3"));
      assertFalse(after.hands().get(new Seat(after.friendIndex())).contains(mighty));
      assertEquals(ControlRoute.pointsLost(after, CardParser.parse("DA")),
          ControlRoute.pointsLost(after, mighty));
    }
  }

  @Test
  void specifiedFriendDoesNotChangeWhenAnotherSeatWinsOpening() {
    var own = CardParser.parseList("S7 S8 D2 DK C3 C4 CQ CK CA JK");
    var buried = CardParser.parseList("H3 H4 HQ");
    int checked = 0;
    for (int seed = 0; seed < 32; seed++) {
      var deal = ConditionalDeal.sampleKnownBuried(own, buried,
          new CardFriend(CardParser.parse("DA")), new Random(seed));
      if (!deal.hands().get(new Seat(deal.friendIndex())).contains(CardParser.parse("SA"))) {
        assertEquals(deal.friendIndex(),
            deal.afterOpening((StandardCard) CardParser.parse("D2")).friendIndex());
        checked++;
      }
    }
    assertTrue(checked > 0);
  }

  @Test
  void knownBuriedCandidatesShareTheSamePhysicalDeals() {
    var own = CardParser.parseList("S7 SA D2 DK C3 C4 CQ CK CA JK");
    var buried = CardParser.parseList("H3 H4 HQ");
    for (int seed = 0; seed < 32; seed++) {
      var first = ConditionalDeal.sampleKnownBuried(own, buried,
          mighty.game.friend.SpecialFriend.FIRST_TRICK, new Random(seed));
      var specified = ConditionalDeal.sampleKnownBuried(own, buried,
          new CardFriend(CardParser.parse("DA")), new Random(seed));
      assertEquals(first.hands(), specified.hands());
      assertEquals(first.buried(), specified.buried());
      assertTrue(specified.hands().get(new Seat(specified.friendIndex()))
          .contains(CardParser.parse("DA")));
      var after = specified.afterOpening((mighty.model.StandardCard) CardParser.parse("D2"));
      assertFalse(after.hands().get(new Seat(0)).contains(CardParser.parse("D2")));
      assertFalse(
          after.hands().get(new Seat(specified.friendIndex())).contains(CardParser.parse("DA")));
    }
  }

  @Test
  void conditionsFriendAndKeepsEveryCardUnique() {
    var own = CardParser.parseList("S4 SJ SQ SK C3 C4 C8 C9 CK CA");
    var buried = CardParser.parseList("D2 D3 D4");
    for (int index = 0; index < 20; index++) {
      var deal = ConditionalDeal.sample(own, buried, new CardFriend(CardParser.parse("SA")), index,
          new Random(index));
      assertEquals(10, deal.hands().get(new Seat(deal.friendIndex())).size());
      assertTrue(deal.hands().get(new Seat(deal.friendIndex())).contains(CardParser.parse("SA")));
      List<Card> all = new ArrayList<>(deal.buried());
      deal.hands().values().forEach(all::addAll);
      assertEquals(53, new HashSet<>(all).size());
      assertEquals(own, deal.hands().get(new Seat(0)));
      assertEquals(buried, deal.buried());
    }
  }

  @Test
  void friendHoldingBlockersChangesThemIntoCooperativeWins() {
    Map<Seat, List<Card>> hands = new LinkedHashMap<>();
    hands.put(new Seat(0), new ArrayList<>(CardParser.parseList("S4 SJ SQ SK C3 C4 C8 C9 CK CA")));
    hands.put(new Seat(1), new ArrayList<>(CardParser.parseList("SA CQ CJ C10 C2 C5 C6 C7 H2 H3")));
    hands.put(new Seat(2), new ArrayList<>(CardParser.parseList("S2 S3 S5")));
    hands.put(new Seat(3), new ArrayList<>(CardParser.parseList("S6 S7 S8")));
    hands.put(new Seat(4), new ArrayList<>(CardParser.parseList("S9 S10")));
    var buried = CardParser.parseList("D2 D3 D4");
    Set<Card> used = new HashSet<>(buried);
    hands.values().forEach(used::addAll);
    var pool = DeckFactory.create().stream().filter(c -> !used.contains(c)).iterator();
    for (int seat = 2; seat < 5; seat++) {
      while (hands.get(new Seat(seat)).size() < 10) {
        hands.get(new Seat(seat)).add(pool.next());
      }
    }
    int cooperative = ControlRoute.pointsLost(new ConditionalDeal(hands, buried, 1),
        CardParser.parse("SA"));
    var friend = hands.get(new Seat(1));
    var enemy = hands.get(new Seat(2));
    for (int index = 0; index < 7; index++) {
      Card guard = friend.get(1 + index);
      friend.set(1 + index, enemy.get(3 + index));
      enemy.set(3 + index, guard);
    }
    int hostile = ControlRoute.pointsLost(new ConditionalDeal(hands, buried, 1),
        CardParser.parse("SA"));
    assertEquals(0, cooperative);
    assertTrue(hostile > cooperative);
  }

}
