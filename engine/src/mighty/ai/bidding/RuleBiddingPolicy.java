package mighty.ai.bidding;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
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

  private final RuleDealMissPolicy dealMiss = new RuleDealMissPolicy();
  private final BiddingHandEvaluator evaluator = new BiddingHandEvaluator();

  private record Candidate(BidOffer offer, BidAssessment assessment) {

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
    List<Candidate> candidates = evaluator.assess(hand).stream()
        .filter(BidAssessment::canCompete)
        .flatMap(assessment -> legal.stream()
            .filter(offer -> offer.bid().trump() == assessment.trump())
            .filter(offer -> offer.bid().points() >= assessment.openingPoints()
                && offer.bid().points() <= assessment.ceilingPoints())
            .min(Comparator.comparingInt(offer -> offer.bid().points()))
            .map(offer -> new Candidate(offer, assessment)).stream()).toList();
    if (!candidates.isEmpty()) {
      return candidates.stream().max(Comparator
              .<Candidate>comparingInt(candidate -> candidate.assessment().preference())
              .thenComparingInt(candidate -> -candidate.offer().bid().points()))
          .orElseThrow().offer();
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

}
