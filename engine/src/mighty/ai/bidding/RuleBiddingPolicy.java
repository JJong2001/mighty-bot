package mighty.ai.bidding;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;
import mighty.ai.bidding.BidExpectedScoreEvaluator.ScoreSamples;
import mighty.ai.planning.HighNoTrumpPlan;
import mighty.ai.planning.SpreadNoTrumpPlan;
import mighty.game.bidding.Bid;
import mighty.game.bidding.BidOffer;
import mighty.game.bidding.BidPass;
import mighty.game.bidding.BiddingAction;
import mighty.game.bidding.LegalBiddingActions;
import mighty.game.bidding.PublicBid;
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
    var assessments = evaluator.assess(hand);
    var contenders = assessments.stream().filter(BidAssessment::canCompete)
        .flatMap(a -> candidates(a, legal)).toList();
    var standing = view.bidding().highestBid();
    var publicRival = standing.filter(entry -> !entry.seat().equals(view.self()))
        .filter(entry -> ((BidOffer) entry.action()).bid().points() >= 16
            && ((BidOffer) entry.action()).bid().trump() != Trump.NO_TRUMP);
    var exploration = standing.isEmpty() || publicRival.isPresent()
        ? exploreAboveCeiling(hand, assessments, legal, publicRival) : List.<BidOffer>of();
    var alternatives = new LinkedHashSet<>(contenders.stream().map(Candidate::offer).toList());
    alternatives.addAll(exploration);
    var offers = List.copyOf(alternatives);
    var special = ContestedSuitAuction.specialCompetition(view, hand, legal, offers);
    if (special.isPresent()) {
      return special.get().choice();
    }
    var regularOffers = contenders.stream().map(Candidate::offer).toList();
    var contested = ContestedSuitAuction.signal(view, hand, regularOffers);
    if (contested.isPresent()) {
      return ContestedSuitAuction.evaluate(view, contested.get(), offers).choice();
    }
    var usual = ordinaryChoice(view, hand, legal, contenders);
    if (exploration.isEmpty()) {
      return usual;
    }
    if (standing.isEmpty() && usual instanceof BidOffer opening) {
      return compareOpeningChallenge(hand, opening, exploration);
    }
    if (publicRival.isEmpty() || preservesEstablishedFriendPass(view, hand, usual)) {
      return usual;
    }
    if (usual instanceof BidOffer offer) {
      alternatives.add(offer);
    }
    var comparison = ContestedSuitAuction.evaluate(view, publicRival.orElseThrow(),
        List.copyOf(alternatives));
    // 추가 탐색 자체 때문에 이미 상한 안에서 고른 공약을 다른 기본 공약으로 바꾸지 않습니다.
    return exploration.contains(comparison.choice()) ? comparison.choice() : usual;
  }

  private static boolean preservesEstablishedFriendPass(BiddingView view, BiddingHand hand,
      BiddingAction usual) {
    if (usual != BidPass.INSTANCE) {
      return false;
    }
    var route = HighNoTrumpPlan.find(hand).filter(plan -> plan.competitiveShape(hand));
    if (route.isEmpty()) {
      return false;
    }
    var standing = ((BidOffer) view.bidding().highestBid().orElseThrow().action()).bid();
    return !route.get().joker() && hand.hasMighty(standing.trump())
        || route.get().joker() && !route.get().mighty() && standing.points() >= 18;
  }

  private BiddingAction ordinaryChoice(BiddingView view, BiddingHand hand, List<BidOffer> legal,
      List<Candidate> contenders) {
    var route = HighNoTrumpPlan.find(hand);
    if (route.isPresent() && view.hand().size() == 10 && route.get().competitiveShape(hand)) {
      var plan = route.get();
      Bid standing = view.bidding().highestBid()
          .map(entry -> ((BidOffer) entry.action()).bid()).orElse(null);
      boolean competing = standing != null && standing.points() >= 16;
      if (competing && !plan.joker() && hand.hasMighty(standing.trump())
          && standing.trump() != Trump.NO_TRUMP) {
        return BidPass.INSTANCE;
      }
      if (!competing && plan.hasTwoSources(hand)) {
        return new BidOffer(new Bid(16, plan.suited()));
      }
      int ceiling = SpreadNoTrumpPlan.available(hand) ? 17 : plan.competitiveCeiling();
      boolean signalled = plan.hasTwoSources(hand) && view.bidding().history().stream()
          .anyMatch(entry -> entry.seat().equals(view.self()) && entry.action() instanceof BidOffer offer
              && offer.bid().equals(new Bid(16, plan.suited())));
      for (int points = signalled ? 17 : 16; points <= ceiling; points++) {
        var offer = new BidOffer(new Bid(points, Trump.NO_TRUMP));
        if (legal.contains(offer)) {
          return offer;
        }
      }
      return BidPass.INSTANCE;
    }
    var noTrump = contenders.stream()
        .filter(candidate -> candidate.offer().bid().trump() == Trump.NO_TRUMP).toList();
    if (!noTrump.isEmpty() && !noTrump.get(0).assessment().compareWithSuitedBids()) {
      // 노기루 성공 운영이 보이면 바닥 뒤 기루 전환 선택권을 남기고 우선 비딩합니다.
      return noTrump.stream().max(CANDIDATE_ORDER).orElseThrow().offer();
    }
    if (!contenders.isEmpty()) {
      return bestScoringOffer(hand, contenders);
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

  static List<BidOffer> exploreAboveCeiling(BiddingHand hand, List<BidAssessment> assessments,
      List<BidOffer> legal, Optional<PublicBid> rival) {
    if (java.util.Arrays.stream(mighty.model.Suit.values())
        .mapToInt(suit -> hand.holding(suit).size()).sum() + (hand.hasJoker() ? 1 : 0) != 10) {
      return List.of();
    }
    int standing = rival.map(entry -> ((BidOffer) entry.action()).bid().points()).orElse(0);
    var ranked = assessments.stream().filter(BidAssessment::canCompete)
        .filter(a -> a.ceilingPoints() >= 15 && a.ceilingPoints() < Bid.MAX_POINTS)
        .filter(a -> standing == 0 || standing >= a.ceilingPoints())
        .filter(a -> a.trump() == Trump.NO_TRUMP
            ? FlexibleNoTrumpOpening.ordinaryWins(hand) >= 4
                && (hand.hasMighty(Trump.NO_TRUMP) || hand.hasJoker())
            : hand.trumpCount(a.trump()) >= 5
                && CandidateResources.from(hand, a.trump()).hasSideOpening())
        .sorted(Comparator.comparingInt(BidAssessment::preference).reversed()).toList();
    // 문양 하나와 노기루 하나만, 각 기존 상한의 바로 윗 숫자를 시험합니다.
    var suited = ranked.stream().filter(a -> a.trump() != Trump.NO_TRUMP)
        .map(a -> new BidOffer(new Bid(a.ceilingPoints() + 1, a.trump())))
        .filter(legal::contains).findFirst();
    var noTrump = ranked.stream().filter(a -> a.trump() == Trump.NO_TRUMP)
        .map(a -> new BidOffer(new Bid(a.ceilingPoints() + 1, a.trump())))
        .filter(legal::contains).findFirst();
    return Stream.concat(suited.stream(), noTrump.stream()).toList();
  }

  private BidOffer compareOpeningChallenge(BiddingHand hand, BidOffer opening,
      List<BidOffer> exploration) {
    var nearby = exploration.stream().filter(offer -> offer.bid().points() >= opening.bid().points()
        && offer.bid().points() <= opening.bid().points() + 1).toList();
    if (nearby.isEmpty()) {
      return opening;
    }
    double baseline = expectedScores.expectedDeclarerScore(hand, opening.bid());
    var challenger = nearby.stream()
        .map(offer -> Map.entry(offer, expectedScores.expectedDeclarerScore(hand, offer.bid())))
        .filter(entry -> entry.getValue() > baseline)
        .max(Map.Entry.comparingByValue());
    if (challenger.isEmpty()) {
      return opening;
    }
    var offer = challenger.orElseThrow().getKey();
    var base = expectedScores.initialBiddingSamples(hand, opening.bid());
    var trial = expectedScores.initialBiddingSamples(hand, offer.bid());
    // 첫 입찰의 도전은 짝지은 비교에서 이득이 뚜렷해야 채택합니다.
    return trial.mean() > base.mean() && !trial.differenceIsUncertain(base) ? offer : opening;
  }

  private BidOffer bestScoringOffer(BiddingHand hand, List<Candidate> contenders) {
    if (contenders.size() == 1) {
      return contenders.get(0).offer();
    }
    // 백런의 손실까지 포함한 평균 정산점으로 상위 두 후보를 찾습니다.
    var finalists = contenders.stream()
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

  private static Stream<Candidate> candidates(BidAssessment assessment, List<BidOffer> legal) {
    var first = candidate(assessment, legal);
    // 곱노를 도전 후보로 표시한 엿노 패에 한해, 같은 문양 안에서도 두 공약의 손익을 비교합니다.
    if (assessment.trump() == Trump.NO_TRUMP && assessment.compareWithSuitedBids()
        && assessment.openingPoints() == 16 && assessment.ceilingPoints() == 17
        && first.filter(candidate -> candidate.offer().bid().points() == 16).isPresent()) {
      var challenge = new BidOffer(new Bid(17, Trump.NO_TRUMP));
      if (legal.contains(challenge)) {
        return Stream.concat(first.stream(), Stream.of(new Candidate(challenge, assessment)));
      }
    }
    return first.stream();
  }

  private static Optional<Candidate> candidate(BidAssessment assessment, List<BidOffer> legal) {
    return legal.stream().filter(offer -> offer.bid().trump() == assessment.trump())
        .filter(offer -> offer.bid().points() >= assessment.openingPoints()
            && offer.bid().points() <= assessment.ceilingPoints())
        .min(Comparator.comparingInt(offer -> offer.bid().points()))
        .map(offer -> new Candidate(offer, assessment));
  }
}
