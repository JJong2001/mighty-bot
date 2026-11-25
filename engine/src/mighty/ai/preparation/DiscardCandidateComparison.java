package mighty.ai.preparation;

import java.util.*;
import mighty.ai.evaluation.PreparedRoundEvaluator;
import mighty.game.*;
import mighty.game.bidding.PublicBid;
import mighty.game.observation.*;
import mighty.model.Card;

/**
 * 제한된 후보 탐색에서 탑 보존과 문양 개발을 기존 방식으로 한 번 비교합니다.
 */
final class DiscardCandidateComparison {

  private final PreparedRoundEvaluator expectedScores = new PreparedRoundEvaluator();
  private final RuleFriendPolicy friends = new RuleFriendPolicy();

  void refine(DiscardView view, List<DiscardCandidate> candidates) {
    var first = candidates.get(0);
    var base = first.assessment();
    for (int index = 1; index < candidates.size(); index++) {
      var alternative = candidates.get(index);
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
      int comparison = Comparator.comparingDouble(PreparedRoundEvaluator.Score::mean)
          .thenComparingInt(PreparedRoundEvaluator.Score::wins)
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

  private PreparedRoundEvaluator.Score estimate(DiscardView view, List<Card> discarded) {
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
    return expectedScores.evaluate(view.contract(), view.hand(), plan, auction);
  }

}
