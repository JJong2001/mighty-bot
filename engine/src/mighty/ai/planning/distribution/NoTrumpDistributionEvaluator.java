package mighty.ai.planning.distribution;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import mighty.ai.bidding.BiddingHand;
import mighty.ai.planning.NoTrumpOpeningPlan;
import mighty.ai.planning.NoTrumpOpeningPlanner;
import mighty.game.bidding.Bid;
import mighty.game.deal.DeckFactory;
import mighty.game.friend.*;
import mighty.model.*;

/**
 * 미확인 카드를 지정 프렌드의 나머지 9장과 야당의 각 10장에 함께 배분합니다. 가상 배분의 문양 통제 경로를 평가하며, 지정 카드 외의 친구 카드도 여당 승리로 셉니다.
 * 실제 상대 패는 입력받지 않으며 확률은 유한 표본의 운영 경로 추정치입니다.
 */
public final class NoTrumpDistributionEvaluator {

  private static final int SAMPLES = 256;
  private static final Map<String, NoTrumpDistribution> CACHE = new ConcurrentHashMap<>();

  private NoTrumpDistributionEvaluator() {
  }

  public static NoTrumpDistribution evaluate(BiddingHand hand, Collection<? extends Card> excluded,
      Card called) {
    return evaluate(hand, excluded, called,
        NoTrumpOpeningPlanner.forCandidate(hand, excluded, called, 0, List.of()));
  }

  private static NoTrumpDistribution evaluate(BiddingHand hand, Collection<? extends Card> excluded,
      Card called, NoTrumpOpeningPlan plan) {
    List<Card> own = DeckFactory.create().stream().filter(hand::contains).toList();
    if (own.size() != 10 || excluded.size() > 3 || excluded.contains(called)) {
      return new NoTrumpDistribution(0,
          0, 0, 0, 0, 0, 0);
    }
    String dealKey =
        own.stream().map(Card::toCode).toList() + "/" + excluded.stream().map(Card::toCode).sorted()
            .toList() + "/" + called.toCode();
    if (CACHE.size() > 512) {
      CACHE.clear();
    }
    String key = dealKey + "/" + plan;
    long seed = excluded.size() == 3 ? sharedSeed(own, excluded) : dealKey.hashCode();
    return CACHE.computeIfAbsent(key,
        k -> evaluate(own, excluded, plan.selection(), seed, plan));
  }

  public static NoTrumpDistribution evaluateSolo(BiddingHand hand,
      Collection<? extends Card> excluded) {
    List<Card> own = DeckFactory.create().stream().filter(hand::contains).toList();
    if (own.size() != 10 || excluded.size() > 3) {
      return new NoTrumpDistribution(0, 0, 0, 0, 0, 0, 0);
    }
    if (CACHE.size() > 512) {
      CACHE.clear();
    }
    String key =
        own.stream().map(Card::toCode).toList() + "/" + excluded.stream().map(Card::toCode).sorted()
            .toList() + "/solo";
    return CACHE.computeIfAbsent(key,
        k -> evaluate(own, excluded, SpecialFriend.NONE,
            excluded.size() == 3 ? sharedSeed(own, excluded) : k.hashCode(),
            new NoTrumpOpeningPlan(SpecialFriend.NONE, Optional.empty(), Optional.empty(),
                excluded.size() == 3, false, NoTrumpOpeningPlan.Purpose.DEFER)));
  }

  private static long sharedSeed(List<Card> own, Collection<? extends Card> excluded) {
    return (own.stream().map(Card::toCode).toList().toString() + "/"
        + excluded.stream().map(Card::toCode).sorted().toList()).hashCode();
  }

  public static int soloCeiling(NoTrumpDistribution distribution) {
    if (distribution.twentyRate() >= 0.60) {
      return 20;
    }
    if (distribution.nineteenWins() >= SAMPLES * 0.80) {
      return 19;
    }
    if (distribution.eighteenWins() >= SAMPLES * 0.80) {
      return 18;
    }
    return 0;
  }

  public record Support(Card friend, NoTrumpDistribution distribution, NoTrumpOpeningPlan plan) {

  }

  public static Support bestSupport(BiddingHand hand, Collection<? extends Card> excluded) {
    return bestSupport(hand, excluded, 0);
  }

  public static Support bestSupport(BiddingHand hand, Collection<? extends Card> excluded,
      int contractPoints) {
    return bestSupport(hand, excluded, contractPoints, List.of());
  }

