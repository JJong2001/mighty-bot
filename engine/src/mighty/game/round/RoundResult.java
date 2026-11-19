package mighty.game.round;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import mighty.game.GameMode;
import mighty.game.Seat;
import mighty.game.bidding.Bid;

/**
 * 10구 종료 후 공개하는 카드 득점입니다. 개인 보유 점수의 정산과는 구분합니다.
 */
public record RoundResult(
    Seat declarer,
    Bid contract,
    Optional<Seat> friend,
    Map<Seat, Integer> captured,
    int discardedPoints
) {

  public static final int TOTAL_CARD_POINTS = 20;

  public RoundResult {
    Objects.requireNonNull(declarer, "주공 자리가 필요합니다.");
    Objects.requireNonNull(contract, "공약이 필요합니다.");
    Objects.requireNonNull(friend, "실제 프렌드 정보가 필요합니다.");
    captured = Map.copyOf(captured);
    if (captured.size() != GameMode.PLAYING_PLAYER_COUNT || !captured.containsKey(declarer)) {
      throw new IllegalArgumentException("플레이 참가자 다섯 명의 득점이 필요합니다.");
    }
    if (friend.isPresent()
        && (friend.get().equals(declarer) || !captured.containsKey(friend.get()))) {
      throw new IllegalArgumentException("실제 프렌드는 주공 외의 참가자여야 합니다.");
    }
    if (discardedPoints < 0 || discardedPoints > 3
        || captured.values().stream().anyMatch(points -> points < 0 || points > TOTAL_CARD_POINTS)
        || captured.values().stream().mapToInt(Integer::intValue).sum() + discardedPoints
        != TOTAL_CARD_POINTS) {
      throw new IllegalArgumentException("버린 카드와 모든 참가자의 득점 합계는 20점이어야 합니다.");
    }
  }

  /**
   * 주공과 실제 프렌드의 획득 점수에 버린 카드 점수를 더합니다.
   */
  public int teamPoints() {
    return captured.get(declarer) + friend.map(captured::get).orElse(0) + discardedPoints;
  }

  /**
   * 총 카드 점수에서 여당 득점을 뺀 야당 득점입니다.
   */
  public int oppositionPoints() {
    return TOTAL_CARD_POINTS - teamPoints();
  }

  /**
   * 여당이 낙찰 공약 이상의 점수를 얻었는지 확인합니다.
   */
  public boolean succeeded() {
    return teamPoints() >= contract.points();
  }

}
