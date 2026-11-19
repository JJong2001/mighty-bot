package mighty.model;

import java.util.Objects;

public enum Trump {
  SPADE,
  DIAMOND,
  HEART,
  CLUB,
  NO_TRUMP;

  public boolean matches(Suit suit) {
    Objects.requireNonNull(suit, "비교할 문양이 필요합니다.");
    return switch (this) {
      case SPADE -> suit == Suit.SPADE;
      case DIAMOND -> suit == Suit.DIAMOND;
      case HEART -> suit == Suit.HEART;
      case CLUB -> suit == Suit.CLUB;
      case NO_TRUMP -> false;
    };
  }

}
