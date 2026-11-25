package mighty.ai.preparation;

import java.util.*;
import mighty.game.observation.DiscardView;
import mighty.model.Card;

/**
 * 버리기 후보 생성과 제한 탐색의 비교를 연결합니다. 공약 비교는 PreparationPlanner가 담당합니다.
 */
public final class RuleDiscardPolicy implements DiscardPolicy {

  private final DiscardCandidates candidates = new DiscardCandidates();
  private final DiscardCandidateComparison comparison = new DiscardCandidateComparison();

  @Override
  public List<Card> choose(DiscardView view) {
    return ranked(view, 1).get(0);
  }

  List<List<Card>> ranked(DiscardView view, int limit) {
    Objects.requireNonNull(view);
    var ranked = candidates.ranked(view);
    // 전체 열거에서는 시뮬레이션을 생략했던 기존 탐색 범위를 유지합니다.
    if (limit < ranked.size()) {
      comparison.refine(view, ranked);
    }
    return ranked.stream().limit(limit).map(DiscardCandidate::cards).toList();
  }
}
