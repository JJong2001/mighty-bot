package mighty.game.rule;

import java.util.List;
import java.util.Objects;
import mighty.game.GameMode;
import mighty.game.TrickNumber;
import mighty.game.trick.PublicPlay;
import mighty.model.Card;
import mighty.model.Trump;

public record LegalActionContext(
    List<Card> hand,
    Trump trump,
    TrickNumber trickNumber,
    List<PublicPlay> trick
) {

  public LegalActionContext {
    hand = List.copyOf(hand);
    trick = List.copyOf(trick);
    Objects.requireNonNull(trump, "기루 설정이 필요합니다.");
    Objects.requireNonNull(trickNumber, "구 번호가 필요합니다.");
    if (hand.isEmpty()) {
      throw new IllegalArgumentException(
          "행동할 사람의 손패가 비어 있습니다."
      );
    }
    if (trick.size() >= GameMode.PLAYING_PLAYER_COUNT) {
      throw new IllegalArgumentException(
          "완료된 구에서는 제출 행동을 계산할 수 없습니다."
      );
    }
  }

}
