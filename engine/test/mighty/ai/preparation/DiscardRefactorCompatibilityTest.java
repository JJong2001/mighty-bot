package mighty.ai.preparation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;
import mighty.data.CardParser;
import mighty.game.Seat;
import mighty.game.bidding.Bid;
import mighty.game.observation.DiscardView;
import mighty.model.Trump;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 리팩토링 이전의 추천과 후보 전체 순서를 그대로 보존하는지 검사합니다.
 */
class DiscardRefactorCompatibilityTest {

  static Stream<String> snapshots() throws IOException {
    try (var reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(
        DiscardRefactorCompatibilityTest.class.getResourceAsStream(
            "/discard-refactor-baseline.txt")), StandardCharsets.UTF_8))) {
      return reader.lines().toList().stream();
    }
  }

  @ParameterizedTest
  @MethodSource("snapshots")
  void preservesRankingAndLimitedComparison(String row) throws Exception {
    var parts = row.split("\\|");
    var view = new DiscardView(new Seat(0),
        new Bid(Integer.parseInt(parts[1]), Trump.valueOf(parts[0])),
        CardParser.parseList(parts[2]));
    var result = new RuleDiscardPolicy().ranked(view, Integer.parseInt(parts[3])).toString();
    var hash = HexFormat.of().formatHex(
        MessageDigest.getInstance("SHA-256").digest(result.getBytes(StandardCharsets.UTF_8)));
    assertEquals(parts[4], hash);
  }
}
