package mighty.ai.bidding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import mighty.ai.play.RulePlayPolicy;
import mighty.ai.preparation.PreparationPlan;
import mighty.ai.preparation.PreparationPlanner;
import mighty.ai.preparation.RuleDiscardPolicy;
import mighty.ai.preparation.RuleFriendPolicy;
import mighty.application.round.RoundService;
import mighty.game.GameMode;
import mighty.game.PlayOrder;
import mighty.game.Seat;
import mighty.game.TableLayout;
import mighty.game.bidding.Bid;
import mighty.game.bidding.BiddingResult;
import mighty.game.deal.DeckFactory;
import mighty.game.friend.FriendSelection;
import mighty.game.friend.SpecialFriend;
import mighty.game.round.RoundResult;
import mighty.game.round.RoundSettlement;
import mighty.game.round.RoundState;
import mighty.model.Card;

/**
 * 후보 공약의 명목 정산 점수를 규칙 봇의 한 판 운영으로 추정합니다.
 * 같은 손패에서 공약 후보에 같은 가상 배분을 사용합니다.
 */
public final class BidExpectedScoreEvaluator {

  private static final int INITIAL_BIDDING_KITTIES = 10;
  private static final int EXPANDED_BIDDING_KITTIES = 100;
  private static final int BIDDING_DEALS_PER_KITTY = 10;
  // 첫 바닥 10묶음으로 계산한 평균 차의 95% 구간에 쓰는 t 임계값입니다.
  private static final double INITIAL_BIDDING_T_CRITICAL = 2.262;
  private static final Map<EvaluationKey, List<Integer>> CACHE = new ConcurrentHashMap<>();
  private static final int KNOWN_KITTY_SAMPLES = 32;
  private static final Map<PreparedEvaluationKey, Double> PREPARED_CACHE = new ConcurrentHashMap<>();
  private static final List<Seat> SEATS = IntStream.range(0, GameMode.PLAYING_PLAYER_COUNT)
      .mapToObj(Seat::new).toList();
  private static final PlayOrder ORDER = PlayOrder.from(
      new TableLayout(GameMode.FIVE_PLAYER, SEATS), new HashSet<>(SEATS));
  private static final Seat DECLARER = SEATS.get(0);
  private static final RoundService ROUND_SERVICE = new RoundService(
      new RuleDiscardPolicy(), new RuleFriendPolicy(), new RulePlayPolicy());
  private static final PreparationPlanner PREPARATION_PLANNER = new PreparationPlanner();

  private record EvaluationKey(String hand, Bid contract, int dealsPerKitty) {}

  private record PreparedEvaluationKey(Bid current, List<Card> kept, Bid selected,
                                       FriendSelection friend, Set<Card> discarded) {}

  record ScoreSamples(List<Integer> kittySettlements, int dealsPerKitty) {

    ScoreSamples {
      kittySettlements = List.copyOf(kittySettlements);
    }

    double mean() {
      return kittySettlements.stream().mapToInt(Integer::intValue).average().orElse(
          Double.NEGATIVE_INFINITY) / dealsPerKitty;
    }

    boolean differenceIsUncertain(ScoreSamples other) {
      int count = kittySettlements.size();
      if (count != INITIAL_BIDDING_KITTIES || other.kittySettlements.size() != count
          || dealsPerKitty != other.dealsPerKitty) {
        return false;
      }
      // 같은 바닥패를 공유하는 10판은 한 묶음으로 보고, 같은 배분끼리 점수 차를 비교합니다.
      double sum = 0;
      double squares = 0;
      for (int i = 0; i < count; i++) {
        double difference = kittySettlements.get(i) - other.kittySettlements.get(i);
        sum += difference;
        squares += difference * difference;
      }
      double meanDifference = sum / count;
      double standardError = Math.sqrt(Math.max(0, squares - sum * meanDifference)
          / (count * (count - 1.0)));
      return Math.abs(meanDifference) <= INITIAL_BIDDING_T_CRITICAL * standardError;
    }
  }

  public double expectedDeclarerScore(BiddingHand hand, Bid contract) {
    return samples(hand, contract, 4, 8).mean();
  }

  ScoreSamples initialBiddingSamples(BiddingHand hand, Bid contract) {
    return samples(hand, contract, INITIAL_BIDDING_KITTIES, BIDDING_DEALS_PER_KITTY);
  }

  ScoreSamples expandedBiddingSamples(BiddingHand hand, Bid contract) {
    return samples(hand, contract, EXPANDED_BIDDING_KITTIES, BIDDING_DEALS_PER_KITTY);
  }

  /**
   * 실제로 본 13장을 고정하고, 버리기와 프렌드가 정해진 두 공약에 같은 상대 배분을 적용합니다.
   */
  public double expectedPreparedScore(Bid current, List<Card> seen, PreparationPlan preparation) {
    List<Card> kept = DeckFactory.create().stream().filter(seen::contains)
        .filter(card -> !preparation.discarded().contains(card)).toList();
    var key = new PreparedEvaluationKey(current, kept, preparation.contract(), preparation.friend(),
        Set.copyOf(preparation.discarded()));
    if (PREPARED_CACHE.size() > 512) {
      PREPARED_CACHE.clear();
    }
    return PREPARED_CACHE.computeIfAbsent(key, ignored -> {
      List<Card> unseen = DeckFactory.create().stream().filter(card -> !seen.contains(card)).toList();
      String seenKey = seen.stream().map(Card::toCode).sorted().toList().toString();
      Random random = new Random(seenKey.hashCode());
      int total = 0;
      for (int sample = 0; sample < KNOWN_KITTY_SAMPLES; sample++) {
        List<Card> opponents = new ArrayList<>(unseen);
        Collections.shuffle(opponents, random);
        RoundState round = createRound(kept, current, preparation.discarded(), opponents);
        round.takeKitty(DECLARER);
        total += playPrepared(round, preparation);
      }
      return (double) total / KNOWN_KITTY_SAMPLES;
    });
  }

