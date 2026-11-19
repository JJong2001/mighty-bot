package mighty.ai.preparation;

import java.util.*;
import mighty.game.observation.DiscardView;
import mighty.model.Card;

/**
 * 같은 운영 평가로 286가지 버리기를 비교합니다. 공약 후보 비교는 PreparationPlanner가 담당합니다.
 */
public final class RuleDiscardPolicy implements DiscardPolicy {

  private final RetainedHandEvaluator evaluator = new RetainedHandEvaluator();

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
          double score = evaluator.score(kept, discarded, view.contract().trump());
          candidates.add(new ScoredDiscard(discarded, score));
        }
      }
    }
    return candidates.stream().sorted(Comparator.comparingDouble(ScoredDiscard::score).reversed())
        .limit(limit).map(ScoredDiscard::cards).toList();
  }

  private record ScoredDiscard(List<Card> cards, double score) {

  }

}
