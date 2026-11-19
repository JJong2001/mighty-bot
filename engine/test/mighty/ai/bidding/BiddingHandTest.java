package mighty.ai.bidding;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import mighty.model.Card;
import mighty.model.Joker;
import mighty.model.Rank;
import mighty.model.StandardCard;
import mighty.model.Suit;
import mighty.model.Trump;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BiddingHandTest {

  private static StandardCard card(Suit suit, Rank rank) {
    return new StandardCard(suit, rank);
  }

  @Test
  @DisplayName("모든 문양과 숫자의 카드 보유 여부를 구분한다")
  void containsStandardCards() {
    for (Suit heldSuit : Suit.values()) {
      for (Rank heldRank : Rank.values()) {
        Card held = card(heldSuit, heldRank);
        BiddingHand hand = BiddingHand.from(List.of(held));
        for (Suit suit : Suit.values()) {
          for (Rank rank : Rank.values()) {
            assertEquals(held.equals(card(suit, rank)), hand.contains(card(suit, rank)));
          }
        }
        assertFalse(hand.contains(Joker.INSTANCE));
      }
    }
  }

  @Test
  @DisplayName("조커는 문양에 넣지 않고 별도로 조회한다")
  void containsJokerSeparately() {
    BiddingHand hand = BiddingHand.from(List.of(Joker.INSTANCE));
    assertTrue(hand.hasJoker());
    assertTrue(hand.contains(Joker.INSTANCE));
    for (Suit suit : Suit.values()) {
      assertEquals(0, hand.holding(suit).size());
    }
  }

  @Test
  @DisplayName("스페이드 기루 후보에서는 다이아 A를 마이티로 평가한다")
  void mightyDependsOnCandidateTrump() {
    BiddingHand spadeAce = BiddingHand.from(List.of(card(Suit.SPADE, Rank.ACE)));
    BiddingHand diamondAce = BiddingHand.from(List.of(card(Suit.DIAMOND, Rank.ACE)));
    for (Trump trump : Trump.values()) {
      assertEquals(trump != Trump.SPADE, spadeAce.hasMighty(trump));
      assertEquals(trump == Trump.SPADE, diamondAce.hasMighty(trump));
    }
  }

  @Test
  @DisplayName("클럽 기루 후보에서는 스페이드 3을 조커콜 카드로 평가한다")
  void gunDependsOnCandidateTrump() {
    BiddingHand clubThree = BiddingHand.from(List.of(card(Suit.CLUB, Rank.THREE)));
    BiddingHand spadeThree = BiddingHand.from(List.of(card(Suit.SPADE, Rank.THREE)));
    for (Trump trump : Trump.values()) {
      assertEquals(trump != Trump.CLUB, clubThree.hasGun(trump));
      assertEquals(trump == Trump.CLUB, spadeThree.hasGun(trump));
    }
  }

  @Test
  @DisplayName("문양별 카드는 내림차순이며 원본 손패 변경에 영향받지 않는다")
  void retainsImmutableSuitHoldings() {
    List<Card> cards = new ArrayList<>(List.of(
        card(Suit.HEART, Rank.TWO), card(Suit.HEART, Rank.KING),
        card(Suit.HEART, Rank.QUEEN), Joker.INSTANCE));
    BiddingHand hand = BiddingHand.from(cards);
    cards.clear();
    assertEquals(List.of(Rank.KING, Rank.QUEEN, Rank.TWO), hand.holding(Suit.HEART).ranks());
    assertTrue(hand.hasJoker());
    assertThrows(UnsupportedOperationException.class,
        () -> hand.holding(Suit.HEART).ranks().clear());
    assertTrue(hand.holding(Suit.CLUB).ranks().isEmpty());
  }

  @Test
  @DisplayName("중복 카드와 null 조회를 거절한다")
  void rejectsInvalidInputs() {
    Card ace = card(Suit.SPADE, Rank.ACE);
    assertThrows(IllegalArgumentException.class, () -> BiddingHand.from(List.of(ace, ace)));
    assertThrows(IllegalArgumentException.class,
        () -> BiddingHand.from(List.of(Joker.INSTANCE, Joker.INSTANCE)));
    BiddingHand hand = BiddingHand.from(List.of());
    assertThrows(NullPointerException.class, () -> hand.contains(null));
    assertThrows(NullPointerException.class, () -> hand.hasMighty(null));
    assertThrows(NullPointerException.class, () -> hand.hasGun(null));
  }

  @Test
  @DisplayName("후보 기루별 장수를 구하고 노기루는 0으로 반환한다")
  void countsTrumpByCandidate() {
    BiddingHand hand = BiddingHand.from(List.of(
        card(Suit.SPADE, Rank.ACE), card(Suit.SPADE, Rank.KING),
        card(Suit.SPADE, Rank.QUEEN), card(Suit.SPADE, Rank.SEVEN),
        card(Suit.DIAMOND, Rank.ACE), card(Suit.DIAMOND, Rank.TWO),
        card(Suit.HEART, Rank.THREE), Joker.INSTANCE));
    assertEquals(4, hand.trumpCount(Trump.SPADE));
    assertEquals(2, hand.trumpCount(Trump.DIAMOND));
    assertEquals(1, hand.trumpCount(Trump.HEART));
    assertEquals(0, hand.trumpCount(Trump.CLUB));
    assertEquals(0, hand.trumpCount(Trump.NO_TRUMP));
    assertThrows(NullPointerException.class, () -> hand.trumpCount(null));
  }

  @Test
  @DisplayName("모든 후보에서 정확히 마이티만 일반 문양 평가에서 제외한다")
  void removesOnlyCandidateMighty() {
    for (Trump trump : Trump.values()) {
      Suit mightySuit = trump == Trump.SPADE ? Suit.DIAMOND : Suit.SPADE;
      for (Suit suit : Suit.values()) {
        for (Rank rank : Rank.values()) {
          Card card = card(suit, rank);
          BiddingHand hand = BiddingHand.from(List.of(card));
          boolean isMighty = suit == mightySuit && rank == Rank.ACE;
          assertEquals(isMighty ? 0 : 1, hand.ordinaryHolding(suit, trump).size());
          assertEquals(1, hand.holding(suit).size());
          assertTrue(hand.contains(card));
        }
      }
    }
  }

  @Test
  @DisplayName("마이티만 있는 문양도 실제 손패에서는 보유하며 없는 문양은 빈 목록이다")
  void keepsPhysicalSuitInformation() {
    BiddingHand hand = BiddingHand.from(List.of(card(Suit.SPADE, Rank.ACE)));
    assertTrue(hand.ordinaryHolding(Suit.SPADE, Trump.NO_TRUMP).ranks().isEmpty());
    assertTrue(hand.holding(Suit.SPADE).contains(Rank.ACE));
    assertTrue(hand.ordinaryHolding(Suit.CLUB, Trump.NO_TRUMP).ranks().isEmpty());
    assertThrows(NullPointerException.class, () -> hand.ordinaryHolding(null, Trump.NO_TRUMP));
    assertThrows(NullPointerException.class, () -> hand.ordinaryHolding(Suit.SPADE, null));
  }

  @Test
  @DisplayName("마이티가 바뀌면 일반 최상위 연속 구간의 시작 숫자도 바뀐다")
  void topRunDependsOnCandidateTrump() {
    BiddingHand hand = BiddingHand.from(List.of(
        card(Suit.SPADE, Rank.KING), card(Suit.SPADE, Rank.QUEEN),
        card(Suit.SPADE, Rank.JACK), card(Suit.SPADE, Rank.SEVEN),
        card(Suit.DIAMOND, Rank.KING), card(Suit.DIAMOND, Rank.QUEEN)));
    assertEquals(3, hand.ordinaryTopRunLength(Suit.SPADE, Trump.NO_TRUMP));
    assertEquals(0, hand.ordinaryTopRunLength(Suit.SPADE, Trump.SPADE));
    assertEquals(2, hand.ordinaryTopRunLength(Suit.DIAMOND, Trump.SPADE));
    assertEquals(0, hand.ordinaryTopRunLength(Suit.DIAMOND, Trump.NO_TRUMP));
  }

  @Test
  @DisplayName("일반 A부터 연속 구간을 세며 마이티나 조커를 추가 장수로 세지 않는다")
  void topRunExcludesSpecialCards() {
    BiddingHand hand = BiddingHand.from(List.of(
        card(Suit.SPADE, Rank.ACE), card(Suit.SPADE, Rank.KING),
        card(Suit.SPADE, Rank.QUEEN), Joker.INSTANCE,
        card(Suit.HEART, Rank.ACE), card(Suit.HEART, Rank.QUEEN)));
    assertEquals(3, hand.ordinaryTopRunLength(Suit.SPADE, Trump.SPADE));
    assertEquals(2, hand.ordinaryTopRunLength(Suit.SPADE, Trump.NO_TRUMP));
    assertEquals(1, hand.ordinaryTopRunLength(Suit.HEART, Trump.NO_TRUMP));
    assertEquals(0, hand.ordinaryTopRunLength(Suit.CLUB, Trump.NO_TRUMP));
    assertThrows(NullPointerException.class,
        () -> hand.ordinaryTopRunLength(null, Trump.NO_TRUMP));
    assertThrows(NullPointerException.class,
        () -> hand.ordinaryTopRunLength(Suit.SPADE, null));
  }

}
