package mighty.model;

import java.util.Locale;
import java.util.Objects;

public enum Rank {
  TWO(2, "2"),
  THREE(3, "3"),
  FOUR(4, "4"),
  FIVE(5, "5"),
  SIX(6, "6"),
  SEVEN(7, "7"),
  EIGHT(8, "8"),
  NINE(9, "9"),
  TEN(10, "10"),
  JACK(11, "J"),
  QUEEN(12, "Q"),
  KING(13, "K"),
  ACE(14, "A");
  private final int value;
  private final String code;

  Rank(int value, String code) {
    this.value = value;
    this.code = code;
  }

  public int value() {
    return value;
  }

  public String code() {
    return code;
  }

  public static Rank parse(String input) {
    Objects.requireNonNull(input, "카드 숫자 입력이 필요합니다.");
    String text = input.strip().toUpperCase(Locale.ROOT);
    if (text.equals("T")) {
      return TEN;
    }
    for (Rank rank : values()) {
      if (rank.code.equals(text)) {
        return rank;
      }
    }
    throw new IllegalArgumentException(
        "잘못된 카드 숫자: " + input
    );
  }

}
