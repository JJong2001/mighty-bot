package mighty.game;

public record TrickNumber(int value) {

  public static final int FIRST = 1;
  public static final int LAST = 10;

  public TrickNumber {
    if (value < FIRST || value > LAST) {
      throw new IllegalArgumentException(
          "구 번호는 1 ~ 10이어야 합니다: " + value
      );
    }
  }

  public boolean isFirst() {
    return value == FIRST;
  }

  public boolean isLast() {
    return value == LAST;
  }

}
