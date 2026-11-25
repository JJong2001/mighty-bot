package mighty.ai.preparation;

import java.util.List;
import mighty.model.Card;

/**
 * 버릴 카드와 그 결과로 남는 패의 평가입니다.
 */
record DiscardCandidate(List<Card> cards, RetainedHandEvaluator.Assessment assessment,
                        int visibleTops) {

}
