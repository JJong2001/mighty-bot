package mighty.ai.planning.distribution;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import mighty.game.Seat;
import mighty.game.rule.Rules;
import mighty.model.StandardCard;
import mighty.model.Suit;
import mighty.model.Trump;

/**
 * 한 가상 배분에서 문양별 일반 카드 순위를 한 번만 구성합니다.
 */
final class DistributionHoldings {

  private final Map<Suit, List<List<Integer>>> ranksBySuit = new EnumMap<>(Suit.class);

  DistributionHoldings(ConditionalDeal deal) {
    var mighty = Rules.mighty(Trump.NO_TRUMP);
    Arrays.stream(Suit.values()).forEach(suit -> ranksBySuit.put(suit,
        IntStream.range(0, deal.hands().size()).mapToObj(index -> deal.hands().get(new Seat(index))
            .stream().filter(StandardCard.class::isInstance).map(StandardCard.class::cast)
            .filter(card -> card.suit() == suit && !card.equals(mighty))
            .map(card -> card.rank().value()).sorted().toList()).toList()));
  }

  List<List<Integer>> ranks(Suit suit) {
    return ranksBySuit.get(suit);
  }

}
