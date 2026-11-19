package mighty.ai.play;

import mighty.game.observation.View;
import mighty.model.Suit;

/**
 * 역할별 기존 운영 조회를 같은 공개 문양 추론으로 연결합니다.
 */
public final class DeclarerOperations {

  private DeclarerOperations() {
  }

  public static boolean contains(View view, PlayFacts facts, Suit suit) {
    return facts.declarerSuits().assess(suit).operating();
  }
}
