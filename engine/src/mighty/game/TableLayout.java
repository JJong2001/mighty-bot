package mighty.game;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public record TableLayout(
    GameMode mode,
    List<Seat> seats
) {

  public TableLayout {
    Objects.requireNonNull(mode, "대국 방식이 필요합니다.");
    seats = List.copyOf(seats);
    if (seats.size() != mode.tablePlayerCount()) {
      throw new IllegalArgumentException(
          "대국 방식과 테이블 자리 수가 일치하지 않습니다."
      );
    }
    if (new HashSet<>(seats).size() != seats.size()) {
      throw new IllegalArgumentException(
          "테이블에 중복된 자리가 있습니다."
      );
    }
  }

  public void requireSeat(Seat seat) {
    Objects.requireNonNull(seat, "확인할 자리가 필요합니다.");
    if (!seats.contains(seat)) {
      throw new IllegalArgumentException(
          "테이블에 존재하지 않는 자리입니다: " + seat.index()
      );
    }
  }

}
