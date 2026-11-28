package mighty.ai.play;

import static mighty.ai.play.PlayTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import mighty.game.Seat;
import mighty.game.friend.*;
import mighty.model.*;
import org.junit.jupiter.api.Test;

class InferenceStrengtheningTest {
  @Test void missedLastSeatCaptureReducesHonorWeightWithoutExcludingIt() {
    var history=List.of(row(1,Trump.NO_TRUMP,p(0,"HJ"),p(1,"H10"),p(2,"H2"),p(3,"H3"),p(4,"H4")));
    var v=view(2,0,Trump.NO_TRUMP,15,"D2 D3 D4 D5 C4 C5 H5 H6 S2","",
        SpecialFriend.NONE,NoFriend.INSTANCE,history,List.of(p(0,"CA"),p(1,"C2")));
    var facts=new PlayFacts(v);
    assertEquals(1,facts.behavior().missedCaptures(new Seat(4),(StandardCard)card("HK")));
    assertEquals(0,facts.behavior().missedCaptures(new Seat(1),(StandardCard)card("HK")));
    assertTrue(facts.locations().possibleHolders(card("HK")).contains(new Seat(4)));
    assertFalse(facts.locations().supportedHolder(new Seat(4),card("HK")));
    assertTrue(facts.locations().supportedHolder(new Seat(1),card("HK")));
  }

  @Test void friendDoesNotHaveToOvertakeDeclarer() {
    var history=List.of(row(1,Trump.NO_TRUMP,p(0,"HJ"),p(1,"H10"),p(2,"H2"),p(3,"H3"),p(4,"H4")));
    var v=view(2,0,Trump.NO_TRUMP,15,"D2 D3 D4 D5 C4 C5 H5 H6 S2","",
        new CardFriend(Joker.INSTANCE),new RevealedFriend(new Seat(4)),history,List.of(p(0,"CA"),p(1,"C2")));
    assertEquals(0,new PlayFacts(v).behavior().missedCaptures(new Seat(4),(StandardCard)card("HK")));
  }

  private mighty.game.observation.View concentrated(boolean split) {
    var history=List.of(
        row(1,Trump.SPADE,p(0,"SA"),p(1,"S2"),p(2,split?"S4":"D2"),p(3,"D3"),p(4,"D4")),
        row(2,Trump.SPADE,p(0,"SK"),p(1,"S3"),p(2,split?"S5":"D5"),p(3,"D6"),p(4,"D7")));
    return view(1,0,Trump.SPADE,16,"H2 H3 H4 H5 H6 H7 H8 H9","",
        SpecialFriend.NONE,NoFriend.INSTANCE,history,List.of(p(0,"C2")));
  }

  @Test void concentratedTrumpRecognizesLateSideWithSevenCardsStillHeld() {
    var v=concentrated(false);
    var facts=new PlayFacts(v);
    assertEquals(new CardLocationInference.CountRange(6,7),facts.locations().countRange(new Seat(0),Suit.SPADE));
    assertTrue(facts.cards().trumps().likelyLastSide(new Seat(0),facts));
    assertTrue(facts.cards().trumps().expectedCount(new Seat(0),facts)>=6);
  }

  @Test void splitTrumpDoesNotClaimSameConcentration() {
    var v=concentrated(true);
    var facts=new PlayFacts(v);
    assertEquals(0,facts.locations().countRange(new Seat(0),Suit.SPADE).minimum());
    assertFalse(facts.cards().trumps().likelyLastSide(new Seat(0),facts));
  }
}
