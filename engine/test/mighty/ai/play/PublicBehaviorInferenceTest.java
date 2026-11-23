package mighty.ai.play;

import static mighty.ai.play.PlayTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import mighty.data.CardParser;
import mighty.game.*;
import mighty.game.friend.*;
import mighty.game.trick.PublicTrick;
import mighty.model.*;
import org.junit.jupiter.api.Test;

class PublicBehaviorInferenceTest {

  private final RulePlayPolicy policy = new RulePlayPolicy();

  @Test
  void ordinaryLastSeatSupportAndTopContinuationIdentifyCandidateWithoutRuff() {
    var history = List.of(
        row(1, Trump.NO_TRUMP, p(0, "SQ"), p(1, "S2"), p(2, "S3"), p(3, "S4"), p(4, "S5")),
        row(2, Trump.NO_TRUMP, p(0, "H2"), p(1, "H3"), p(2, "H4"), p(3, "H5"), p(4, "HJ")));
    var current = List.of(p(4, "CA"));
    var first = view(0, 0, Trump.NO_TRUMP, 20, "SA HA HK HQ H6 H7 D2 D3", "C2 C3 C4",
        new CardFriend(Joker.INSTANCE), UnrevealedFriend.INSTANCE, history, current);
    var otherPrivate = view(0, 0, Trump.NO_TRUMP, 20, "SA HA HK HQ H6 H7 D5 D6", "C6 C7 C8",
        new CardFriend(Joker.INSTANCE), UnrevealedFriend.INSTANCE, history, current);
    var facts = new PlayFacts(first);
    var alternative = new PlayFacts(otherPrivate);
    assertEquals(Set.of(new Seat(4)), facts.behavior().likelyFriends());
    assertEquals(facts.behavior().supportEvidence(), alternative.behavior().supportEvidence());
    assertEquals(facts.behavior().likelyFriends(), alternative.behavior().likelyFriends());
    assertFalse(facts.teams().isAlly(new Seat(4)));
    assertTrue(facts.teams().isLikelyAlly(new Seat(4)));
    assertNotEquals(card("SA"), policy.choose(first).card());
  }

  @Test
  void attackingDeclarersOpeningTopContradictsEarlyTrumpSignal() {
    var history = List.of(
        row(1, Trump.DIAMOND, p(0, "HA"), p(1, "H2"), p(2, "D2"), p(3, "H3"), p(4, "H4")));
    var observation = view(3, 0, Trump.DIAMOND, 16, "C2 C3 C4 C5 C6 C7 C8 C9 C10", "",
        new CardFriend(Joker.INSTANCE), UnrevealedFriend.INSTANCE, history, List.of(p(2, "D4")));
    assertFalse(new PlayFacts(observation).behavior().likelyFriends().contains(new Seat(2)));
  }

  private List<PublicTrick> missedDeclarerCapture() {
    return List.of(
        row(1, Trump.HEART, p(0, "SA"), p(1, "CA"), p(2, "C2"), p(3, "C3"), p(4, "C4")),
        row(2, Trump.HEART, p(0, "C5"), p(1, "CK"), p(2, "C7"), p(3, "C8"), p(4, "C9")),
        row(3, Trump.HEART, p(1, "H2"), p(2, "HA"), p(3, "H9"), p(4, "H10"), p(0, "H6")));
  }

  @Test
  void missedCaptureIsPublicAndOwnJokerOverridesTheRelativeLocation() {
    var history = missedDeclarerCapture();
    var first = view(2, 0, Trump.HEART, 16, "D2 D3 D4 D5 S2 S3 HQ", "",
        new CardFriend(card("CA")), UnrevealedFriend.INSTANCE, history, List.of());
    var ownJoker = view(2, 0, Trump.HEART, 16, "D2 D3 D4 D5 JK S3 HQ", "",
        new CardFriend(card("CA")), UnrevealedFriend.INSTANCE, history, List.of());
    var facts = new PlayFacts(first);
    var privateFacts = new PlayFacts(ownJoker);
    assertTrue(facts.behavior().missedJokerCaptures().getOrDefault(new Seat(0), 0) > 0);
    assertEquals(facts.behavior().missedJokerCaptures(),
        privateFacts.behavior().missedJokerCaptures());
    assertTrue(facts.joker().relativeWeights().get(new Seat(0)) < facts.joker().relativeWeights()
        .get(new Seat(3)));
    assertEquals(Map.of(new Seat(2), 1.0), privateFacts.joker().relativeWeights());
    assertTrue(facts.joker().likelyHeldByOpposition());
  }

  private List<PublicTrick> afterRecovery() {
    var history = new ArrayList<>(missedDeclarerCapture());
    history.add(row(4, Trump.HEART, p(2, "D2"), p(3, "D3"), p(4, "D4"), p(0, "DA"), p(1, "D5")));
    return history;
  }

