package mighty.application.match;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import mighty.ApplicationInfo;
import mighty.data.Journal;
import mighty.game.GamePhase;
import mighty.game.bidding.BidOffer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MatchSessionTest {

  @TempDir
  Path temporary;

  @Test
  void currentUiSessionCompletesTwentyRoundsAndRecordsCurrentEngine() throws Exception {
    for (int seed = 0; seed < 20; seed++) {
      Journal journal = new Journal(temporary.resolve("round-" + seed));
      MatchSession match = new MatchSession(seed, journal);
      int steps = 0;
      while (match.phase() != GamePhase.FINISHED) {
        assertEquals(match.handSize(new mighty.game.Seat(0)), match.humanHand().size());
        match.advanceAi();
        assertTrue(++steps < 200);
      }
      assertEquals(10, match.history().size() + match.settingCollectedCards() / 5);
      assertTrue(match.humanHand().isEmpty());
      var result = match.result().orElseThrow();
      assertEquals(20, result.teamPoints() + result.oppositionPoints());
      var reviews = journal.reviews();
      assertEquals(match.history().size() * 5L,
          reviews.stream().filter(review -> review.stage().startsWith("PLAY")).count());
      assertTrue(
          reviews.stream().allMatch(review -> review.version().equals(ApplicationInfo.VERSION)));
      assertFalse(reviews.stream().anyMatch(Journal.Review::publicOnly));
    }
  }

  @Test
  void humanBidUsesCurrentRulesAndIsRecordedAsHuman() throws Exception {
    Journal journal = new Journal(temporary);
    MatchSession match = new MatchSession(42, journal);
    while (match.currentSeat().orElseThrow().index() != 0) {
      match.advanceAi();
    }
    var offer = match.humanBids().stream().filter(action -> action instanceof BidOffer).findFirst()
        .orElseThrow();
    match.bid(offer);
    var human = journal.reviews().stream().filter(review -> review.seat() == 0).findFirst()
        .orElseThrow();
    assertTrue(human.reason().contains("human"));
    assertTrue(human.snapshot().containsKey("hand"));
  }

}
