package mighty.model;

public enum Suit {
  SPADE('S', '♠'),
  DIAMOND('D', '♦'),
  HEART('H', '♥'),
  CLUB('C', '♣');
  private final char code;
  private final char symbol;

  Suit(char code, char symbol) {
    this.code = code;
    this.symbol = symbol;
  }

  public char code() {
    return code;
  }

  public char symbol() {
    return symbol;
  }

  public static Suit parse(char value) {
    return switch (Character.toUpperCase(value)) {
      case 'S', '♠' -> SPADE;
      case 'D', '♦' -> DIAMOND;
      case 'H', '♥' -> HEART;
      case 'C', '♣' -> CLUB;
      default -> throw new IllegalArgumentException(
          "잘못된 문양: " + value
      );
    };
  }

}
