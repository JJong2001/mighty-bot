package mighty.game.rule;

import java.util.Objects;
import mighty.game.TrickNumber;
import mighty.model.Suit;
import mighty.model.Trump;

/**
 * 이미 시작된 한 구에서 카드 강약을 판단하는 조건.
 */
public record TrickConditions(
    Trump trump,
    Suit leadSuit,
    TrickNumber trickNumber,
    boolean jokerCalled
) {

  public TrickConditions {
    Objects.requireNonNull(trump, "기루 설정이 필요합니다.");
    Objects.requireNonNull(leadSuit, "리드 문양이 필요합니다.");
    Objects.requireNonNull(trickNumber, "구 번호가 필요합니다.");
    if (trickNumber.isFirst() && jokerCalled) {
      throw new IllegalArgumentException(
          "첫 구에는 조커콜을 할 수 없습니다."
      );
    }
  }

}
