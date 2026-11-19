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
import mighty.ai.preparation.RuleDiscardPolicy;
import mighty.ai.preparation.RuleFriendPolicy;
import mighty.application.round.RoundService;
import mighty.game.GamePhase;
import mighty.game.GameMode;
import mighty.game.PlayOrder;
import mighty.game.Seat;
import mighty.game.TableLayout;
import mighty.game.bidding.Bid;
import mighty.game.bidding.BiddingResult;
import mighty.game.bidding.ContractChangeRules;
import mighty.game.deal.DeckFactory;
import mighty.game.friend.SpecialFriend;
import mighty.game.round.RoundResult;
import mighty.game.round.RoundSettlement;
import mighty.game.round.RoundState;
import mighty.model.Card;

/**
 * 후보 공약의 명목 정산 점수를 현재 규칙 봇의 전체 한 판 운영으로 추정합니다.
 * 같은 손패에서는 모든 공약이 같은 가상 카드 배분 표본을 사용합니다.
 */
public final class BidExpectedScoreEvaluator {

  private static final int SAMPLES = 32;
  private static final Map<String, Double> CACHE = new ConcurrentHashMap<>();
  private static final Map<String, Double> AWARD_CACHE = new ConcurrentHashMap<>();
  private static final List<Seat> SEATS = IntStream.range(0, GameMode.PLAYING_PLAYER_COUNT)
      .mapToObj(Seat::new).toList();
  private static final PlayOrder ORDER = PlayOrder.from(
      new TableLayout(GameMode.FIVE_PLAYER, SEATS), new HashSet<>(SEATS));
  private static final Seat DECLARER = SEATS.get(0);
  private static final RoundService ROUND_SERVICE = new RoundService(
      new RuleDiscardPolicy(), new RuleFriendPolicy(), new RulePlayPolicy());

  public double expectedDeclarerScore(BiddingHand hand, Bid contract) {
    List<Card> own = DeckFactory.create().stream().filter(hand::contains).toList();
    if (own.size() != 10) {
      return Double.NEGATIVE_INFINITY;
    }
    String handKey = own.stream().map(Card::toCode).sorted().toList().toString();
    String key = handKey + "/" + contract.points() + "/" + contract.trump();
    if (CACHE.size() > 512) {
      CACHE.clear();
    }
    return CACHE.computeIfAbsent(key,
        ignored -> evaluate(own, contract, handKey.hashCode()));
  }

  /**
   * 낙찰 공약 뒤 바닥을 들기 전에 가능한 전환까지 고려한 예상 점수입니다.
   */
  public double expectedScoreAfterPreKitty(BiddingHand hand, Bid awarded) {
    List<Card> own = DeckFactory.create().stream().filter(hand::contains).toList();
    if (own.size() != 10) {
      return Double.NEGATIVE_INFINITY;
    }
    String handKey = own.stream().map(Card::toCode).sorted().toList().toString();
    String key = handKey + "/award/" + awarded.points() + "/" + awarded.trump();
    if (AWARD_CACHE.size() > 512) {
      AWARD_CACHE.clear();
    }
    return AWARD_CACHE.computeIfAbsent(key, ignored -> {
      double best = expectedDeclarerScore(hand, awarded);
      var assessments = new BiddingHandEvaluator().assess(hand);
      for (var assessment : assessments) {
        if (!assessment.canCompete()) {
          continue;
        }
        int start = awarded.points();
        for (int points = start; points <= assessment.ceilingPoints(); points++) {
          Bid candidate = new Bid(points, assessment.trump());
          if (candidate.equals(awarded)
              || !ContractChangeRules.canChange(awarded, awarded, candidate,
              GamePhase.BEFORE_KITTY)) {
            continue;
          }
          best = Math.max(best, expectedDeclarerScore(hand, candidate));
        }
      }
      return best;
    });
  }

  private static double evaluate(List<Card> own, Bid contract, long seed) {
    Random random = new Random(seed);
    int total = 0;
    for (int sample = 0; sample < SAMPLES; sample++) {
      RoundState round = createRound(own, contract, random);
      RoundResult result = ROUND_SERVICE.playToEnd(round);
      boolean declaredSolo = round.friendSelection().orElseThrow() == SpecialFriend.NONE;
      total += RoundSettlement.nominal(result, declaredSolo).get(DECLARER);
    }
    return (double) total / SAMPLES;
  }

  private static RoundState createRound(List<Card> own, Bid contract, Random random) {
    List<Card> pool = new ArrayList<>(DeckFactory.create().stream()
        .filter(card -> !own.contains(card)).toList());
    Collections.shuffle(pool, random);

    List<Card> kitty = List.copyOf(pool.subList(0, 3));
    Map<Seat, List<Card>> hands = new HashMap<>();
    hands.put(DECLARER, own);
    for (int index = 1; index < SEATS.size(); index++) {
      int start = 3 + (index - 1) * 10;
      hands.put(SEATS.get(index), List.copyOf(pool.subList(start, start + 10)));
    }
    return RoundState.start(ORDER, new BiddingResult(DECLARER, contract), hands, kitty);
  }
}
