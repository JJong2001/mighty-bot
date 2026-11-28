package mighty.ai.play;

import static mighty.ai.play.PlayTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import mighty.game.Seat;
import mighty.game.friend.*;
import mighty.model.*;
import org.junit.jupiter.api.Test;

class TrickContinuationTest {
  private mighty.game.observation.View ruffView(boolean singlePoint, int friend) {
    var history = List.of(row(1,Trump.SPADE,p(0,"CA"),p(1,"C2"),p(2,"C3"),p(3,"C4"),p(4,"D2")));
    return view(1,0,Trump.SPADE,16,
        singlePoint ? "D10 H5 D3 D4 H2 H3 H4 H6 H7" : "D10 DJ D3 D4 H2 H3 H4 H6 H7","",
        new CardFriend(Joker.INSTANCE),new RevealedFriend(new Seat(friend)),history,List.of(p(0,"CK")));
  }

  @Test void supportsSideTopWhenLastOpponentCanRuff() {
    var v = ruffView(false,3);
    var facts = new PlayFacts(v);
    var opportunity = new TrickContinuation(v,facts).alliedRuffBehind().orElseThrow();
    assertEquals(new Seat(4),opportunity.seat());
    assertTrue(opportunity.knownVoid());
    assertEquals(card("D10"),new RulePlayPolicy().choose(v).card());
  }

  @Test void doesNotGambleOnlyPointOnUnlocatedTrump() {
    var v = ruffView(true,3);
    assertNotEquals(card("D10"),new RulePlayPolicy().choose(v).card());
  }

  @Test void friendVoidDoesNotCreateOppositionRuffOpportunity() {
    var v = ruffView(false,4);
    assertTrue(new TrickContinuation(v,new PlayFacts(v)).alliedRuffBehind().isEmpty());
  }

  @Test void keepsQueenWhenAlliedAceKingAlreadyLoaded() {
    var v = view(3,0,Trump.NO_TRUMP,15,"HQ H4 H5 D2 D3 C2 C3 S2 S3 S4","",
        new CardFriend(Joker.INSTANCE),new RevealedFriend(new Seat(4)),List.of(),
        List.of(p(0,"H2"),p(1,"HA"),p(2,"HK")));
    assertTrue(new TrickContinuation(v,new PlayFacts(v)).spendsLastPointGuard((StandardCard)card("HQ")));
    assertNotEquals(card("HQ"),new RulePlayPolicy().choose(v).card());
  }

  @Test void followingGunDoesNotAssignJokerToDeclarer() {
    var history=List.of(row(1,Trump.NO_TRUMP,p(1,"CA"),p(2,"C2"),p(3,"C4"),p(4,"C5"),p(0,"C3")));
    var v=view(2,0,Trump.NO_TRUMP,15,"D2 D3 D4 H2 H3 H4 S2 S3 S4","",
        new CardFriend(card("DA")),UnrevealedFriend.INSTANCE,history,List.of(p(1,"CK")));
    assertTrue(new PlayFacts(v).locations().suggestedHolders(Joker.INSTANCE).isEmpty());
  }

  @Test void takesLoadedKingEvenWhenLaterPointThreatsRemain() {
    var v=view(2,0,Trump.NO_TRUMP,15,"HA H4 H5 D2 D3 C2 C3 S2 S3 S4","",
        new CardFriend(Joker.INSTANCE),new RevealedFriend(new Seat(4)),List.of(),
        List.of(p(0,"HK"),p(1,"HQ")));
    assertFalse(new PlayFacts(v).locations().possiblePointCards(new Seat(0),Suit.HEART).isEmpty());
    assertEquals(card("HA"),new RulePlayPolicy().choose(v).card());
  }

  @Test void publiclyPlayedJokerOverridesEarlierGunSignal() {
    var history=List.of(row(1,Trump.NO_TRUMP,p(0,"C3"),p(1,"JK"),p(2,"C4"),p(3,"C5"),p(4,"CA")));
    var v=view(0,0,Trump.NO_TRUMP,15,"D2 D3 D4 H3 H4 H5 S2 S3 S4","D5 D6 D7",
        new CardFriend(card("DA")),UnrevealedFriend.INSTANCE,history,List.of(p(4,"H2")));
    assertTrue(new PlayFacts(v).joker().relativeWeights().isEmpty());
  }
}
