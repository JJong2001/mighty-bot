package mighty.ai.play;

import static mighty.ai.play.PlayTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Set;
import mighty.game.Seat;
import mighty.game.friend.*;
import mighty.game.observation.View;
import mighty.model.*;
import org.junit.jupiter.api.Test;

class ConfirmedLeadSignalTest {
  private View opening(String lead, Trump trump, Card friend) {
    return view(1, 0, trump, 15, "H2 H3 D2 D3 C2 C4 S2 S4 S5 S6", "",
        new CardFriend(friend), UnrevealedFriend.INSTANCE, List.of(), List.of(p(0, lead)));
  }

  @Test void gunAndOrdinaryLeadDistinguishJokerLocation() {
    for (String lead : List.of("C3", "C5")) {
      var v = opening(lead, Trump.NO_TRUMP, card("DA"));
      var facts = new PlayFacts(v);
      var locations = facts.locations().possibleHolders(Joker.INSTANCE);
      assertEquals(lead.equals("C3"), locations.equals(Set.of(new Seat(0))));
      assertEquals(locations, new CardLocationInference(v, facts.cards()).possibleHolders(Joker.INSTANCE));
      assertEquals(facts.joker().relativeWeights(),
          new JokerBelief(v, facts.teams(), facts.behavior()).relativeWeights());
    }
    assertEquals(Set.of(new Seat(0)), new PlayFacts(opening("S3", Trump.CLUB, card("DA")))
        .locations().possibleHolders(Joker.INSTANCE));
  }

  @Test void jokerFriendKingConfirmsOrdinaryOrMightyAceButQueenOnlyHints() {
    for (String suit : List.of("H", "S")) {
      for (String rank : List.of("K", "Q")) {
        var v = opening(suit + rank, Trump.NO_TRUMP, Joker.INSTANCE);
        var facts = new PlayFacts(v);
        assertEquals(rank.equals("K"), facts.locations().possibleHolders(card(suit + "A"))
            .equals(Set.of(new Seat(0))));
        assertEquals(rank.equals("K"), facts.locations().behavioralWeight(new Seat(4),
            card(suit + "A")) == 0);
      }
    }
  }

  @Test void secondTrickGunSecuresPartnersAceAgainstJokerBehind() {
    var history = List.of(row(1, Trump.NO_TRUMP, p(0, "SA"), p(1, "S7"),
        p(2, "S8"), p(3, "S9"), p(4, "S10")));
    for (String lead : List.of("C3", "C5")) {
      var v = view(2, 0, Trump.NO_TRUMP, 15, "H2 H3 D2 D3 C2 C4 S2 S4 S5", "",
          new CardFriend(card("DA")), UnrevealedFriend.INSTANCE, history,
          List.of(p(0, lead), p(1, "CA")));
      var facts = new PlayFacts(v);
      assertEquals(lead.equals("C3"), facts.isSecured(facts.currentWinner().orElseThrow()));
    }
  }

  @Test void kingIsSecureWhenAceIsAtDeclarerAndObserverHoldsMighty() {
    for (var friend : List.of(Joker.INSTANCE, card("DA"))) {
      var v = view(1, 0, Trump.NO_TRUMP, 15, "SA H2 H3 D2 D3 C2 C4 S2 S4 S5", "",
          new CardFriend(friend), UnrevealedFriend.INSTANCE, List.of(), List.of(p(0, "HK")));
      var facts = new PlayFacts(v);
      assertEquals(friend instanceof Joker, facts.isSecured(facts.currentWinner().orElseThrow()));
    }
  }
}
