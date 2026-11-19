package mighty.game.bidding;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import mighty.model.Suit;
import mighty.model.Trump;

/**
 * 공개 공약에서 기루 선언을 최근 순서로 읽습니다.
 */
public final class PublicContractHistory {

  private PublicContractHistory() {
  }

  public static Optional<Suit> latestSuit(List<Bid> history) {
    for (int index = history.size() - 1; index >= 0; index--) {
      var suit = suitOf(history.get(index).trump());
      if (suit.isPresent()) {
        return suit;
      }
    }
    return Optional.empty();
  }

  public static List<Suit> suitsMostRecentFirst(List<Bid> history) {
    var suits = new LinkedHashSet<Suit>();
    for (int index = history.size() - 1; index >= 0; index--) {
      suitOf(history.get(index).trump()).ifPresent(suits::add);
    }
    return List.copyOf(suits);
  }

  public static Optional<Suit> suitOf(Trump trump) {
    return Arrays.stream(Suit.values()).filter(trump::matches).findFirst();
  }

}
