package mighty.ai.analysis;

import java.util.*;
import mighty.model.*;
import mighty.game.rule.Rules;

/**
 * 남길 패와 이미 제거한 카드로 상위 장벽·연결·향후 승리 가능 장수를 계산합니다.
 */
public record HoldingControl(int length, int blockers, int connected, int topRun,
                             int possibleWinners) {

  public static HoldingControl analyze(List<Card> owned, List<Card> removed, Trump trump,
      Suit suit) {
    Card mighty = Rules.mighty(trump);
    var held = owned.stream()
        .filter(c -> c instanceof StandardCard card && card.suit() == suit && !c.equals(mighty))
        .map(StandardCard.class::cast)
        .sorted(Comparator.comparingInt((StandardCard c) -> c.rank().value()).reversed()).toList();
    Set<Card> gone = new HashSet<>(removed);
    var ranks = Arrays.stream(Rank.values()).sorted(Comparator.comparingInt(Rank::value).reversed())
        .map(rank -> new StandardCard(suit, rank))
        .filter(c -> !c.equals(mighty) && !gone.contains(c)).toList();
    int topRun = 0;
    for (var c : ranks) {
      if (!held.contains(c)) {
        break;
      }
      topRun++;
    }
    if (held.isEmpty()) {
      return new HoldingControl(0, 0, 0, 0, 0);
    }
    int highest = held.get(0).rank().value();
    int blockers = (int) ranks.stream().filter(c -> c.rank().value() > highest && !held.contains(c))
        .count();
    int connected = 0;
    for (var c : ranks) {
      if (c.rank().value() > highest) {
        continue;
      }
      if (!held.contains(c)) {
        break;
      }
      connected++;
    }
    return new HoldingControl(held.size(), blockers, connected, topRun,
        Math.max(0, held.size() - blockers));
  }

}
