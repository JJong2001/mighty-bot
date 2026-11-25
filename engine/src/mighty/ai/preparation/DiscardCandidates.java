package mighty.ai.preparation;

import java.util.*;
import mighty.game.observation.DiscardView;
import mighty.model.Card;

/**
 * 버리기 조합을 같은 평가와 안정 정렬 순서로 생성합니다.
 */
final class DiscardCandidates {

  private final RetainedHandEvaluator evaluator = new RetainedHandEvaluator();

  List<DiscardCandidate> ranked(DiscardView view) {
    List<DiscardCandidate> candidates = new ArrayList<>();
    for (int first = 0; first < view.hand().size() - 2; first++) {
      for (int second = first + 1; second < view.hand().size() - 1; second++) {
        for (int third = second + 1; third < view.hand().size(); third++) {
          List<Card> discarded = List.of(view.hand().get(first), view.hand().get(second),
              view.hand().get(third));
          List<Card> kept = new ArrayList<>(view.hand());
          kept.removeAll(discarded);
          var assessment = evaluator.assess(kept, discarded, view.contract().trump());
          candidates.add(new DiscardCandidate(discarded, assessment,
              evaluator.visibleTopCount(kept, view.contract().trump())));
        }
      }
    }
    candidates.sort(Comparator.comparingDouble(
            (DiscardCandidate candidate) -> candidate.assessment().score())
        .thenComparingInt(DiscardCandidate::visibleTops).reversed());
    return candidates;
  }
}
