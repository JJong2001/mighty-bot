package mighty.ai.planning;

import mighty.model.Card;
import mighty.model.Suit;

/**
 * 주력·프렌드·호출 시점의 운영 자료입니다. 공약 상한은 조건부 배분 평가에서만 정합니다.
 */
public record NoTrumpPlan(
    boolean available,
    Suit primary,
    Card friend,
    int jokerBreaks,
    int ownWinsBeforeSupport
) {

  public boolean canDelaySupport() {
    return available && jokerBreaks == 0 && ownWinsBeforeSupport >= 8;
  }

}
