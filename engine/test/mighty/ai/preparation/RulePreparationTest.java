package mighty.ai.preparation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import mighty.game.Seat;
import mighty.game.bidding.Bid;
import mighty.game.deal.DeckFactory;
import mighty.game.friend.CardFriend;
import mighty.game.observation.DiscardView;
import mighty.game.observation.FriendView;
import mighty.game.rule.Rules;
import mighty.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RulePreparationTest {

  @Test
  @DisplayName("전체 버리기 조합을 비교해도 마이티와 조커를 보존한다")
  void preservesRecoveryCards() {
    for (Trump trump : Trump.values()) {
      List<Card> cards = new ArrayList<>(List.of(Rules.mighty(trump), Joker.INSTANCE));
      DeckFactory.create().stream().filter(card -> !cards.contains(card)).limit(11)
          .forEach(cards::add);
      List<Card> discarded = new RuleDiscardPolicy().choose(
          new DiscardView(new Seat(0), new Bid(15, trump),
              cards));
      assertEquals(3, discarded.size());
      assertEquals(3, discarded.stream().distinct().count());
      assertTrue(cards.containsAll(discarded));
      assertFalse(discarded.contains(Rules.mighty(trump)));
      assertFalse(discarded.contains(Joker.INSTANCE));
    }
  }

  @Test
  @DisplayName("마이티와 총 및 긴 KQJ 문양이 있으면 그 A 프렌드를 검토한다")
  void completesLongSuitInsteadOfFixingJokerFriend() {
    List<Card> hand = List.of(new StandardCard(Suit.SPADE, Rank.ACE),
        new StandardCard(Suit.CLUB, Rank.THREE), new StandardCard(Suit.HEART, Rank.KING),
        new StandardCard(Suit.HEART, Rank.QUEEN), new StandardCard(Suit.HEART, Rank.JACK),
        new StandardCard(Suit.HEART, Rank.SEVEN), new StandardCard(Suit.DIAMOND, Rank.ACE),
        new StandardCard(Suit.DIAMOND, Rank.TWO), new StandardCard(Suit.CLUB, Rank.TWO),
        new StandardCard(Suit.CLUB, Rank.FOUR));
    List<Card> discards = List.of(new StandardCard(Suit.DIAMOND, Rank.THREE),
        new StandardCard(Suit.DIAMOND, Rank.FOUR), new StandardCard(Suit.DIAMOND, Rank.FIVE));
    var view = new FriendView(new Seat(0), new Bid(15, Trump.NO_TRUMP), hand, discards);
    assertEquals(new CardFriend(new StandardCard(Suit.HEART, Rank.ACE)),
        new RuleFriendPolicy().choose(view));
  }

}
