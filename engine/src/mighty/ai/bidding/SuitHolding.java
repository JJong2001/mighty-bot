package mighty.ai.bidding;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import mighty.model.Rank;
import mighty.model.Suit;

public record SuitHolding(
    Suit suit,
    List<Rank> ranks
) {

  public SuitHolding {
    Objects.requireNonNull(suit, "문양이 필요합니다.");
    ranks = List.copyOf(ranks);
    if (new HashSet<>(ranks).size() != ranks.size()) {
      throw new IllegalArgumentException(
          "같은 문양에 중복된 숫자가 있습니다."
      );
    }
    ranks = ranks.stream()
        .sorted(Comparator.comparingInt(Rank::value).reversed())
        .toList();
  }

  public int size() {
    return ranks.size();
  }

  public boolean contains(Rank rank) {
    Objects.requireNonNull(rank, "확인할 숫자가 필요합니다.");
    return ranks.contains(rank);
  }

  /**
   * 지정한 숫자부터 아래로 연속해서 보유한 카드 수를 반환합니다. 시작 숫자의 카드가 없으면 0을 반환합니다.
   */
  public int consecutiveFrom(Rank start) {
    Objects.requireNonNull(start, "연속 보유를 확인할 시작 숫자가 필요합니다.");
    int expected = start.value();
    int count = 0;
    // ranks는 생성자에서 높은 숫자부터 정렬되어 있습니다.
    for (Rank rank : ranks) {
      // 지정한 시작 숫자보다 높은 카드는 건너뜁니다.
      if (rank.value() > expected) {
        continue;
      }
      // 다음 숫자가 없으면 연속 구간이 끝납니다.
      if (rank.value() < expected) {
        break;
      }
      count++;
      expected--;
    }
    return count;
  }

}
