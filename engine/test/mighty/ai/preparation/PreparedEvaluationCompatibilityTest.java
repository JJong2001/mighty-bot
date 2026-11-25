package mighty.ai.preparation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Stream;
import mighty.ai.evaluation.PreparedRoundEvaluator;
import mighty.data.CardParser;
import mighty.game.bidding.Bid;
import mighty.game.friend.CardFriend;
import mighty.game.rule.Rules;
import mighty.model.Trump;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * 수정 전 JAR에서 기록한 동일 배분의 평균 정산과 성공 횟수를 비교합니다.
 */
class PreparedEvaluationCompatibilityTest {

  static Stream<String> snapshots() throws IOException {
    try (var reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(
        PreparedEvaluationCompatibilityTest.class.getResourceAsStream(
            "/prepared-evaluation-refactor-baseline.txt")), StandardCharsets.UTF_8))) {
      return reader.lines().toList().stream();
    }
  }

  @ParameterizedTest
  @MethodSource("snapshots")
  void preservesSimulationResults(String row) {
    var p = row.split("\\|");
    var bid = new Bid(Integer.parseInt(p[0]), Trump.valueOf(p[1]));
    var plan = new PreparationPlan(bid, CardParser.parseList(p[3]),
        new CardFriend(Rules.mighty(bid.trump())), "");
    var actual = new PreparedRoundEvaluator().evaluate(bid, CardParser.parseList(p[2]), plan,
        List.of());
    assertEquals(Double.parseDouble(p[4]), actual.mean());
    assertEquals(Integer.parseInt(p[5]), actual.wins());
  }
}
