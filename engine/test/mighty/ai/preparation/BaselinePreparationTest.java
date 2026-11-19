package mighty.ai.preparation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import mighty.game.Seat;
import mighty.game.bidding.Bid;
import mighty.game.deal.DeckFactory;
import mighty.game.friend.CardFriend;
import mighty.game.friend.SpecialFriend;
import mighty.game.observation.DiscardView;
import mighty.game.observation.FriendView;
import mighty.game.rule.Rules;
import mighty.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BaselinePreparationTest {

  @Test
  @DisplayName("버리기는 손패의 세 장만 선택하고 마이티와 조커를 보존한다")
  void discardsOnlyOwnedCardsAndPreservesSpecials() {
    for (Trump trump : Trump.values()) {
      List<Card> cards = new ArrayList<>(List.of(Rules.mighty(trump), Joker.INSTANCE));
      DeckFactory.create().stream().filter(card -> !cards.contains(card)).limit(11)
          .forEach(cards::add);
      List<Card> discarded = new BaselineDiscardPolicy().choose(
          new DiscardView(new Seat(0), new Bid(13, trump), cards));
      assertEquals(3, discarded.size());
      assertTrue(cards.containsAll(discarded));
      assertFalse(discarded.contains(Rules.mighty(trump)));
      assertFalse(discarded.contains(Joker.INSTANCE));
    }
  }

  private static FriendView friendView(List<Card> owned) {
    List<Card> combined = new ArrayList<>(owned);
    DeckFactory.create().stream().filter(card -> !combined.contains(card)
            && !card.equals(Rules.mighty(Trump.NO_TRUMP)) && !(card instanceof Joker))
        .limit(13 - owned.size()).forEach(combined::add);
    return new FriendView(new Seat(0), new Bid(13, Trump.NO_TRUMP),
        combined.subList(0, 10), combined.subList(10, 13));
  }

  @Test
  @DisplayName("마이티와 조커의 자기 보유 여부에 따라 프렌드 선택이 달라진다")
  void selectsFriendFromOwnInformation() {
    BaselineFriendPolicy policy = new BaselineFriendPolicy();
    Card mighty = Rules.mighty(Trump.NO_TRUMP);
    assertEquals(new CardFriend(mighty), policy.choose(friendView(List.of())));
    assertEquals(new CardFriend(Joker.INSTANCE), policy.choose(friendView(List.of(mighty))));
    assertEquals(SpecialFriend.FIRST_TRICK,
        policy.choose(friendView(List.of(mighty, Joker.INSTANCE))));
  }

}