  @Test
  void friendLimitsEarlySupportWhenOppositionCanTurnItIntoFourPoints() {
    var observation = view(1, 0, Trump.HEART, 16, "DJ DQ CJ C6 S4 S5", "",
        new CardFriend(card("CA")), UnrevealedFriend.INSTANCE, afterRecovery(),
        List.of(p(0, "HK")));
    var facts = new PlayFacts(observation);
    assertEquals(Role.FRIEND, observation.role());
    assertTrue(facts.joker().likelyHeldByOpposition());
    assertFalse(PointExposurePlan.allows(observation, facts, p(1, "DJ").action()));
    assertEquals(0, policy.choose(observation).card().points());
    var forced = view(1, 0, Trump.HEART, 16, "DJ DQ DK CJ C10 CQ", "",
        new CardFriend(card("CA")), UnrevealedFriend.INSTANCE, afterRecovery(),
        List.of(p(0, "HK")));
    assertTrue(PointExposurePlan.allows(forced, new PlayFacts(forced), p(1, "DJ").action()));
  }

  @Test
  void oppositionFeedsWhenPublicEvidencePlacesJokerBehindIt() {
    var observation = view(2, 0, Trump.HEART, 16, "D10 DJ DQ S2 S3 S4", "",
        new CardFriend(card("CA")), UnrevealedFriend.INSTANCE, afterRecovery(),
        List.of(p(0, "HK"), p(1, "H3")));
    assertTrue(new PlayFacts(observation).joker().likelyHeldByOpposition());
    assertTrue(policy.choose(observation).card().points() > 0);
  }

  @Test
  void oppositionFeedsAgainstFriendOnlyWhenAnInferredJokerHolderIsBehind() {
    var history = List.of(
        row(1, Trump.HEART, p(0, "SA"), p(1, "S7"), p(2, "S8"), p(3, "S9"), p(4, "SK")),
        row(2, Trump.HEART, p(0, "C5"), p(1, "CA"), p(2, "C7"), p(3, "C8"), p(4, "C9")),
        row(3, Trump.HEART, p(1, "H2"), p(2, "HA"), p(3, "H9"), p(4, "H10"), p(0, "H6")),
        row(4, Trump.HEART, p(2, "D2"), p(3, "D3"), p(4, "D4"), p(0, "D5"), p(1, "DA")));
    var friend = new Seat(1);
    var selection = new CardFriend(card("CA"));
    var revealed = new RevealedFriend(friend);
    String own = "D10 DJ DQ S2 S3 S4";

    var early = view(2, 0, Trump.HEART, 16, own, "", selection, revealed, history, List.of(p(1, "HK")));
    var facts = new PlayFacts(early);
    assertEquals(friend, facts.currentWinner().orElseThrow().seat());
    assertTrue(facts.joker().likelyAt(Set.of(new Seat(3), new Seat(4))));
    assertEquals(card("D10"), policy.choose(early).card());
    assertTrue(policy.recommend(early).reason().contains("뒤 야당이 조커로"));

    var late = view(4, 0, Trump.HEART, 16, own, "", selection, revealed, history,
        List.of(p(1, "HK"), p(2, "S5"), p(3, "S6")));
    var lateFacts = new PlayFacts(late);
    assertTrue(lateFacts.joker().likelyHeldByOpposition());
    assertEquals(List.of(new Seat(0)), lateFacts.remainingSeats());
    assertEquals(0, policy.choose(late).card().points());
  }

  @Test
  void operatingSideTopSurvivesWhileDeclarerStillHasSideCardsToPlay() {
    String owned = "CA D2 D3 H2 H3";
    var current = List.of(p(0, "SA"));
    var last = row(5, Trump.SPADE, p(0, "C7"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var history = countedHistory(owned, "", "DA JK S2 S3 S4 S5 S6 S7 S8 S9 S10 SJ CK CQ CJ C10",
        "SK SQ", current, last);
    var observation = view(1, 0, Trump.SPADE, 16, owned, "", new CardFriend(card("C2")),
        new RevealedFriend(new Seat(1)), history, current);
    var facts = new PlayFacts(observation);
    assertTrue(facts.declarerSuits().assess(Suit.CLUB).operating());
    assertEquals(2, facts.cards().trumps().remainingCount());
    assertNotEquals(card("CA"), policy.choose(observation).card());
  }

  @Test
  void buryingTiePreservesVisibleMasterRegardlessOfInputOrder() {
    var owned = CardParser.parseList("S3 S4 SJ DK DA H3 H6 H7 H10 HJ C6 CK S6");
    var reversed = new ArrayList<>(owned);
    Collections.reverse(reversed);
    var policy = new mighty.ai.preparation.RuleDiscardPolicy();
    for (var input : List.of(owned, reversed)) {
      var result = policy.choose(new mighty.game.observation.DiscardView(new Seat(0),
          new mighty.game.bidding.Bid(16, Trump.HEART), input));
      assertFalse(result.contains(card("DA")));
    }
  }
}
