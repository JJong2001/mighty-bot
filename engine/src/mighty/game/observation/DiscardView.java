package mighty.game.observation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import mighty.game.Seat;
import mighty.game.bidding.Bid;
import mighty.model.Card;

/**
 * 바닥을 받은 주공에게만 전달하는 버리기 관측입니다.
 */
public record DiscardView(Seat self, Bid contract, List<Card> hand) {

  public DiscardView {
    Objects.requireNonNull(self, "주공 자리가 필요합니다.");
    Objects.requireNonNull(contract, "공약이 필요합니다.");
    hand = List.copyOf(hand);
    if (hand.size() != 13 || new HashSet<>(hand).size() != 13) {
      throw new IllegalArgumentException("주공의 서로 다른 카드 13장이 필요합니다.");
    }
  }

}
