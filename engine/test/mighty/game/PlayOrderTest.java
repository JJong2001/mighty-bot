package mighty.game;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 테이블 전체 자리와 실제 다섯 명의 플레이 순서를 구분해 검사한다.
 */
class PlayOrderTest {

  @Test
  @DisplayName("오마는 테이블의 다섯 자리 모두 순서에 포함한다")
  void fivePlayerModeIncludesAllSeats() {
    TableLayout table = table(GameMode.FIVE_PLAYER);
    PlayOrder order = PlayOrder.from(table, Set.copyOf(table.seats()));
    assertEquals(5, order.seats().size());
    assertEquals(table.seats(), order.seats());
  }

  @Test
  @DisplayName("육마는 휴식자를 제외하고 다섯 자리만 포함한다")
  void sixPlayerModeExcludesRestingSeat() {
    PlayOrder order = sixPlayerOrder();
    assertEquals(seats(0, 1, 3, 4, 5), order.seats());
  }

  @Test
  @DisplayName("마지막 참가자의 다음 차례는 첫 참가자다")
  void wrapsFromLastParticipantToFirst() {
    PlayOrder order = sixPlayerOrder();
    assertEquals(new Seat(0), order.nextAfter(new Seat(5)));
  }

  @Test
  @DisplayName("다음 차례를 계산할 때 휴식자 자리를 건너뛴다")
  void skipsRestingSeat() {
    assertEquals(new Seat(3), sixPlayerOrder().nextAfter(new Seat(1)));
  }

  @Test
  @DisplayName("테이블 밖의 자리를 참가자로 지정할 수 없다")
  void rejectsParticipantOutsideTable() {
    assertThrows(IllegalArgumentException.class, () -> PlayOrder.from(
        table(GameMode.SIX_PLAYER), Set.copyOf(seats(0, 1, 2, 3, 9))
    ));
  }

  @Test
  @DisplayName("실제 참가자가 다섯 명이 아니면 거부한다")
  void rejectsWrongParticipantCount() {
    TableLayout table = table(GameMode.SIX_PLAYER);
    assertThrows(IllegalArgumentException.class, () -> PlayOrder.from(
        table, Set.copyOf(seats(0, 1, 2, 3))
    ));
    assertThrows(IllegalArgumentException.class, () -> PlayOrder.from(
        table, Set.copyOf(table.seats())
    ));
  }

  @Test
  @DisplayName("휴식자나 테이블 밖 자리의 다음 차례를 조회할 수 없다")
  void rejectsNextTurnForNonParticipant() {
    PlayOrder order = sixPlayerOrder();
    assertThrows(IllegalArgumentException.class, () -> order.nextAfter(new Seat(2)));
    assertThrows(IllegalArgumentException.class, () -> order.nextAfter(new Seat(9)));
  }

  @Test
  @DisplayName("입력 집합과 반환 목록을 통해 참가 순서를 변경할 수 없다")
  void protectsParticipantOrderFromExternalChanges() {
    Set<Seat> participants = new HashSet<>(seats(0, 1, 3, 4, 5));
    PlayOrder order = PlayOrder.from(table(GameMode.SIX_PLAYER), participants);
    participants.clear();
    assertEquals(seats(0, 1, 3, 4, 5), order.seats());
    assertThrows(UnsupportedOperationException.class, () -> order.seats().clear());
  }

  @Test
  @DisplayName("자리 번호순이 아니라 테이블에 지정한 방향과 순서를 따른다")
  void followsTableOrderInsteadOfNumericOrder() {
    TableLayout table = new TableLayout(GameMode.SIX_PLAYER, seats(0, 2, 4, 1, 3, 5));
    PlayOrder order = PlayOrder.from(table, Set.copyOf(seats(0, 1, 2, 3, 5)));
    assertEquals(seats(0, 2, 1, 3, 5), order.seats());
    assertEquals(new Seat(1), order.nextAfter(new Seat(2)));
  }

  @Test
  @DisplayName("진행 중인 일부 제출도 휴식자를 건너뛴 순서로 검사한다")
  void acceptsPartialSequenceSkippingRestingSeat() {
    assertDoesNotThrow(() -> sixPlayerOrder().requireSequence(new Seat(1), seats(1, 3, 4)));
  }

  @Test
  @DisplayName("선이 중간 자리여도 한 바퀴 도는 다섯 제출을 허용한다")
  void acceptsCompleteSequenceWrappingAroundTable() {
    assertDoesNotThrow(() -> sixPlayerOrder().requireSequence(new Seat(4), seats(4, 5, 0, 1, 3)));
  }

  @Test
  @DisplayName("참가자가 선이면 아직 제출이 없는 상태도 허용한다")
  void acceptsEmptySequenceForParticipatingLeader() {
    assertDoesNotThrow(() -> sixPlayerOrder().requireSequence(new Seat(0), List.of()));
  }

  @Test
  @DisplayName("참가자를 건너뛰고 제출한 순서는 거부한다")
  void rejectsSkippedActiveParticipant() {
    assertThrows(IllegalArgumentException.class,
        () -> sixPlayerOrder().requireSequence(new Seat(0), seats(0, 3)));
  }

  @Test
  @DisplayName("휴식자가 제출 목록에 섞이면 거부한다")
  void rejectsRestingSeatInsideSequence() {
    assertThrows(IllegalArgumentException.class,
        () -> sixPlayerOrder().requireSequence(new Seat(0), seats(0, 1, 2)));
  }

  @Test
  @DisplayName("첫 제출자가 지정된 선과 다르면 거부한다")
  void rejectsSequenceStartingFromWrongSeat() {
    assertThrows(IllegalArgumentException.class,
        () -> sixPlayerOrder().requireSequence(new Seat(0), seats(1, 3)));
  }

  @Test
  @DisplayName("제출이 없어도 휴식자를 선으로 지정할 수 없다")
  void rejectsRestingLeaderEvenWithoutSubmissions() {
    assertThrows(IllegalArgumentException.class,
        () -> sixPlayerOrder().requireSequence(new Seat(2), List.of()));
  }

  @Test
  @DisplayName("한 구에서 여섯 번째 제출은 허용하지 않는다")
  void rejectsMoreThanFiveSubmissions() {
    assertThrows(IllegalArgumentException.class,
        () -> sixPlayerOrder().requireSequence(new Seat(0), seats(0, 1, 3, 4, 5, 0)));
  }

  private static PlayOrder sixPlayerOrder() {
    return PlayOrder.from(table(GameMode.SIX_PLAYER), Set.copyOf(seats(0, 1, 3, 4, 5)));
  }

  private static TableLayout table(GameMode mode) {
    return new TableLayout(mode, IntStream.range(0, mode.tablePlayerCount())
        .mapToObj(Seat::new).toList());
  }

  private static List<Seat> seats(int... indexes) {
    return IntStream.of(indexes).mapToObj(Seat::new).toList();
  }

}
