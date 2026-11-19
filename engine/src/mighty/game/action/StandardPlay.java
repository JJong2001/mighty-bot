package mighty.game.action;

import java.util.Objects;
import mighty.model.StandardCard;

public record StandardPlay(
    StandardCard card,
    boolean call
) implements Action {

  public StandardPlay {
    Objects.requireNonNull(card, "낼 카드가 필요합니다.");
  }

}
