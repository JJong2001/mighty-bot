package mighty.ai.play;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import mighty.ai.evaluation.PreparedRoundEvaluator;
import mighty.data.CardParser;
import mighty.game.*;
import mighty.game.bidding.Bid;
import mighty.game.deal.DeckFactory;
import mighty.game.friend.CardFriend;
import mighty.game.trick.PublicPlay;
import mighty.model.*;
import org.junit.jupiter.api.Test;

class InferenceRoundLegalityTest {
  @Test void completesSuitedAndNoTrumpRoundsUsingOnlyCurrentView() {
    var own=CardParser.parseList("C2 C4 CK CA S2 S4 D2 D3 H2 H3");
    var kitty=CardParser.parseList("C5 D4 H4");
    for (var trump: List.of(Trump.SPADE,Trump.CLUB,Trump.NO_TRUMP)) {
      for (int seed:List.of(0,1)) {
        var pool=new ArrayList<>(DeckFactory.create());
        pool.removeAll(own); pool.removeAll(kitty);
        Collections.shuffle(pool,new Random(seed));
        var round=PreparedRoundEvaluator.createRound(own,new Bid(16,trump),kitty,pool);
        round.takeKitty(new Seat(0));
        round.discard(new Seat(0),CardParser.parseList("D2 D3 H2"));
        round.chooseFriend(new Seat(0),new CardFriend(Joker.INSTANCE));
        var policy=new RulePlayPolicy();
        int turns=0;
        while (round.phase()==GamePhase.PLAY) {
          var view=round.currentView();
          var action=policy.choose(view);
          assertTrue(new PlayFacts(view).legalActions().contains(action));
          round.play(new PublicPlay(view.self(),action));
          assertTrue(++turns<=50);
        }
        assertEquals(50,turns);
        assertTrue(round.result().isPresent());
      }
    }
  }
}
