package mighty.game.deal;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import mighty.game.Seat;
import mighty.game.TableLayout;

public record DealParticipants(
    DealFormat format,
    List<Seat> seats
) {

  public DealParticipants {
    Objects.requireNonNull(format, "배분 형식이 필요합니다.");
    seats = List.copyOf(seats);
    if (seats.size() != format.participantCount()) {
      throw new IllegalArgumentException(
          "배분 형식과 참가자 수가 일치하지 않습니다."
      );
    }
    if (Set.copyOf(seats).size() != seats.size()) {
      throw new IllegalArgumentException(
          "배분 참가자에 중복된 자리가 있습니다."
      );
    }
  }

  public static DealParticipants from(
      TableLayout table,
      DealFormat format,
      Set<Seat> participants
  ) {
    Objects.requireNonNull(table, "테이블 형식이 필요합니다.");
    Objects.requireNonNull(format, "배분 형식이 필요합니다.");
    Set<Seat> selected = Set.copyOf(participants);
    for (Seat seat : selected) {
      table.requireSeat(seat);
    }
    List<Seat> orderedSeats = table.seats().stream()
        .filter(selected::contains)
        .toList();
    return new DealParticipants(format, orderedSeats);
  }

}