  private static ScoreSamples samples(BiddingHand hand, Bid contract, int kittyCount,
      int dealsPerKitty) {
    List<Card> own = DeckFactory.create().stream().filter(hand::contains).toList();
    // 육마 첫 입찰의 8장에는 오마의 10장 배분 대신 기존 패 평가를 사용합니다.
    if (own.size() != 10) {
      return new ScoreSamples(List.of(), dealsPerKitty);
    }
    String handKey = own.stream().map(Card::toCode).sorted().toList().toString();
    var key = new EvaluationKey(handKey, contract, dealsPerKitty);
    if (CACHE.size() > 512) {
      CACHE.clear();
    }
    List<Integer> totals = CACHE.compute(key, (ignored, previous) -> {
      if (previous != null && previous.size() >= kittyCount) {
        return previous;
      }
      return evaluate(own, contract, handKey.hashCode(), kittyCount, dealsPerKitty,
          previous == null ? List.of() : previous);
    });
    return new ScoreSamples(totals.subList(0, kittyCount), dealsPerKitty);
  }

  private static List<Integer> evaluate(List<Card> own, Bid contract, long seed, int kittyCount,
      int dealsPerKitty, List<Integer> previous) {
    Random random = new Random(seed);
    List<Card> unseen = DeckFactory.create().stream().filter(card -> !own.contains(card)).toList();
    List<Integer> totals = new ArrayList<>(previous);
    for (int kittySample = 0; kittySample < kittyCount; kittySample++) {
      List<Card> pool = new ArrayList<>(unseen);
      Collections.shuffle(pool, random);
      List<Card> kitty = List.copyOf(pool.subList(0, 3));
      List<Card> opponentPool = pool.subList(3, pool.size());
      List<List<Card>> opponentDeals = new ArrayList<>(dealsPerKitty);
      for (int deal = 0; deal < dealsPerKitty; deal++) {
        List<Card> shuffledOpponents = new ArrayList<>(opponentPool);
        Collections.shuffle(shuffledOpponents, random);
        opponentDeals.add(shuffledOpponents);
      }
      // 이미 계산한 바닥의 난수 순서만 재현하고 대국은 새 바닥부터 이어갑니다.
      if (kittySample >= previous.size()) {
        totals.add(evaluateKitty(own, contract, kitty, opponentPool, opponentDeals));
      }
    }
    return List.copyOf(totals);
  }

  private static int evaluateKitty(List<Card> own, Bid contract, List<Card> kitty,
      List<Card> opponentPool, List<List<Card>> opponentDeals) {
    RoundState preparationRound = createRound(own, contract, kitty, opponentPool);
    Bid before = PreparationPlanner.highBeforeKitty(BiddingHand.from(own), contract, contract)
        .orElse(contract);
    if (!before.equals(contract)) {
      preparationRound.reviseContract(DECLARER, before);
    }
    preparationRound.takeKitty(DECLARER);
    PreparationPlan preparation = PREPARATION_PLANNER.choose(
        preparationRound.discardView(DECLARER), contract);
    int totalSettlement = 0;
    for (List<Card> opponents : opponentDeals) {
      RoundState round = createRound(own, contract, kitty, opponents);
      if (!before.equals(contract)) {
        round.reviseContract(DECLARER, before);
      }
      round.takeKitty(DECLARER);
      totalSettlement += playPrepared(round, preparation);
    }
    return totalSettlement;
  }

  private static int playPrepared(RoundState round, PreparationPlan preparation) {
    var history = round.contractHistory();
    for (var bid : PreparationPlanner.discardDeclarations(round.discardView(DECLARER),
        history.get(0), history, preparation)) {
      round.reviseContract(DECLARER, bid);
    }
    round.discard(DECLARER, preparation.discarded());
    round.chooseFriend(DECLARER, preparation.friend());
    RoundResult result = ROUND_SERVICE.playToEnd(round);
    boolean declaredSolo = preparation.friend() == SpecialFriend.NONE;
    return RoundSettlement.nominal(result, declaredSolo).get(DECLARER);
  }

  private static RoundState createRound(List<Card> own, Bid contract, List<Card> kitty,
      List<Card> opponentPool) {
    Map<Seat, List<Card>> hands = new HashMap<>();
    hands.put(DECLARER, own);
    for (int index = 1; index < SEATS.size(); index++) {
      int start = (index - 1) * 10;
      hands.put(SEATS.get(index), List.copyOf(opponentPool.subList(start, start + 10)));
    }
    return RoundState.start(ORDER, new BiddingResult(DECLARER, contract), hands, kitty);
  }
}
