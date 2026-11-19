package mighty.game.deal;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import mighty.model.Card;
import mighty.model.Joker;
import mighty.model.Rank;
import mighty.model.StandardCard;
import mighty.model.Suit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 덱의 구성과 불변성을 검사한다. 셔플·배분은 별도 책임이다.
 */
class DeckFactoryTest {

  @Test
  @DisplayName("마이티 덱은 중복 없이 정확히 53장이다")
  void createsFiftyThreeDistinctCards() {
    List<Card> deck = DeckFactory.create();
    assertEquals(53, deck.size());
    assertEquals(53, new HashSet<>(deck).size());
  }

  @Test
  @DisplayName("네 문양의 2부터 A까지 일반 카드가 모두 포함된다")
  void containsEveryOrdinaryCard() {
    List<Card> deck = DeckFactory.create();
    assertEquals(52, deck.stream().filter(StandardCard.class::isInstance).count());
    for (Suit suit : Suit.values()) {
      for (Rank rank : Rank.values()) {
        assertTrue(deck.contains(new StandardCard(suit, rank)),
            () -> "Missing card: " + suit + " " + rank);
      }
    }
  }

  @Test
  @DisplayName("조커는 정확히 한 장 포함된다")
  void containsExactlyOneJoker() {
    assertEquals(1, DeckFactory.create().stream()
        .filter(card -> card == Joker.INSTANCE).count());
  }

  @Test
  @DisplayName("반환된 덱은 외부에서 변경할 수 없다")
  void returnsUnmodifiableDeck() {
    List<Card> deck = DeckFactory.create();
    assertThrows(UnsupportedOperationException.class, deck::clear);
    assertThrows(UnsupportedOperationException.class, () -> deck.add(Joker.INSTANCE));
  }

}