  public static Support bestSupport(BiddingHand hand, Collection<? extends Card> excluded,
      int contractPoints, List<Bid> history) {
    return mighty.ai.planning.NoTrumpPlanner.supportCandidates(hand, excluded).stream()
        .map(card -> {
          var plan = NoTrumpOpeningPlanner.forCandidate(hand, excluded, card, contractPoints,
              history);
          return new Support(card, evaluate(hand, excluded, card, plan), plan);
        })
        // 자력 주력을 소모하기 전에 다른 문양의 상위를 연결하는 의존 관계를 먼저 충족합니다.
        .max(Comparator.<Support>comparingInt(s -> s.plan().connectionBeforeRun() ? 1 : 0)
            .thenComparingInt(s -> contractPoints == 0
                ? qualifyingContract(hand, s)
                : qualifyingContract(hand, s) >= contractPoints ? 1 : 0)
            // 반환까지 평가한 초구프가 풀 조건을 충족하면 그 수순을 준비 단계에서도 사용합니다.
            .thenComparingInt(s -> (contractPoints == 0 || contractPoints == 20)
                && hand.contains(s.friend())
                && mighty.ai.planning.NoTrumpPlanner.openingFriendReturn(hand).isPresent()
                && qualifyingContract(hand, s) == 20 ? 1 : 0)
            .thenComparingInt(s -> winsAt(s.distribution(), contractPoints == 0
                ? qualifyingContract(hand, s) : contractPoints))
            .thenComparingDouble(s -> s.distribution().meanPoints())
            .thenComparingInt(s -> hand.contains(s.friend()) ? 1 : 0))
        .orElseThrow();
  }

  public static int qualifyingContract(BiddingHand hand, Support support) {
    var distribution = support.distribution();
    if (hand.contains(support.friend())
        && mighty.ai.planning.NoTrumpPlanner.openingFriendReturn(hand).isPresent()
        && distribution.twentyRate() >= 0.50 && distribution.seventeenRate() >= 0.80) {
      int failures = distribution.samples() - distribution.twentyWins();
      double failureLoss = failures == 0 ? 0
          : (20 - distribution.meanPoints()) * distribution.samples() / failures;
      // 실패한 배분에서도 대체로 한 구의 손실에 그치는 반환 경로는 풀을 검토합니다.
      if (failureLoss <= 4.5) {
        return 20;
      }
    }
    if (distribution.twentyRate() >= 0.60) {
      return 20;
    }
    if (distribution.seventeenRate() >= 0.70) {
      return 17;
    }
    if (mighty.ai.planning.NoTrumpPlanner.splitControl(hand, support.friend())
        && distribution.sixteenRate() >= 0.65 && distribution.meanPoints() >= 16) {
      return 16;
    }
    return 0;
  }

  private static int winsAt(NoTrumpDistribution distribution, int points) {
    if (points == 20) {
      return distribution.twentyWins();
    }
    if (points == 16) {
      return distribution.sixteenWins();
    }
    return distribution.seventeenWins();
  }

  private static NoTrumpDistribution evaluate(List<Card> own, Collection<? extends Card> excluded,
      FriendSelection selection, long seed, NoTrumpOpeningPlan plan) {
    Random random = new Random(seed);
    int sixteen = 0, seventeen = 0, eighteen = 0, nineteen = 0, twenty = 0, total = 0;
    Card called = selection instanceof CardFriend friend ? friend.card() : own.get(0);
    var hand = BiddingHand.from(own);
    var returnSuit = plan.returnSuit();
    if (selection == SpecialFriend.FIRST_TRICK && plan.opening().isEmpty()) {
      return new NoTrumpDistribution(0, 0, 0, 0, 0, 0, 0);
    }
    Suit main = mighty.ai.planning.NoTrumpPlanner.signallingSuit(hand);
    for (int index = 0; index < SAMPLES; index++) {
      var deal = excluded.size() == 3
          ? ConditionalDeal.sampleKnownBuried(own, excluded, selection, random)
          : ConditionalDeal.sample(own, excluded, selection, index, random);
      // 바닥 전에는 열 장이 바뀔 수 있어 일반 지정프의 초구를 확정 수순으로 소비하지 않습니다.
      // 초구프는 승자에 따라 소속 자체가 정해지므로 예비 평가에서도 초구가 필요합니다.
      if (plan.opening().isPresent() && (plan.finalHand()
          || selection == SpecialFriend.FIRST_TRICK)) {
        deal = deal.afterOpening(plan.opening().get());
      }
      int lost = returnSuit.isPresent()
          ? ControlRoute.openingReturnLoss(deal, main, returnSuit.get(),
          hand.ordinaryTopRunLength(main, Trump.NO_TRUMP))
          : ControlRoute.pointsLost(deal, called);
      int points = Math.max(0, 20 - lost);
      total += points;
      if (lost <= 4) {
        sixteen++;
      }
      if (lost <= 3) {
        seventeen++;
      }
      if (lost <= 2) {
        eighteen++;
      }
      if (lost <= 1) {
        nineteen++;
      }
      if (lost == 0) {
        twenty++;
      }
    }
    return new NoTrumpDistribution(SAMPLES, sixteen, seventeen, twenty, (double) total / SAMPLES,
        eighteen, nineteen);
  }

}
