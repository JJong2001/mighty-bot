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
      var policy = new RulePlayPolicy();
      for (int turn = 1; turn < targetTurn; turn++) {
        var view = round.currentView();
        round.play(new PublicPlay(view.self(), policy.choose(view)));
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
        for (boolean six : List.of(false, true)) {
          for (int seed : List.of(3, 11, 29)) {
            scenarios.add(new Scenario(trump, friend, six, seed));
          }
        }
      }
    }
    return List.copyOf(scenarios);
  }

  @TestFactory
  Stream<DynamicTest> preservesActionsReasonsAndResults() throws Exception {
    var input = getClass().getResourceAsStream(RESOURCE);
    assertNotNull(input, "수정 전 엔진으로 기록한 기준 파일이 필요합니다.");
    List<String> expected;
    try (var reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
      expected = new ArrayList<>(reader.lines().toList());
    }
    // 원래 리팩토링 기준은 보존하고, 별도 검토한 전략 변경 배분만 명시적으로 덮습니다.
    for (String resource : List.of(STRATEGY_DELTAS, SUPPORT_DELTAS, DESCENDING_DELTAS,
        SUPPORT_ORDER_DELTAS, MIGHTY_SUPPORT_DELTAS, RECOVERY_DELTAS,
        APPROVED_ACTION_DELTAS)) {
      var deltas = getClass().getResourceAsStream(resource);
      assertNotNull(deltas, "전략 변경 검토 기준이 필요합니다. " + resource);
      var changedIds = new HashSet<String>();
      try (var reader = new BufferedReader(new InputStreamReader(deltas, StandardCharsets.UTF_8))) {
        for (String line : reader.lines().toList()) {
          String id = line.split("\t")[0];
          org.junit.jupiter.api.Assertions.assertTrue(changedIds.add(id), "중복 변경 기준: " + id);
          int index = IntStream.range(0, expected.size())
              .filter(i -> expected.get(i).startsWith(id + "\t")).findFirst().orElseThrow();
          expected.set(index, line);
        }
      }
    }
    var explanations = getClass().getResourceAsStream(EXPLANATION_DELTAS);
    assertNotNull(explanations, "설명 문구 변경 기준이 필요합니다.");
    var explainedIds = new HashSet<String>();
    try (var reader = new BufferedReader(
        new InputStreamReader(explanations, StandardCharsets.UTF_8))) {
      for (String line : reader.lines().toList()) {
        String[] delta = line.split("\t");
        assertEquals(2, delta.length, "설명 기준은 시나리오 ID와 이유 해시만 포함합니다.");
        String id = delta[0];
        org.junit.jupiter.api.Assertions.assertTrue(explainedIds.add(id), "중복 설명 기준: " + id);
        int index = IntStream.range(0, expected.size())
            .filter(i -> expected.get(i).startsWith(id + "\t")).findFirst().orElseThrow();
        String[] fields = expected.get(index).split("\t");
        fields[2] = delta[1];
        expected.set(index, String.join("\t", fields));
      }
    }
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
