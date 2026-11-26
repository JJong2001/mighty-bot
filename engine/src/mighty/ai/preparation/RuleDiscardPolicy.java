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
    // 같은 저공약의 별도 운영이 가능한 경우에만 받침과 개발패를 보존합니다.
    if (mighty.ai.planning.TumbleNoTrumpPlan.find(mighty.ai.bidding.BiddingHand.from(view.hand()),
        List.of(), view.contract()).isPresent()) {
      var preserving = ranked.stream().filter(candidate -> {
        var kept = new ArrayList<>(view.hand());
        kept.removeAll(candidate.cards());
        return mighty.ai.planning.TumbleNoTrumpPlan.find(mighty.ai.bidding.BiddingHand.from(kept),
            candidate.cards(), view.contract()).isPresent();
      }).toList();
      if (!preserving.isEmpty()) {
        return preserving.stream().limit(limit).map(DiscardCandidate::cards).toList();
      }
    }
    // 전체 열거에서는 시뮬레이션을 생략했던 기존 탐색 범위를 유지합니다.
    if (limit < ranked.size()) {
      comparison.refine(view, ranked);
    }
    return ranked.stream().limit(limit).map(DiscardCandidate::cards).toList();
  }
}
