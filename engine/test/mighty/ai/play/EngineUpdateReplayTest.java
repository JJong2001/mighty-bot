package mighty.ai.play;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import mighty.ai.evaluation.PreparedRoundEvaluator;
import mighty.data.CardParser;
import mighty.game.*;
import mighty.game.bidding.Bid;
import mighty.game.deal.DeckFactory;
import mighty.game.friend.CardFriend;
import mighty.game.round.RoundSettlement;
import mighty.game.trick.PublicPlay;
import mighty.model.*;
import org.junit.jupiter.api.Test;

/** 동일 배분을 유지하고 야당/프렌드 좌석만 순환하는 소규모 전후 비교. */
class EngineUpdateReplayTest {
  @Test
  void pairedTumbleDealsAcrossPartnerPositions() {
    var own = CardParser.parseList("S4 SA D6 DQ DK C6 CK H3 H7 JK");
    var kitty = CardParser.parseList("SK D3 H2");
    var bid = new Bid(15, Trump.NO_TRUMP);
    for (int seed : List.of(0, 1, 6, 7)) {
      var pool = new ArrayList<>(DeckFactory.create());
      pool.removeAll(own);
      pool.removeAll(kitty);
      Collections.shuffle(pool, new Random(seed));
      for (int rotation = 0; rotation < 4; rotation++) {
        var rotated = new ArrayList<>(pool);
        Collections.rotate(rotated, rotation * 10);
        var round = PreparedRoundEvaluator.createRound(own, bid, kitty, rotated);
        round.takeKitty(new Seat(0));
        round.discard(new Seat(0), CardParser.parseList("H2 H3 H7"));
        round.chooseFriend(new Seat(0), new CardFriend(CardParser.parse("DA")));
        var policy = new RulePlayPolicy();
        int turns = 0;
        var trace = new ArrayList<String>();
        while (round.phase() == GamePhase.PLAY) {
          var view = round.currentView();
          var action = policy.choose(view);
          assertTrue(new PlayFacts(view).legalActions().contains(action));
          trace.add(view.self().index() + ":" + action);
          round.play(new PublicPlay(view.self(), action));
          assertTrue(++turns <= 50);
        }
        assertEquals(50, turns);
        var result = round.result().orElseThrow();
        int nominal = RoundSettlement.nominal(result, false).get(new Seat(0));
        // 잔액 정보가 없는 비교이므로 실제 칩 정산을 명목 정산으로 가장하지 않습니다.
        System.out.println("PAIR seed=" + seed + " rotation=" + rotation
            + " friend=" + result.friend().orElseThrow().index()
            + " points=" + result.teamPoints() + " nominal=" + nominal
            + " success=" + result.succeeded() + " trace=" + trace.hashCode());
      }
    }
  }
}
