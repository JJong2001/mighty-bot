package mighty.ai.play;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Random;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import mighty.game.GameMode;
import mighty.game.GamePhase;
import mighty.game.PlayOrder;
import mighty.game.Seat;
import mighty.game.TableLayout;
import mighty.game.bidding.Bid;
import mighty.game.bidding.BiddingResult;
import mighty.game.deal.DeckFactory;
import mighty.game.friend.CardFriend;
import mighty.game.friend.FriendSelection;
import mighty.game.friend.SpecialFriend;
import mighty.game.observation.View;
import mighty.game.round.RoundState;
import mighty.game.rule.Rules;
import mighty.game.trick.PublicPlay;
import mighty.model.Card;
import mighty.model.Joker;
import mighty.model.Trump;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

/**
 * 0.15.41 기준과 검토된 전략 변경분을 고정합니다. 정답이나 승률을 증명하는 검사가 아닙니다.
 */
public class PlayPolicyCharacterizationTest {

  private static final String RESOURCE = "/play-policy-0.15.41.tsv";
  private static final String STRATEGY_DELTAS = "/play-policy-operating-suit-deltas.tsv";
  private static final String SUPPORT_DELTAS = "/play-policy-support-flow-deltas.tsv";
  private static final String DESCENDING_DELTAS = "/play-policy-friend-descending-deltas.tsv";
  private static final String SUPPORT_ORDER_DELTAS = "/play-policy-support-order-deltas.tsv";
  private static final String MIGHTY_SUPPORT_DELTAS = "/play-policy-mighty-support-deltas.tsv";
  private static final String RECOVERY_DELTAS = "/play-policy-joint-special-recovery-deltas.tsv";
  private static final String APPROVED_ACTION_DELTAS = "/play-policy-approved-action-deltas.tsv";
  private static final String EXPLANATION_DELTAS = "/play-policy-explanation-deltas.tsv";

  static record Scenario(Trump trump, int friend, boolean sixSeats, int seed) {

    String id() {
      return trump + ":friend=" + friend + ":six=" + sixSeats + ":seed=" + seed;
    }

    View viewAtTurn(int targetTurn) {
      var seats = IntStream.range(0, sixSeats ? 6 : 5).mapToObj(Seat::new).toList();
      var active = new HashSet<>(seats);
      if (sixSeats) {
        active.remove(new Seat(2));
      }
      var order = PlayOrder.from(new TableLayout(
          sixSeats ? GameMode.SIX_PLAYER : GameMode.FIVE_PLAYER, seats), active);
      List<Card> deck = new ArrayList<>(DeckFactory.create());
      Collections.shuffle(deck, new Random(seed));
      var hands = new HashMap<Seat, List<Card>>();
      for (int index = 0; index < 5; index++) {
        hands.put(order.seats().get(index), List.copyOf(deck.subList(index * 10, index * 10 + 10)));
      }
      var declarer = order.seats().get(seed % 5);
      var round = RoundState.start(order, new BiddingResult(declarer, new Bid(15, trump)), hands,
          List.copyOf(deck.subList(50, 53)));
      round.takeKitty(declarer);
      round.discard(declarer, List.copyOf(deck.subList(50, 53)));
      FriendSelection selection = switch (friend) {
        case 0 -> new CardFriend(Rules.mighty(trump));
        case 1 -> new CardFriend(Joker.INSTANCE);
        case 2 -> SpecialFriend.FIRST_TRICK;
        default -> SpecialFriend.NONE;
      };
      round.chooseFriend(declarer, selection);
      List<String> baseline;
      try {
        baseline = baselineTraces();
      } catch (Exception failure) {
        throw new IllegalStateException("의도 검사에 사용할 기존 공개 진행을 읽을 수 없습니다.", failure);
      }
      String[] recorded = baseline.stream().filter(line -> line.startsWith(id() + "\t"))
          .findFirst().orElseThrow().split("\t")[1].split("\\|");
      for (int turn = 1; turn < targetTurn; turn++) {
        var view = round.currentView();
        // 앞 차례의 새 정책 때문에 검사할 손패 자체가 달라지지 않도록 입력을 고정합니다.
        String[] entry = recorded[turn - 1].split(":", 2);
        assertEquals(view.self().index(), Integer.parseInt(entry[0]));
        round.play(new PublicPlay(view.self(), recordedAction(entry[1])));
      }
      return round.currentView();
    }

