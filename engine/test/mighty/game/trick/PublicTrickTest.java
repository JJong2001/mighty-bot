package mighty.game.trick;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import mighty.game.Seat;
import mighty.game.TrickNumber;
import mighty.game.action.StandardPlay;
import mighty.model.Rank;
import mighty.model.StandardCard;
import mighty.model.Suit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 완료 기록 자체의 조건만 검사한다. 실제 순서는 PlayOrder가 검사한다.
 */
class PublicTrickTest {

  @Test
  @DisplayName("육마의 5번 자리를 포함한 다섯 제출을 기록한다")
  void acceptsFiveSubmissionsWithSixPlayerSeatIdentity() {
    List<PublicPlay> cards = submissions();
    PublicTrick trick = new PublicTrick(new TrickNumber(2), cards, new Seat(3));
    assertEquals(cards, trick.cards());
    assertEquals(new Seat(4), trick.leader());
    assertEquals(new Seat(3), trick.winner());
    assertEquals(1, trick.points());
  }

  @Test
  @DisplayName("한 사람이 두 번 제출한 기록을 거부한다")
  void rejectsRepeatedSubmitter() {
    List<PublicPlay> cards = new ArrayList<>(submissions());
    cards.set(4, new PublicPlay(new Seat(4), cards.get(4).action()));
    assertThrows(IllegalArgumentException.class,
        () -> new PublicTrick(new TrickNumber(2), cards, new Seat(4)));
  }

  @Test
  @DisplayName("서로 다른 사람이 같은 카드를 낸 기록을 거부한다")
  void rejectsRepeatedCard() {
    List<PublicPlay> cards = new ArrayList<>(submissions());
    cards.set(4, new PublicPlay(new Seat(3), cards.get(0).action()));
    assertThrows(IllegalArgumentException.class,
        () -> new PublicTrick(new TrickNumber(2), cards, new Seat(3)));
  }

  @Test
  @DisplayName("제출하지 않은 사람을 승자로 기록할 수 없다")
  void rejectsWinnerWhoDidNotSubmit() {
    assertThrows(IllegalArgumentException.class,
        () -> new PublicTrick(new TrickNumber(2), submissions(), new Seat(2)));
  }

  @Test
  @DisplayName("다섯 제출이 모이기 전에는 완료 기록을 만들 수 없다")
  void rejectsIncompleteTrick() {
    assertThrows(IllegalArgumentException.class,
        () -> new PublicTrick(new TrickNumber(2), submissions().subList(0, 4), new Seat(4)));
  }

  @Test
  @DisplayName("원본 목록과 반환 목록을 통해 완료 기록을 변경할 수 없다")
  void protectsRecordedSubmissionsFromExternalChanges() {
    List<PublicPlay> original = new ArrayList<>(submissions());
    PublicTrick trick = new PublicTrick(new TrickNumber(2), original, new Seat(3));
    original.clear();
    assertEquals(5, trick.cards().size());
    assertEquals(new Seat(4), trick.leader());
    assertThrows(UnsupportedOperationException.class, () -> trick.cards().clear());
  }

  /**
   * 휴식자 2번을 제외하고 4번 선에서 시작한 한 바퀴.
   */
  private static List<PublicPlay> submissions() {
    return List.of(
        played(4, Rank.TWO), played(5, Rank.THREE), played(0, Rank.FOUR),
        played(1, Rank.FIVE), played(3, Rank.TEN)
    );
  }

  private static PublicPlay played(int seat, Rank rank) {
    return new PublicPlay(new Seat(seat),
        new StandardPlay(new StandardCard(Suit.HEART, rank), false));
  }

}
