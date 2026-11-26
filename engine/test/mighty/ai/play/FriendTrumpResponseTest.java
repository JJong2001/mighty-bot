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

class FriendTrumpResponseTest {

  private static List<PublicTrick> concentratedRuns() {
    return List.of(
        row(1, Trump.HEART, p(0, "HA"), p(1, "H2"), p(2, "D2"), p(3, "H4"), p(4, "C8")),
        row(2, Trump.HEART, p(0, "HK"), p(1, "H5"), p(2, "D3"), p(3, "H6"), p(4, "C9")));
  }

  @Test
  void hidesCoveredMightyEvenWithTrumpHolderBehind() {
    var v = view(2, 0, Trump.HEART, 15, "SA S4 D4 D5 C4 C5 C6 C7", "",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, concentratedRuns(),
        List.of(p(0, "H3"), p(1, "H9")));
    var decision = new RulePlayPolicy().recommend(v);
    assertNotEquals(card("SA"), decision.action().card());
    assertNotEquals(card("S4"), decision.action().card());
    assertTrue(decision.reason().contains("집중된 기루"));
  }

  @Test
  void canReadConcentrationOnTheSecondTrumpRound() {
    var v = view(2, 0, Trump.HEART, 15, "SA S4 D3 D4 D5 C4 C5 C6 C7", "",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE,
        List.of(concentratedRuns().get(0)), List.of(p(0, "H3"), p(1, "H9")));
    assertTrue(new RulePlayPolicy().recommend(v).reason().contains("집중된 기루"));
  }

  @Test
  void singletonMightyCannotHide() {
    var v = view(2, 0, Trump.HEART, 15, "SA D6 D4 D5 C4 C5 C6 C7", "",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, concentratedRuns(),
        List.of(p(0, "H3"), p(1, "H9")));
    assertEquals(card("SA"), new RulePlayPolicy().choose(v).card());
  }

  @Test
  void jokerRequiresPublicCallSafety() {
    for (boolean safe : List.of(false, true)) {
      var v = view(2, 0, Trump.HEART, 15, "JK S4 D4 D5 C4 C5 C6 " + (safe ? "C3" : "C7"), "",
          new CardFriend(Joker.INSTANCE), UnrevealedFriend.INSTANCE, concentratedRuns(),
          List.of(p(0, "H3"), p(1, "H9")));
      assertEquals(!safe, new RulePlayPolicy().choose(v).card() instanceof Joker);
    }
    var history = List.of(
        row(1, Trump.HEART, p(0, "HA"), p(1, "H2"), p(2, "D2"), p(3, "H4"), p(4, "C3")),
        concentratedRuns().get(1));
    var spent = view(2, 0, Trump.HEART, 15, "JK S4 D4 D5 C4 C5 C6 C7", "",
        new CardFriend(Joker.INSTANCE), UnrevealedFriend.INSTANCE, history,
        List.of(p(0, "H3"), p(1, "H9")));
    assertFalse(new RulePlayPolicy().choose(spent).card() instanceof Joker);
  }

  @Test
  void loadedThreePointsTakePriorityOverHiding() {
    var v = view(4, 0, Trump.HEART, 15, "SA S4 D4 D5 C4 C5 C6 C7", "",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, concentratedRuns(),
        List.of(p(0, "H3"), p(1, "H10"), p(2, "DQ"), p(3, "HQ")));
    assertEquals(card("SA"), new RulePlayPolicy().choose(v).card());
  }

  @Test
  void aShrinkingDistributionBehindStillNeedsResponseToUnextractedJack() {
    var history = List.of(
        row(1, Trump.HEART, p(0, "HA"), p(1, "D2"), p(2, "H2"), p(3, "H4"), p(4, "C8")),
        row(2, Trump.HEART, p(0, "HK"), p(1, "D3"), p(2, "H5"), p(3, "H6"), p(4, "C9")),
        row(3, Trump.HEART, p(0, "HQ"), p(1, "D4"), p(2, "C2"), p(3, "H7"), p(4, "C10")));
    var v = view(1, 0, Trump.HEART, 15, "SA S4 D5 C4 C5 C6 C7", "",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, history, List.of(p(0, "H3")));
    var decision = new RulePlayPolicy().recommend(v);
    assertEquals(card("SA"), decision.action().card());
    assertTrue(decision.reason().contains("미소모 상위"));
  }

}
