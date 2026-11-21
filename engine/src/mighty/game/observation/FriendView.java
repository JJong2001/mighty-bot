package mighty.game.observation;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import mighty.game.Seat;
import mighty.game.bidding.Bid;
import mighty.model.Card;

/**
 * 프렌드를 지정할 주공의 손패와 자기 버린 카드만 제공합니다.
 */
public record FriendView(
    Seat self, Bid contract, List<Card> hand, List<Card> discardedCards, List<Bid> contractHistory
) {

  public FriendView(Seat self, Bid contract, List<Card> hand, List<Card> discardedCards) {
    this(self, contract, hand, discardedCards, List.of(contract));
  }

  public FriendView {
    Objects.requireNonNull(self, "주공 자리가 필요합니다.");
    Objects.requireNonNull(contract, "공약이 필요합니다.");
    hand = List.copyOf(hand);
    discardedCards = List.copyOf(discardedCards);
    contractHistory = List.copyOf(contractHistory);
    List<Card> combined = new ArrayList<>(hand);
    combined.addAll(discardedCards);
    if (hand.size() != 10 || discardedCards.size() != 3
        || new HashSet<>(combined).size() != 13) {
      throw new IllegalArgumentException("서로 겹치지 않는 손패 10장과 버린 카드 3장이 필요합니다.");
    }
  }

}
