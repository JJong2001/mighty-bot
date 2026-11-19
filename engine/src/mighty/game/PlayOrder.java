package mighty.game;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public class PlayOrder {

  private final List<Seat> seats;

  private PlayOrder(List<Seat> seats) {
    this.seats = List.copyOf(seats);
  }

  public static PlayOrder from(
      TableLayout table,
      Set<Seat> participants
  ) {
    Objects.requireNonNull(table, "테이블 구성이 필요합니다.");
    Set<Seat> active = Set.copyOf(participants);
    if (active.size() != GameMode.PLAYING_PLAYER_COUNT) {
      throw new IllegalArgumentException(
          "실제 플레이 참가자는 다섯 명이어야 합니다."
      );
    }
    for (Seat seat : active) {
      table.requireSeat(seat);
    }
    List<Seat> orderedSeats = table.seats().stream()
        .filter(active::contains)
        .toList();
    return new PlayOrder(orderedSeats);
  }

  public List<Seat> seats() {
    return seats;
  }

  public Seat nextAfter(Seat seat) {
    Objects.requireNonNull(seat, "현재 자리가 필요합니다.");
    int position = seats.indexOf(seat);
    if (position == -1) {
      throw new IllegalArgumentException(
          "이번 판의 플레이 참가자가 아닙니다: " + seat.index()
      );
    }
    int nextPosition = (position + 1) % seats.size();
    return seats.get(nextPosition);
  }

  public void requireSequence(Seat leader, List<Seat> submittedSeats) {
    Objects.requireNonNull(leader, "선 자리가 필요합니다.");
    List<Seat> submitted = List.copyOf(submittedSeats);
    if (!seats.contains(leader)) {
      throw new IllegalArgumentException(
          "선은 이번 판의 참가자여야 합니다."
      );
    }
    if (submitted.size() > seats.size()) {
      throw new IllegalArgumentException(
          "제출 수가 플레이 참가자 수를 초과했습니다."
      );
    }
    Seat expected = leader;
    for (Seat actual : submitted) {
      if (!actual.equals(expected)) {
        throw new IllegalArgumentException(
            "이번 판의 제출 순서가 일치하지 않습니다."
        );
      }
      expected = nextAfter(expected);
    }
  }

}
