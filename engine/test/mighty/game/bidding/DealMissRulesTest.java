package mighty.game.bidding;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import mighty.game.deal.DealFormat;
import mighty.model.Card;
import mighty.model.Joker;
import mighty.model.Rank;
import mighty.model.StandardCard;
import mighty.model.Suit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class DealMissRulesTest {

  /**
   * 평가 대상 외의 자리는 서로 다른 낮은 카드로 채웁니다.
   */
  private static List<Card> hand(DealFormat format, Card... targets) {
    List<Card> cards = new ArrayList<>(Arrays.asList(targets));
    for (Suit suit : Suit.values()) {
      for (Rank rank : Rank.values()) {
        if (rank.value() >= Rank.TEN.value()) {
          continue;
        }
        Card filler = new StandardCard(suit, rank);
        if (!cards.contains(filler) && cards.size() < format.handSize()) {
          cards.add(filler);
        }
      }
    }
    return cards;
  }

  @Test
  @DisplayName("모든 카드의 평가를 오마와 육마 배분에서 확인한다")
  void evaluatesEveryCardInBothFormats() {
    for (DealFormat format : DealFormat.values()) {
      for (Suit suit : Suit.values()) {
        for (Rank rank : Rank.values()) {
          double expected = switch (rank) {
            case TEN -> 0.5;
            case JACK, QUEEN, KING -> 1.0;
            case ACE -> suit == Suit.SPADE ? 0.0 : 1.0;
            default -> 0.0;
          };
          assertEquals(expected, DealMissRules.evaluateHand(format,
              hand(format, new StandardCard(suit, rank))));
        }
      }
      assertEquals(-1.0,
          DealMissRules.evaluateHand(format, hand(format, Joker.INSTANCE)));
    }
  }

  @Test
  @DisplayName("조커와 스페이드 A 및 10의 혼합 평가를 합산한다")
  void combinesSpecialCardsAndTens() {
    assertEquals(-0.5, DealMissRules.evaluateHand(DealFormat.SIX_PLAYER_OPENING,
        hand(DealFormat.SIX_PLAYER_OPENING, Joker.INSTANCE,
            new StandardCard(Suit.SPADE, Rank.ACE),
            new StandardCard(Suit.HEART, Rank.TEN))));
  }

  @Test
  @DisplayName("배분 형식과 다른 카드 수를 거절한다")
  void rejectsWrongHandSize() {
    assertThrows(IllegalArgumentException.class, () -> DealMissRules.evaluateHand(
        DealFormat.FIVE_PLAYER_OPENING, hand(DealFormat.SIX_PLAYER_OPENING)));
    assertThrows(IllegalArgumentException.class, () -> DealMissRules.evaluateHand(
        DealFormat.SIX_PLAYER_OPENING, hand(DealFormat.FIVE_PLAYER_OPENING)));
  }

  @Test
  @DisplayName("중복된 카드를 거절한다")
  void rejectsDuplicateCards() {
    List<Card> cards = hand(DealFormat.FIVE_PLAYER_OPENING);
    cards.set(9, cards.get(0));
    assertThrows(IllegalArgumentException.class, () -> DealMissRules.evaluateHand(
        DealFormat.FIVE_PLAYER_OPENING, cards));
  }

  @Test
  @DisplayName("손패 목록과 카드 및 배분 형식의 null을 거절한다")
  void rejectsNullInputs() {
    assertThrows(NullPointerException.class,
        () -> DealMissRules.evaluateHand(null, hand(DealFormat.FIVE_PLAYER_OPENING)));
    assertThrows(NullPointerException.class,
        () -> DealMissRules.evaluateHand(DealFormat.FIVE_PLAYER_OPENING, null));
    List<Card> cards = hand(DealFormat.FIVE_PLAYER_OPENING);
    cards.set(0, null);
    assertThrows(NullPointerException.class,
        () -> DealMissRules.evaluateHand(DealFormat.FIVE_PLAYER_OPENING, cards));
  }

  @Test
  @DisplayName("10장 배분의 딜미스 평가는 1점 미만이어야 한다")
  void fivePlayerEligibilityBoundary() {
    DealFormat format = DealFormat.FIVE_PLAYER_OPENING;
    assertTrue(DealMissRules.isEligible(format,
        hand(format, new StandardCard(Suit.HEART, Rank.TEN)), 6));
    assertFalse(DealMissRules.isEligible(format,
        hand(format, new StandardCard(Suit.HEART, Rank.JACK)), 6));
  }

  @Test
  @DisplayName("육마 최초 배분은 0점과 음수를 허용하고 양수는 거절한다")
  void sixPlayerEligibilityBoundary() {
    DealFormat format = DealFormat.SIX_PLAYER_OPENING;
    assertTrue(DealMissRules.isEligible(format, hand(format), 6));
    assertTrue(DealMissRules.isEligible(format, hand(format, Joker.INSTANCE), 6));
    assertFalse(DealMissRules.isEligible(format,
        hand(format, new StandardCard(Suit.HEART, Rank.TEN)), 6));
  }

  @Test
  @DisplayName("손패 조건을 만족해도 보유 점수가 5점 이하면 딜미스 불가다")
  void playerScoreBoundary() {
    for (DealFormat format : DealFormat.values()) {
      assertFalse(DealMissRules.isEligible(format, hand(format), 0));
      assertFalse(DealMissRules.isEligible(format, hand(format), 5));
      assertTrue(DealMissRules.isEligible(format, hand(format), 6));
    }
  }

}
