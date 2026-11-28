package mighty.ai.play;

import static mighty.ai.play.PlayTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import mighty.game.Seat;
import mighty.game.friend.*;
import mighty.model.*;
import org.junit.jupiter.api.Test;

class CardLocationInferenceTest {
  @Test void openingHonorsSignalUpperRunWithIndependentFriend() {
    for (var trump : List.of(Trump.NO_TRUMP, Trump.CLUB)) {
      for (var friend : List.of(Joker.INSTANCE,card("DA"))) {
        for (String lead : List.of("HJ","HQ","HK")) {
          var v=view(1,0,trump,15,"H2 H3 D2 D3 C2 C4 S2 S3 S4 S5","",
              new CardFriend(friend),UnrevealedFriend.INSTANCE,List.of(),List.of(p(0,lead)));
          var facts=new PlayFacts(v);
          int rank=((StandardCard)card(lead)).rank().value();
          for (String upper:List.of("HQ","HK","HA")) {
            if (((StandardCard)card(upper)).rank().value()>rank)
              assertEquals(java.util.Set.of(new Seat(0)),facts.locations().suggestedHolders(card(upper)));
          }
        }
      }
    }
  }

  @Test void mightyAndSameSuitAceDoNotSignalUpperRun() {
    for (var friend:List.of(card("SA"),card("HA"))) {
      var v=view(1,0,Trump.NO_TRUMP,15,"H2 H3 D2 D3 C2 C4 S2 S3 S4 S5","",
          new CardFriend(friend),UnrevealedFriend.INSTANCE,List.of(),List.of(p(0,"HJ")));
      for (String upper:List.of("HQ","HK","HA"))
        assertTrue(new PlayFacts(v).locations().suggestedHolders(card(upper)).isEmpty());
    }
  }

  @Test void jokerFriendOpeningKingConfirmsAceLocation() {
    var v = view(1,0,Trump.NO_TRUMP,15,"H2 H3 D2 D3 C2 C4 S2 S3 S4 S5","",
        new CardFriend(Joker.INSTANCE),UnrevealedFriend.INSTANCE,List.of(),List.of(p(0,"HK")));
    var facts = new PlayFacts(v);
    assertEquals(java.util.Set.of(new Seat(0)),facts.locations().suggestedHolders(card("HA")));
    assertFalse(facts.locations().possibleHolders(card("HA")).contains(new Seat(4)));
  }

  @Test void ownAceOverridesOpeningSignal() {
    var v = view(1,0,Trump.NO_TRUMP,15,"HA H3 D2 D3 C2 C4 S2 S3 S4 S5","",
        new CardFriend(Joker.INSTANCE),UnrevealedFriend.INSTANCE,List.of(),List.of(p(0,"HK")));
    assertTrue(new PlayFacts(v).locations().suggestedHolders(card("HA")).isEmpty());
  }

  @Test void gunLeadAssignsJokerToDeclarerForStrategicDecisions() {
    var v = view(1,0,Trump.NO_TRUMP,15,"HA H3 D2 D3 C2 C4 S2 S3 S4 S5","",
        new CardFriend(card("DA")),UnrevealedFriend.INSTANCE,List.of(),List.of(p(0,"C3")));
    var facts = new PlayFacts(v);
    assertEquals(java.util.Map.of(new Seat(0), 1.0),facts.joker().relativeWeights());
    assertFalse(facts.locations().possibleHolders(Joker.INSTANCE).contains(new Seat(4)));
  }

  @Test void spadeVoidBehindCanStillOvertakeWithMighty() {
    var history = List.of(row(1, Trump.NO_TRUMP, p(0,"S5"), p(1,"H2"),
        p(2,"SK"), p(3,"S3"), p(4,"S4")));
    var v = view(0,0,Trump.NO_TRUMP,15,"H3 H4 H5 C2 C3 C4 D2 D3 D4","D5 D6 D7",
        new CardFriend(card("SA")), new RevealedFriend(new Seat(1)),history,
        List.of(new mighty.game.trick.PublicPlay(new Seat(2),
            new mighty.game.action.JokerLead(Suit.HEART)),p(3,"H6"),p(4,"H7")));
    var facts = new PlayFacts(v);
    assertFalse(facts.isSecured(facts.currentWinner().orElseThrow()));
  }

  @Test void jokerFriendIsNotAttributedToDeclarer() {
    var v = view(1,0,Trump.NO_TRUMP,15,"D2 D3 D4 D5 C2 C3 H2 H3 S2 S3","",
        new CardFriend(Joker.INSTANCE),UnrevealedFriend.INSTANCE,List.of(),List.of(p(0,"DA")));
    assertFalse(new PlayFacts(v).joker().relativeWeights().containsKey(new Seat(0)));
  }

  @Test void ordinaryVoidDoesNotExcludeMighty() {
    var history = List.of(row(1, Trump.NO_TRUMP, p(0,"SK"), p(1,"H2"),
        p(2,"S2"), p(3,"S3"), p(4,"S4")));
    var v = view(0,0,Trump.NO_TRUMP,15,"HA H3 H4 H5 C2 C3 C4 D2 D3","D4 D5 D6",
        SpecialFriend.NONE,NoFriend.INSTANCE,history,List.of());
    var locations = new PlayFacts(v).locations();
    assertFalse(locations.possibleHolders(card("SQ")).contains(new Seat(1)));
    assertTrue(locations.possibleHolders(card("SA")).contains(new Seat(1)));
    assertTrue(locations.possibleHolders(card("D4")).isEmpty());
  }

  @Test void designatedCardCannotBelongToDeclarer() {
    var v = view(1,0,Trump.NO_TRUMP,15,"D2 D3 D4 D5 C2 C3 H2 H3 S2 S3","",
        new CardFriend(card("CA")),UnrevealedFriend.INSTANCE,List.of(),List.of(p(0,"DA")));
    var locations = new PlayFacts(v).locations();
    assertFalse(locations.possibleHolders(card("CA")).contains(new Seat(0)));
    assertTrue(locations.possibleHolders(card("CA")).contains(new Seat(4)));
    assertEquals(java.util.Set.of(new Seat(1)), locations.possibleHolders(card("D2")));
    assertTrue(locations.possibleHolders(card("DA")).isEmpty());
  }

  @Test void undisclosedBurialIsNotReadByOpposition() {
    var v = view(1,0,Trump.NO_TRUMP,15,"D2 D3 D4 D5 C2 C3 H2 H3 S2 S3","CK CQ CJ",
        SpecialFriend.NONE,NoFriend.INSTANCE,List.of(),List.of(p(0,"DA")));
    assertFalse(new PlayFacts(v).locations().possibleHolders(card("CK")).isEmpty());
  }

  @Test void noRemainingPointThreatAfterKingWhenAllOtherPointsKnown() {
    var history = List.of(row(1,Trump.NO_TRUMP,p(0,"DQ"),p(1,"D10"),p(2,"DJ"),p(3,"D2"),p(4,"D3")));
    var v = view(1,0,Trump.NO_TRUMP,15,"DA D4 C2 C3 C4 H2 H3 S2 S3","",
        new CardFriend(card("CA")),new RevealedFriend(new Seat(4)),history,List.of(p(0,"DK")));
    assertTrue(new PlayFacts(v).locations().possiblePointCards(new Seat(0),Suit.DIAMOND).isEmpty());
    assertEquals(card("DA"),new RulePlayPolicy().choose(v).card());
  }
}
