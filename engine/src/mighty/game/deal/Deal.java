package mighty.game.deal;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import mighty.game.Seat;
import mighty.model.Card;

public record Deal(
    Map<Seat, List<Card>> hands,
    List<Card> kitty
) {

  public static final int HAND_SIZE = 10;
  public static final int KITTY_SIZE = 3;

  public Deal {
    Objects.requireNonNull(hands, "자리별 손패가 필요합니다.");
  }

}
