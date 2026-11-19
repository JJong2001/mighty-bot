package mighty.game.observation;

import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import mighty.game.bidding.Bid;
import mighty.game.GameMode;
import mighty.game.PlayOrder;
import mighty.game.Role;
import mighty.game.Seat;
import mighty.game.TableLayout;
import mighty.game.action.StandardPlay;
import mighty.game.deal.DeckFactory;
import mighty.game.friend.NoFriend;
import mighty.game.friend.SpecialFriend;
import mighty.game.trick.PublicPlay;
import mighty.model.Card;
import mighty.model.Rank;
import mighty.model.StandardCard;
import mighty.model.Suit;
import mighty.model.Trump;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 2번 자리가 쉬는 육마에서 관측이 실제 행동 차례에 해당하는지 검사한다.
 */
class ViewTest {

  @Test
  @DisplayName("아직 제출이 없으면 주공의 선 차례 관측을 만들 수 있다")
  void acceptsDeclarerAsOpeningActor() {
    assertDoesNotThrow(() -> view(0, 0, List.of()));
  }

  @Test
  @DisplayName("선 다음 실제 참가자의 관측을 허용한다")
  void acceptsNextParticipantAfterOpeningPlay() {
    assertDoesNotThrow(() -> view(1, 0, List.of(played(0, Rank.TWO))));
  }

  @Test
  @DisplayName("휴식자 2번을 건너뛰고 3번의 행동 차례를 허용한다")
  void acceptsActorAfterSkippingRestingSeat() {
    assertDoesNotThrow(() -> view(3, 0,
        List.of(played(0, Rank.TWO), played(1, Rank.THREE))));
  }

  @Test
  @DisplayName("네 명이 제출한 뒤에는 다섯 번째 참가자의 관측을 허용한다")
  void acceptsLastParticipantInTrick() {
    assertDoesNotThrow(() -> view(5, 0, List.of(
        played(0, Rank.TWO), played(1, Rank.THREE),
        played(3, Rank.FOUR), played(4, Rank.FIVE))));
  }

  @Test
  @DisplayName("5번 자리 선 다음에는 0번 자리의 차례로 돌아간다")
  void wrapsActingTurnAcrossTableBoundary() {
    assertDoesNotThrow(() -> view(0, 5, List.of(played(5, Rank.TWO))));
  }

  @Test
  @DisplayName("휴식자의 플레이 관측을 거부한다")
  void rejectsRestingActor() {
    assertThrows(IllegalArgumentException.class, () -> view(2, 0,
        List.of(played(0, Rank.TWO), played(1, Rank.THREE))));
  }

  @Test
  @DisplayName("참가자라도 현재 차례가 아니면 관측을 거부한다")
  void rejectsActiveParticipantWhoseTurnHasNotCome() {
    assertThrows(IllegalArgumentException.class, () -> view(4, 0,
        List.of(played(0, Rank.TWO), played(1, Rank.THREE))));
  }

  @Test
  @DisplayName("마지막 제출자 다음 자리와 맞아도 앞선 제출 순서가 틀리면 거부한다")
  void rejectsInvalidSequenceEvenWhenActorFollowsLastSubmitter() {
    assertThrows(IllegalArgumentException.class, () -> view(4, 0,
        List.of(played(0, Rank.TWO), played(3, Rank.THREE))));
  }

  @Test
  @DisplayName("첫 제출자가 실제 선과 다르면 관측을 거부한다")
  void rejectsSequenceStartingFromWrongLeader() {
    assertThrows(IllegalArgumentException.class,
        () -> view(3, 0, List.of(played(1, Rank.TWO))));
  }

  /**
   * 차례 검사 외의 조건은 충족하도록 실제 카드가 겹치지 않는 관측을 준비한다.
   */
  private static View view(int selfIndex, int declarerIndex, List<PublicPlay> trick) {
    Seat self = new Seat(selfIndex);
    Seat declarer = new Seat(declarerIndex);
    TableLayout table = new TableLayout(GameMode.SIX_PLAYER,
        IntStream.range(0, 6).mapToObj(Seat::new).toList());
    PlayOrder order = PlayOrder.from(table,
        Set.of(new Seat(0), new Seat(1), new Seat(3), new Seat(4), new Seat(5)));
    Context context = new Context(order, declarer, NoFriend.INSTANCE, List.of());
    Set<Card> submitted = trick.stream().map(play -> play.action().card())
        .collect(java.util.stream.Collectors.toSet());
    List<Card> remaining = DeckFactory.create().stream()
        .filter(card -> !submitted.contains(card)).toList();
    List<Card> hand = remaining.subList(0, 10);
    boolean isDeclarer = self.equals(declarer);
    PrivateInfo privateInfo = isDeclarer
        ? new DeclarerInfo(hand, remaining.subList(10, 13))
        : new NonDeclarerInfo(hand);
    return new View(self, privateInfo,
        isDeclarer ? Role.DECLARER : Role.OPPOSITION,
        new Bid(13, Trump.NO_TRUMP), SpecialFriend.NONE, context, trick);
  }

  private static PublicPlay played(int seat, Rank rank) {
    return new PublicPlay(new Seat(seat),
        new StandardPlay(new StandardCard(Suit.CLUB, rank), false));
  }

}
