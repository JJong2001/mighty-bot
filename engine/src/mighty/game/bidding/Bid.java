package mighty.game.bidding;

import java.util.Objects;
import mighty.model.Trump;

public record Bid(int points, Trump trump) {

  public static final int MIN_POINTS = 13;
  public static final int MAX_POINTS = 20;

  public Bid {
    Objects.requireNonNull(trump, "기루 설정이 필요합니다.");
    if (points < MIN_POINTS || points > MAX_POINTS) {
      throw new IllegalArgumentException(
          "공약은 " + MIN_POINTS + " ~ " + MAX_POINTS
              + " 사이여야 합니다: " + points
      );
    }
  }

}
