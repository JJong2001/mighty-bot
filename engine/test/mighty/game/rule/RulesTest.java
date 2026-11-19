package mighty.game.rule;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import mighty.game.Seat;
import mighty.game.TrickNumber;
import mighty.game.action.JokerFollow;
import mighty.game.action.JokerLead;
import mighty.game.action.StandardPlay;
import mighty.game.trick.PublicPlay;
import mighty.model.Card;
import mighty.model.Joker;
import mighty.model.Rank;
import mighty.model.StandardCard;
import mighty.model.Suit;
import mighty.model.Trump;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 전략 점수와 분리해 실제 카드 강약 및 현재 승자 계산을 검사한다.
 */
class RulesTest {

  @Test
  @DisplayName("스페이드 기루에서만 마이티가 다이아몬드 A로 바뀐다")
  void mightyChangesOnlyForSpadeTrump() {
    assertEquals(new StandardCard(Suit.DIAMOND, Rank.ACE), Rules.mighty(Trump.SPADE));
    for (Trump trump : List.of(Trump.DIAMOND, Trump.HEART, Trump.CLUB, Trump.NO_TRUMP)) {
      assertEquals(new StandardCard(Suit.SPADE, Rank.ACE), Rules.mighty(trump));
    }
  }

  @Test
  @DisplayName("클럽 기루에서만 총이 스페이드 3으로 바뀐다")
  void gunChangesOnlyForClubTrump() {
    assertEquals(new StandardCard(Suit.SPADE, Rank.THREE), Rules.gun(Trump.CLUB));
    for (Trump trump : List.of(Trump.SPADE, Trump.DIAMOND, Trump.HEART, Trump.NO_TRUMP)) {
      assertEquals(new StandardCard(Suit.CLUB, Rank.THREE), Rules.gun(trump));
    }
  }

  @Test
  @DisplayName("마이티는 기루·리드 문양·조커 활성 여부와 관계없이 가장 강하다")
  void mightyBeatsEveryOtherCard() {
    for (Trump trump : Trump.values()) {
      for (Suit lead : Suit.values()) {
        List<TrickConditions> cases = List.of(
            conditions(trump, lead, 1, false),
            conditions(trump, lead, 2, false),
            conditions(trump, lead, 10, false),
            conditions(trump, lead, 2, true)
        );
        Card mighty = Rules.mighty(trump);
        for (TrickConditions context : cases) {
          assertStronger(mighty, Joker.INSTANCE, context);
          for (Suit suit : Suit.values()) {
            for (Rank rank : Rank.values()) {
              Card other = new StandardCard(suit, rank);
              if (!mighty.equals(other)) {
                assertStronger(mighty, other, context);
              }
            }
          }
        }
      }
    }
  }

  @Test
  @DisplayName("정상 조커는 마이티를 제외한 모든 일반 카드보다 강하다")
  void activeJokerBeatsEveryNonMightyOrdinaryCard() {
    for (Trump trump : Trump.values()) {
      TrickConditions context = conditions(trump, Suit.CLUB, 2, false);
      for (Suit suit : Suit.values()) {
        for (Rank rank : Rank.values()) {
          Card other = new StandardCard(suit, rank);
          if (!other.equals(Rules.mighty(trump))) {
            assertStronger(Joker.INSTANCE, other, context);
          }
        }
      }
    }
  }

  @Test
  @DisplayName("첫 구·마지막 구·콜에 무력화된 조커는 모든 일반 카드보다 약하다")
  void inactiveJokerIsWeakerThanEveryOrdinaryCard() {
    for (Trump trump : Trump.values()) {
      List<TrickConditions> cases = List.of(
          conditions(trump, Suit.CLUB, 1, false),
          conditions(trump, Suit.CLUB, 10, false),
          conditions(trump, Suit.CLUB, 2, true)
      );
      for (TrickConditions context : cases) {
        for (Suit suit : Suit.values()) {
          for (Rank rank : Rank.values()) {
            assertStronger(new StandardCard(suit, rank), Joker.INSTANCE, context);
          }
        }
      }
    }
  }

  @Test
  @DisplayName("낮은 기루도 리드 문양의 A를 이긴다")
  void trumpBeatsLedSuitAce() {
    assertStronger(
        new StandardCard(Suit.HEART, Rank.TWO),
        new StandardCard(Suit.CLUB, Rank.ACE),
        conditions(Trump.HEART, Suit.CLUB, 2, false)
    );
  }