    String trace() throws Exception {
      var seats = IntStream.range(0, sixSeats ? 6 : 5).mapToObj(Seat::new).toList();
      var active = new HashSet<>(seats);
      if (sixSeats) {
        active.remove(new Seat(2));
      }
      var order = PlayOrder.from(new TableLayout(
          sixSeats ? GameMode.SIX_PLAYER : GameMode.FIVE_PLAYER, seats), active);
      List<Card> deck = new ArrayList<>(DeckFactory.create());
      Collections.shuffle(deck, new Random(seed));
      var hands = new HashMap<Seat, List<Card>>();
      for (int index = 0; index < 5; index++) {
        hands.put(order.seats().get(index), List.copyOf(deck.subList(index * 10, index * 10 + 10)));
      }
      var kitty = List.copyOf(deck.subList(50, 53));
      var declarer = order.seats().get(seed % 5);
      var round = RoundState.start(order, new BiddingResult(declarer, new Bid(15, trump)), hands,
          kitty);
      // 준비 정책이 기루를 변경하지 않도록 고정 배분의 바닥만 다시 묻습니다.
      round.takeKitty(declarer);
      round.discard(declarer, kitty);
      FriendSelection selection = switch (friend) {
        case 0 -> new CardFriend(Rules.mighty(trump));
        case 1 -> new CardFriend(Joker.INSTANCE);
        case 2 -> SpecialFriend.FIRST_TRICK;
        default -> SpecialFriend.NONE;
      };
      round.chooseFriend(declarer, selection);
      var policy = new RulePlayPolicy();
      var actions = new ArrayList<String>();
      var reasons = new StringBuilder();
      while (round.phase() == GamePhase.PLAY) {
        var view = round.currentView();
        var decision = policy.recommend(view);
        actions.add(view.self().index() + ":" + decision.action());
        reasons.append(decision.reason()).append('\n');
        round.play(new PublicPlay(view.self(), decision.action()));
      }
      var hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
          .digest(reasons.toString().getBytes(StandardCharsets.UTF_8)));
      var result = round.result().orElseThrow();
      // Map의 출력 순서는 JVM마다 달라질 수 있으므로 자리 순서로 직렬화합니다.
      var captured = order.seats().stream()
          .map(seat -> seat.index() + ":" + result.captured().get(seat)).toList();
      return id() + "\t" + String.join("|", actions) + "\t" + hash
          + "\t" + result.declarer() + ":" + result.contract() + ":" + result.friend()
          + ":" + captured + ":discarded=" + result.discardedPoints();
    }
  }

  private static List<Scenario> scenarios() {
    var scenarios = new ArrayList<Scenario>();
    for (Trump trump : Trump.values()) {
      for (int friend = 0; friend < 4; friend++) {
        for (int seed : List.of(3, 11, 29)) {
          scenarios.add(new Scenario(trump, friend, false, seed));
        }
      }
    }
    return List.copyOf(scenarios);
  }

  private static List<String> baselineTraces() throws Exception {
    var expected = new ArrayList<>(readFixture(RESOURCE));
    for (String resource : List.of(STRATEGY_DELTAS, SUPPORT_DELTAS, DESCENDING_DELTAS,
        SUPPORT_ORDER_DELTAS, MIGHTY_SUPPORT_DELTAS, RECOVERY_DELTAS, APPROVED_ACTION_DELTAS)) {
      applyActionDeltas(expected, resource);
    }
    applyExplanationDeltas(expected, EXPLANATION_DELTAS);
    return expected;
  }

  private static List<String> readFixture(String resource) throws Exception {
    var input = PlayPolicyCharacterizationTest.class.getResourceAsStream(resource);
    assertNotNull(input, "전략 변경 검토 기준이 필요합니다: " + resource);
    try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
      return reader.lines().toList();
    }
  }

  private static int traceIndex(List<String> traces, String id) {
    return IntStream.range(0, traces.size()).filter(i -> traces.get(i).startsWith(id + "\t"))
        .findFirst().orElseThrow();
  }

  private static void applyActionDeltas(List<String> traces, String resource) throws Exception {
    var ids = new HashSet<String>();
    for (String line : readFixture(resource)) {
      String[] fields = line.split("\t");
      assertEquals(4, fields.length, "행동 기준은 배분·행동·이유·정산을 포함합니다.");
      org.junit.jupiter.api.Assertions.assertTrue(ids.add(fields[0]), "중복 행동 기준: " + fields[0]);
      traces.set(traceIndex(traces, fields[0]), line);
    }
  }

  private static void applyExplanationDeltas(List<String> traces, String resource)
      throws Exception {
    var ids = new HashSet<String>();
    for (String line : readFixture(resource)) {
      String[] delta = line.split("\t");
      assertEquals(2, delta.length, "설명 기준은 시나리오 ID와 이유 해시만 포함합니다.");
      org.junit.jupiter.api.Assertions.assertTrue(ids.add(delta[0]), "중복 설명 기준: " + delta[0]);
      int index = traceIndex(traces, delta[0]);
      String[] fields = traces.get(index).split("\t");
      fields[2] = delta[1];
      traces.set(index, String.join("\t", fields));
    }
  }

  private static mighty.game.action.Action recordedAction(String text) {
    if (text.startsWith("StandardPlay[")) {
      String code = text.substring(text.indexOf("card=") + 5, text.indexOf(','));
      return mighty.data.ActionConverter.decode(code + (text.contains("call=true") ? ":CALL" : ""));
    }
    if (text.startsWith("JokerLead[")) {
      String suit = text.substring(text.indexOf('=') + 1, text.length() - 1);
      return new mighty.game.action.JokerLead(mighty.model.Suit.valueOf(suit));
    }
    if (text.equals("JokerFollow[]")) {
      return new mighty.game.action.JokerFollow();
    }
    throw new IllegalArgumentException("지원하지 않는 회귀 행동입니다: " + text);
  }

  @TestFactory
  Stream<DynamicTest> preservesActionsReasonsAndResults() throws Exception {
    var expected = baselineTraces();
    applyActionDeltas(expected, "/play-policy-feedback-action-deltas.tsv");
    applyExplanationDeltas(expected, "/play-policy-feedback-explanation-deltas.tsv");
    expected.removeIf(line -> line.split("\t", 2)[0].contains(":six=true:"));
    var scenarios = scenarios();
    assertEquals(scenarios.size(), expected.size());
    return IntStream.range(0, scenarios.size()).mapToObj(index -> DynamicTest.dynamicTest(
        scenarios.get(index).id(), () -> {
          var before = expected.get(index).split("\t");
          var after = scenarios.get(index).trace().split("\t");
          assertEquals(before[0], after[0], "검증 배분");
          var beforeActions = before[1].split("\\|");
          var afterActions = after[1].split("\\|");
          assertEquals(50, afterActions.length);
          for (int turn = 0; turn < 50; turn++) {
            assertEquals(beforeActions[turn], afterActions[turn], "차례 " + (turn + 1));
          }
          assertEquals(before[2], after[2], "선택 이유 SHA-256");
          assertEquals(before[3], after[3], "최종 득점과 프렌드");
        }));
  }

  /**
   * 검토할 전략 변경의 추적을 별도 파일로 출력합니다. 기존 기준 파일은 덮어쓰지 않습니다.
   */
  public static void main(String[] args) throws Exception {
    if (args.length != 1) {
      throw new IllegalArgumentException("새 기준 파일의 경로가 필요합니다.");
    }
    var lines = new ArrayList<String>();
    for (var scenario : scenarios()) {
      lines.add(scenario.trace());
    }
    Files.write(Path.of(args[0]), lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
    System.out.println(
        "Recorded " + lines.size() + " rounds / " + lines.size() * 50 + " decisions.");
  }
}
