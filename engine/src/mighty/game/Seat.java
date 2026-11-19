package mighty.game;

public record Seat(int index) {

  public Seat {
    if (index < 0) {
      throw new IllegalArgumentException(
          "자리는 번호는 음수일 수 없습니다: " + index
      );
    }
  }

}
