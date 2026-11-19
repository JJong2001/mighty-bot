package mighty.ai.preparation;

import java.util.List;
import mighty.game.observation.DiscardView;
import mighty.model.Card;

/**
 * 주공이 가진 13장 중 버릴 세 장을 선택합니다.
 */
public interface DiscardPolicy {

  List<Card> choose(DiscardView view);
}
