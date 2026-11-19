package mighty.game.observation;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import mighty.game.GameMode;
import mighty.game.PlayOrder;
import mighty.game.Seat;
import mighty.game.TableLayout;
import mighty.game.TrickNumber;
import mighty.game.action.StandardPlay;
import mighty.game.friend.NoFriend;
import mighty.game.friend.RevealedFriend;
import mighty.game.friend.UnrevealedFriend;
import mighty.game.trick.PublicPlay;
import mighty.game.trick.PublicTrick;
import mighty.model.Rank;
import mighty.model.StandardCard;
import mighty.model.Suit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 공개 관측 구성의 조건을 검사한다. 과거 대국 전체를 재검산하지 않는다.
 */
class ContextTest {

  @Test
  @DisplayName("육마의 5번 자리도 참가 중이면 주공이나 프렌드가 될 수 있다")
  void acceptsActiveSixPlayerSeats() {
    assertDoesNotThrow(() -> new Context(order(), new Seat(5),
        new RevealedFriend(new Seat(3)), List.of()));
    assertDoesNotThrow(() -> new Context(order(), new Seat(0),
        new RevealedFriend(new Seat(5)), List.of()));
  }

  @Test
  @DisplayName("테이블에 존재해도 휴식자는 주공이 될 수 없다")
  void rejectsRestingDeclarer() {
    assertThrows(IllegalArgumentException.class, () -> new Context(
        order(), new Seat(2), UnrevealedFriend.INSTANCE, List.of()));
  }

  @Test
  @DisplayName("휴식자를 공개 프렌드로 지정할 수 없다")
  void rejectsRestingRevealedFriend() {
    assertThrows(IllegalArgumentException.class, () -> new Context(
        order(), new Seat(0), new RevealedFriend(new Seat(2)), List.of()));
  }

  @Test
  @DisplayName("주공과 공개 프렌드는 같은 자리일 수 없다")
  void rejectsDeclarerAsRevealedFriend() {
    assertThrows(IllegalArgumentException.class, () -> new Context(
        order(), new Seat(0), new RevealedFriend(new Seat(0)), List.of()));
  }

  @Test
  @DisplayName("프렌드 미공개와 프렌드 없음 상태를 모두 표현할 수 있다")
  void acceptsUnrevealedAndNoFriendStates() {
    assertDoesNotThrow(() -> new Context(
        order(), new Seat(0), UnrevealedFriend.INSTANCE, List.of()));
    assertDoesNotThrow(() -> new Context(
        order(), new Seat(0), NoFriend.INSTANCE, List.of()));
  }

  @Test
  @DisplayName("완료 이력이 없으면 주공이 선이고 획득 점수는 0이다")
  void initialContextUsesDeclarerAsLeaderAndZeroCapturedPoints() {
    Context context = new Context(order(), new Seat(5), UnrevealedFriend.INSTANCE, List.of());
    assertEquals(new Seat(5), context.leader());
    for (Seat seat : context.playOrder().seats()) {
      assertEquals(0, context.captured(seat));
    }
  }

  @Test
  @DisplayName("마지막 구 승자와 자리별 점수를 완료 이력에서 계산한다")
  void derivesLeaderAndCapturedPointsFromHistory() {
    Context context = new Context(order(), new Seat(0), UnrevealedFriend.INSTANCE, history());
    assertEquals(new Seat(3), context.leader());
    assertEquals(1, context.captured(new Seat(5)));
    assertEquals(1, context.captured(new Seat(3)));
    assertEquals(0, context.captured(new Seat(0)));
  }

  @Test
  @DisplayName("외부에서 이력을 바꿔도 관측의 선과 점수는 바뀌지 않는다")
  void protectsHistoryFromExternalChanges() {
    List<PublicTrick> original = new ArrayList<>(history());
    Context context = new Context(order(), new Seat(0), UnrevealedFriend.INSTANCE, original);
    original.clear();
    assertEquals(2, context.history().size());
    assertEquals(new Seat(3), context.leader());
    assertEquals(1, context.captured(new Seat(5)));
    assertThrows(UnsupportedOperationException.class, () -> context.history().clear());
  }

  private static PlayOrder order() {
    List<Seat> seats = IntStream.range(0, 6).mapToObj(Seat::new).toList();
    return PlayOrder.from(new TableLayout(GameMode.SIX_PLAYER, seats),
        Set.of(new Seat(0), new Seat(1), new Seat(3), new Seat(4), new Seat(5)));
  }

  private static List<PublicTrick> history() {
    PublicTrick first = new PublicTrick(new TrickNumber(1), List.of(
        played(0, Suit.HEART, Rank.TWO), played(1, Suit.HEART, Rank.THREE),
        played(3, Suit.HEART, Rank.FOUR), played(4, Suit.HEART, Rank.FIVE),
        played(5, Suit.HEART, Rank.TEN)), new Seat(5));
    PublicTrick second = new PublicTrick(new TrickNumber(2), List.of(
        played(5, Suit.CLUB, Rank.TWO), played(0, Suit.CLUB, Rank.THREE),
        played(1, Suit.CLUB, Rank.FOUR), played(3, Suit.CLUB, Rank.TEN),
        played(4, Suit.CLUB, Rank.FIVE)), new Seat(3));
    return List.of(first, second);
  }

  private static PublicPlay played(int seat, Suit suit, Rank rank) {
    return new PublicPlay(new Seat(seat), new StandardPlay(new StandardCard(suit, rank), false));
  }

}
