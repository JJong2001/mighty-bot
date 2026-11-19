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
