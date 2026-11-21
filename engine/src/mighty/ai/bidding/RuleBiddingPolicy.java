package mighty.ai.bidding;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import mighty.ai.bidding.BidExpectedScoreEvaluator.ScoreSamples;
import mighty.game.bidding.Bid;
import mighty.game.bidding.BidOffer;
import mighty.game.bidding.BidPass;
import mighty.game.bidding.BiddingAction;
import mighty.game.bidding.LegalBiddingActions;
import mighty.game.observation.BiddingView;
import mighty.model.Trump;

/**
 * 자발적 입찰 자격과 강제 첫 공약을 분리하고 운영 가능한 후보만 경쟁시킵니다.
 */
public final class RuleBiddingPolicy implements BiddingPolicy {

  private static final Comparator<Candidate> CANDIDATE_ORDER =
      Comparator.comparingInt(Candidate::highestSuccessScore)
          .thenComparingInt(candidate -> candidate.assessment().preference())
          .thenComparingInt(candidate -> -candidate.offer().bid().points());
  private static final Comparator<ScoredCandidate> SCORE_ORDER =
      Comparator.comparingDouble((ScoredCandidate candidate) -> candidate.samples().mean())
          .thenComparing(ScoredCandidate::candidate, CANDIDATE_ORDER);

  private final RuleDealMissPolicy dealMiss = new RuleDealMissPolicy();
  private final BiddingHandEvaluator evaluator = new BiddingHandEvaluator();
  private final BidExpectedScoreEvaluator expectedScores = new BidExpectedScoreEvaluator();

  private record Candidate(BidOffer offer, BidAssessment assessment) {

    int highestSuccessScore() {
      int contractCeiling = assessment.ceilingPoints();
      int base = Math.max(0, contractCeiling - 6);
      int multiplier = assessment.trump() == Trump.NO_TRUMP ? 2 : 1;
      if (contractCeiling == Bid.MAX_POINTS) {
        multiplier *= 2;
      }
      return base * multiplier;
    }
  }

  private record ScoredCandidate(Candidate candidate, ScoreSamples samples) {

    BidOffer offer() {
      return candidate.offer();
    }
  }

  @Override
  public BiddingAction choose(BiddingView view) {
    Objects.requireNonNull(view);
    if (dealMiss.shouldDeclare(view)) {
      return mighty.game.bidding.DealMiss.INSTANCE;
    }
    BiddingHand hand = BiddingHand.from(view.hand());
    List<BidOffer> legal = LegalBiddingActions.bidAndPassActions(view.bidding()).stream()
        .filter(BidOffer.class::isInstance).map(BidOffer.class::cast).toList();
    List<BidAssessment> assessments = evaluator.assess(hand).stream()
        .filter(BidAssessment::canCompete).toList();
    List<Candidate> noTrump = candidates(assessments, legal, Trump.NO_TRUMP);
    if (!noTrump.isEmpty()) {
      // 노기루 성공 운영이 보이면 바닥 뒤 기루 전환 선택권을 남기고 우선 비딩합니다.
      return noTrump.stream().max(CANDIDATE_ORDER).orElseThrow().offer();
    }
    List<Candidate> suited = assessments.stream()
        .filter(assessment -> assessment.trump() != Trump.NO_TRUMP)
        .flatMap(assessment -> candidates(List.of(assessment), legal, assessment.trump()).stream())
        .toList();
    if (!suited.isEmpty()) {
      return bestSuitedOffer(hand, suited);
    }
    if (view.bidding().highestBid().isPresent()) {
      return BidPass.INSTANCE;
    }
    // 아무도 입찰하지 않은 첫 차례만 강제로 13을 부릅니다. 경쟁 가능 평가로 재사용하지 않습니다.
    Trump longest = java.util.Arrays.stream(Trump.values()).filter(trump -> trump != Trump.NO_TRUMP)
        .max(Comparator.<Trump>comparingInt(hand::trumpCount)
            .thenComparingInt(trump -> -trump.ordinal())).orElseThrow();
    return new BidOffer(new Bid(Bid.MIN_POINTS, longest));
  }

  private BidOffer bestSuitedOffer(BiddingHand hand, List<Candidate> suited) {
    if (suited.size() == 1) {
      return suited.get(0).offer();
    }
    // 백런의 손실까지 포함한 평균 정산점으로 상위 두 후보를 찾습니다.
    var finalists = suited.stream()
        .map(candidate -> new ScoredCandidate(candidate,
            expectedScores.initialBiddingSamples(hand, candidate.offer().bid())))
        .sorted(SCORE_ORDER.reversed()).limit(2).toList();
    var first = finalists.get(0);
    var second = finalists.get(1);
    if (!first.samples().differenceIsUncertain(second.samples())) {
      return first.offer();
    }
    // 첫 표본의 오차 안에 평균 차이가 있으면 같은 배분을 더 돌려 누적 평균을 비교합니다.
    return finalists.stream()
        .map(finalist -> new ScoredCandidate(finalist.candidate(),
            expectedScores.expandedBiddingSamples(hand, finalist.offer().bid())))
        .max(SCORE_ORDER).orElseThrow().offer();
  }

  private static List<Candidate> candidates(List<BidAssessment> assessments, List<BidOffer> legal,
      Trump trump) {
    return assessments.stream()
        .filter(assessment -> assessment.trump() == trump)
        .flatMap(assessment -> legal.stream()
            .filter(offer -> offer.bid().trump() == assessment.trump())
            .filter(offer -> offer.bid().points() >= assessment.openingPoints()
                && offer.bid().points() <= assessment.ceilingPoints())
            .min(Comparator.comparingInt(offer -> offer.bid().points()))
            .map(offer -> new Candidate(offer, assessment)).stream())
        .toList();
  }
}
