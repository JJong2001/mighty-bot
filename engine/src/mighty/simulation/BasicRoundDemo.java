package mighty.simulation;

import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;
import mighty.ai.bidding.BaselineBiddingPolicy;
import mighty.ai.bidding.BiddingPolicy;
import mighty.ai.AiPolicies;
import mighty.application.bidding.BiddingService;
import mighty.application.round.RoundService;
import mighty.game.GameMode;
import mighty.game.PlayOrder;
import mighty.game.Seat;
import mighty.game.TableLayout;
import mighty.game.bidding.BiddingResult;
import mighty.game.bidding.BiddingState;
import mighty.game.deal.DealFormat;
import mighty.game.deal.DealParticipants;
import mighty.game.deal.DeckFactory;
import mighty.game.observation.BiddingView;
import mighty.game.round.RoundResult;
import mighty.game.round.RoundState;
import mighty.model.Card;

/**
 * 테탑 연결 없이 새 진행 흐름을 확인하는 로컬 시뮬레이션 진입점입니다.
 */
public final class BasicRoundDemo {

  private BasicRoundDemo() {
  }

  /**
   * 시뮬레이터만 전체 배분을 보관하고 각 AI에는 자기 관측만 전달합니다.
   */
  public static void main(String[] args) {
    long seed = args.length == 0 ? 1L : Long.parseLong(args[0]);
    boolean baseline = args.length > 1 && args[1].equals("baseline");
    Random random = new Random(seed);
    List<Card> deck = new ArrayList<>(DeckFactory.create());
    List<Seat> seats = IntStream.range(0, 5).mapToObj(Seat::new).toList();
    Map<Seat, List<Card>> hands = new HashMap<>();
    Map<Seat, Integer> scores = new HashMap<>();
    seats.forEach(seat -> scores.put(seat, 40));
    Seat opening = seats.get(random.nextInt(5));
    BiddingPolicy biddingPolicy =
        baseline ? new BaselineBiddingPolicy() : AiPolicies.current().bidding();
    BiddingService biddingService = new BiddingService(biddingPolicy);
    BiddingResult auction;
    while (true) {
      Collections.shuffle(deck, random);
      for (int index = 0; index < seats.size(); index++) {
        hands.put(seats.get(index), List.copyOf(deck.subList(index * 10, index * 10 + 10)));
      }
      BiddingState bidding = BiddingState.start(
          new DealParticipants(DealFormat.FIVE_PLAYER_OPENING, seats),
          opening);
      while (!bidding.isFinished()) {
        Seat self = bidding.currentSeat().orElseThrow();
        bidding = biddingService.playTurn(
            new BiddingView(self, hands.get(self), scores.get(self), bidding));
      }
      if (bidding.result().orElseThrow() instanceof mighty.game.bidding.DealMissResult miss) {
        scores.merge(miss.declaredBy(), -5, Integer::sum);
        opening = miss.declaredBy();
        continue;
        // 딜미스는 낙찰로 형변환하지 않고 같은 선언자부터 재배분합니다.
      }
      auction = (BiddingResult) bidding.result().orElseThrow();
      break;
    }
    PlayOrder order = PlayOrder.from(new TableLayout(GameMode.FIVE_PLAYER, seats),
        new HashSet<>(seats));
    RoundState round = RoundState.start(order, auction, hands, deck.subList(50, 53));
    RoundResult result = (baseline ? RoundService.baseline() : RoundService.strategic()).playToEnd(
        round);
    PrintWriter output = new PrintWriter(new OutputStreamWriter(System.out, StandardCharsets.UTF_8),
        true);
    output.printf("시드 %d · 주공 %d번 · 공약 %d %s%n", seed,
        result.declarer().index(), result.contract().points(), result.contract().trump());
    round.history().forEach(trick -> output.printf("%d구: %d번 승리, %d점%n",
        trick.number().value(), trick.winner().index(), trick.points()));
    if (round.settingCollectedCards() > 0) {
      output.printf("셋팅: 남은 %d장 획득%n", round.settingCollectedCards());
    }
    output.printf("여당 %d점 / 야당 %d점 · 공약 %s%n", result.teamPoints(),
        result.oppositionPoints(), result.succeeded() ? "성공" : "실패");
    output.flush();
  }

}
