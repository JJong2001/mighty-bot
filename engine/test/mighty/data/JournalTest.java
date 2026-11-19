package mighty.data;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import mighty.ApplicationInfo;
import mighty.game.Seat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class JournalTest {

  @TempDir
  Path temporary;

  @Test
  void ongoingOpponentOnlyExposesPublicBidding() throws Exception {
    Journal journal = new Journal(temporary);
    journal.gameEvent("game-a", "start", "test");
    journal.decision("game-a", new Seat(1), "BIDDING",
        Map.of("hand", "SA JK", "currentBid", "13:HEART", "privateReason", "hidden"),
        "14:SPADE", List.of("14:SPADE"), "hidden reasoning", "bot");
    journal.decision("game-a", new Seat(1), "DISCARD", Map.of("hand", "secret"), "secret",
        List.of(), "secret",
        "bot");
    var publicReview = journal.reviews();
    assertEquals(1, publicReview.size());
    assertTrue(publicReview.get(0).publicOnly());
    assertFalse(publicReview.get(0).snapshot().containsKey("hand"));
    assertFalse(publicReview.get(0).reason().contains("hidden reasoning"));
    assertTrue(publicReview.get(0).alternatives().isEmpty());
    journal.gameEvent("game-a", "result", "finished");
    assertEquals(2, journal.reviews().size());
    assertTrue(journal.reviews().stream().noneMatch(Journal.Review::publicOnly));
  }

  @Test
  void readsLegacyEvidenceWithoutOldEngineAndAppendsFeedback() throws Exception {
    List<Map<String, Object>> legacy = List.of(
        Map.of("type", "decision", "id", "old-id", "engineVersion", "0.6.0", "selected", "SA",
            "source", "human",
            "view", Map.of("hand", "SA H2", "trickNumber", 2), "ranking",
            List.of(Map.of("action", "SA", "reason",
                "당시 판단"))),
        Map.of("type", "game-play", "gameId", "old-game", "detail",
            "seat=0; decisionId=old-id; action=SA"),
        Map.of("type", "game-result", "gameId", "old-game", "detail", "finished"));
    String original = String.join("\n", legacy.stream().map(Json::write).toList()) + "\n";
    Path file = temporary.resolve("training.jsonl");
    Files.writeString(file, original, StandardCharsets.UTF_8);
    Journal journal = new Journal(temporary);
    var review = journal.reviews().get(0);
    assertEquals("0.6.0", review.version());
    assertEquals("old-game", review.gameId());
    assertEquals(0, review.seat());
    assertEquals("SA H2", review.snapshot().get("hand"));
    journal.feedback(review, "검토 필요", "", "사람의 실수와 엔진 추천을 구분");
    String after = Files.readString(file, StandardCharsets.UTF_8);
    assertTrue(after.startsWith(original));
    var feedback = Json.object(Json.read(after.substring(original.length()).strip()));
    assertEquals(ApplicationInfo.VERSION, feedback.get("engineVersion"));
    assertEquals("0.6.0", feedback.get("originalEngineVersion"));
    assertEquals(false, feedback.get("applyExact"));
  }

}