  @Test
  @DisplayName("리드 문양의 낮은 카드도 관계없는 문양의 A를 이긴다")
  void ledSuitBeatsUnrelatedAce() {
    assertStronger(
        new StandardCard(Suit.CLUB, Rank.TWO),
        new StandardCard(Suit.DIAMOND, Rank.ACE),
        conditions(Trump.NO_TRUMP, Suit.CLUB, 2, false)
    );
  }

  @Test
  @DisplayName("같은 리드 문양의 일반 카드끼리는 숫자가 높은 카드가 이긴다")
  void rankOrdersCardsWithinLedSuit() {
    assertStronger(
        new StandardCard(Suit.HEART, Rank.KING),
        new StandardCard(Suit.HEART, Rank.QUEEN),
        conditions(Trump.NO_TRUMP, Suit.HEART, 2, false)
    );
  }

  @Test
  @DisplayName("조커 선의 지정 문양이 리드 문양이 된다")
  void jokerLeadSuppliesDeclaredSuit() {
    TrickConditions context = Rules.conditionsFor(
        Trump.DIAMOND, new TrickNumber(2), new JokerLead(Suit.HEART)
    );
    assertEquals(Suit.HEART, context.leadSuit());
    assertFalse(context.jokerCalled());
  }

  @Test
  @DisplayName("잘못된 콜과 조커 따라내기를 선 행동으로 거부한다")
  void rejectsInvalidOpeningActions() {
    assertThrows(IllegalArgumentException.class, () -> Rules.conditionsFor(
        Trump.NO_TRUMP, new TrickNumber(2),
        new StandardPlay(new StandardCard(Suit.CLUB, Rank.FOUR), true)
    ));
    assertThrows(IllegalArgumentException.class, () -> Rules.conditionsFor(
        Trump.NO_TRUMP, new TrickNumber(1),
        new StandardPlay(new StandardCard(Suit.CLUB, Rank.THREE), true)
    ));
    assertThrows(IllegalArgumentException.class, () -> Rules.conditionsFor(
        Trump.NO_TRUMP, new TrickNumber(2), new JokerFollow()
    ));
  }

  @Test
  @DisplayName("선의 마이티는 한 장일 때부터 선두이며 이후 조커에도 유지된다")
  void identifiesMightyWinnerBeforeFivePlays() {
    PublicPlay mighty = played(0, Suit.SPADE, Rank.ACE);
    assertEquals(mighty, Rules.currentWinner(
        Trump.NO_TRUMP, new TrickNumber(2), List.of(mighty)
    ).orElseThrow());
    assertEquals(mighty, Rules.currentWinner(
        Trump.NO_TRUMP, new TrickNumber(2), List.of(
            mighty,
            new PublicPlay(new Seat(1), new JokerFollow()),
            played(2, Suit.SPADE, Rank.TWO)
        )
    ).orElseThrow());
  }

  @Test
  @DisplayName("구 진행 중 더 강한 카드가 나오면 현재 선두가 바뀐다")
  void updatesCurrentWinnerDuringTrick() {
    PublicPlay first = played(0, Suit.HEART, Rank.TWO);
    PublicPlay second = played(1, Suit.HEART, Rank.ACE);
    assertEquals(first, Rules.currentWinner(
        Trump.NO_TRUMP, new TrickNumber(2), List.of(first)
    ).orElseThrow());
    assertEquals(second, Rules.currentWinner(
        Trump.NO_TRUMP, new TrickNumber(2), List.of(first, second)
    ).orElseThrow());
  }

  @Test
  @DisplayName("아무도 내지 않았다면 현재 승자는 없다")
  void emptyTrickHasNoCurrentWinner() {
    assertTrue(Rules.currentWinner(
        Trump.NO_TRUMP, new TrickNumber(2), List.of()
    ).isEmpty());
  }

  private static TrickConditions conditions(Trump trump, Suit lead, int number, boolean called) {
    return new TrickConditions(trump, lead, new TrickNumber(number), called);
  }

  private static PublicPlay played(int seat, Suit suit, Rank rank) {
    return new PublicPlay(new Seat(seat), new StandardPlay(new StandardCard(suit, rank), false));
  }

  /**
   * 구체적인 내부 점수 대신 게임 규칙이 요구하는 강약 관계를 비교한다.
   */
  private static void assertStronger(Card stronger, Card weaker, TrickConditions context) {
    assertTrue(Rules.strength(stronger, context) > Rules.strength(weaker, context),
        () -> stronger.toCode() + " must beat " + weaker.toCode() + " / " + context);
  }

}
