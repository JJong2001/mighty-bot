package mighty.ai.preparation;

import java.util.*;
import mighty.ai.bidding.BidExpectedScoreEvaluator;
import mighty.game.GameMode;
import mighty.game.Seat;
import mighty.game.bidding.PublicBid;
import mighty.game.observation.DiscardView;
import mighty.game.observation.FriendView;
import mighty.model.Card;

/**
 * 같은 운영 평가로 286가지 버리기를 비교합니다. 공약 후보 비교는 PreparationPlanner가 담당합니다.
 */
public final class RuleDiscardPolicy implements DiscardPolicy {

  private final RetainedHandEvaluator evaluator = new RetainedHandEvaluator();
  private final BidExpectedScoreEvaluator expectedScores = new BidExpectedScoreEvaluator();
  private final RuleFriendPolicy friends = new RuleFriendPolicy();

  @Override
  public List<Card> choose(DiscardView view) {
    return ranked(view, 1).get(0);
  }

  List<List<Card>> ranked(DiscardView view, int limit) {
    Objects.requireNonNull(view);
    List<ScoredDiscard> candidates = new ArrayList<>();
    for (int first = 0; first < view.hand().size() - 2; first++) {
      for (int second = first + 1; second < view.hand().size() - 1; second++) {
        for (int third = second + 1; third < view.hand().size(); third++) {
          List<Card> discarded = List.of(view.hand().get(first), view.hand().get(second),
              view.hand().get(third));
          List<Card> kept = new ArrayList<>(view.hand());
          kept.removeAll(discarded);
          var assessment = evaluator.assess(kept, discarded, view.contract().trump());
          candidates.add(new ScoredDiscard(discarded, assessment,
              evaluator.visibleTopCount(kept, view.contract().trump())));
        }
      }
    }
    candidates.sort(Comparator.comparingDouble(
            (ScoredDiscard candidate) -> candidate.assessment().score())
        .thenComparingInt(ScoredDiscard::visibleTops).reversed());
    if (limit < candidates.size()) {
      compareDevelopmentWithTops(view, candidates);
    }
    return candidates.stream().limit(limit).map(ScoredDiscard::cards).toList();
  }

  private void compareDevelopmentWithTops(DiscardView view, List<ScoredDiscard> candidates) {
    var first = candidates.get(0);
    var base = first.assessment();
    for (var alternative : candidates.subList(1, candidates.size())) {
      var other = alternative.assessment();
      if (!base.sameControls(other)) {
        continue;
      }
      boolean preservesTops = other.topCards() > base.topCards()
          && other.development() < base.development();
      boolean developsInstead = other.topCards() < base.topCards()
          && other.development() > base.development()
          && base.score() - other.score() <= RetainedHandEvaluator.TOP_RUN_VALUE;
      if (!preservesTops && !developsInstead) {
        continue;
      }
      // 같은 특수카드·기루 자원에서 탑 보존과 문양 개발이 맞설 때만 동일한 배분으로 비교합니다.
      var firstScore = estimate(view, first.cards());
      var otherScore = estimate(view, alternative.cards());
      int comparison = Comparator.comparingDouble(BidExpectedScoreEvaluator.PreparedScore::mean)
          .thenComparingInt(BidExpectedScoreEvaluator.PreparedScore::wins)
          .compare(otherScore, firstScore);
      var winner = comparison > 0 || comparison == 0 && preservesTops
          ? alternative : first;
      candidates.remove(winner == first ? alternative : first);
      if (winner != first) {
        candidates.remove(winner);
        candidates.add(0, winner);
      }
      break;
    }
  }

  private BidExpectedScoreEvaluator.PreparedScore estimate(DiscardView view, List<Card> discarded) {
    var kept = new ArrayList<>(view.hand());
    kept.removeAll(discarded);
    var selection = friends.choose(new FriendView(view.self(), view.contract(), kept, discarded,
        List.of(view.contract()), view.auction()));
    var plan = new PreparationPlan(view.contract(), discarded, selection, "");
    // 평가 대국은 주공을 0번에 둡니다. 오마 공개 입찰도 같은 자리 순서로 옮깁니다.
    int self = view.self().index();
    int seats = GameMode.PLAYING_PLAYER_COUNT;
    var auction = self < seats && view.auction().stream().allMatch(b -> b.seat().index() < seats)
        ? view.auction().stream().map(b -> new PublicBid(
            new Seat(Math.floorMod(b.seat().index() - self, seats)), b.action())).toList()
        : List.<PublicBid>of();
    return expectedScores.preparedScore(view.contract(), view.hand(), plan, auction);
  }

  private record ScoredDiscard(List<Card> cards, RetainedHandEvaluator.Assessment assessment,
                               int visibleTops) {

  }

}
