package mighty.game.round;

import java.util.LinkedHashMap;
import java.util.Map;
import mighty.game.Seat;
import mighty.game.bidding.Bid;
import mighty.model.Trump;

/**
 * 카드 득점에서 테이블탑의 정산 원액과 잔고 한도를 계산합니다.
 */
public final class RoundSettlement {

  private RoundSettlement() {
  }

  public static Map<Seat, Integer> nominal(RoundResult result, boolean declaredSolo) {
    int contract = result.contract().points();
    int difference = result.teamPoints() - contract;
    int base = result.succeeded() ? difference + 2 * (contract - Bid.MIN_POINTS)
        : difference <= -10 ? -15 : difference;
    int unit = base * (result.contract().trump() == Trump.NO_TRUMP ? 2 : 1)
        * (declaredSolo ? 2 : 1) * (contract == Bid.MAX_POINTS ? 2 : 1);
    Map<Seat, Integer> amounts = new LinkedHashMap<>();
    for (Seat seat : result.captured().keySet()) {
      int amount = seat.equals(result.declarer()) ? (result.friend().isPresent() ? 2 : 4) * unit
          : result.friend().filter(seat::equals).isPresent() ? unit : -unit;
      amounts.put(seat, amount);
    }
    return Map.copyOf(amounts);
  }

  /**
   * 부족분의 배분 방식은 테이블의 별도 선택이므로 징수액과 받을 상한을 제공한다.
   */
  public static CollectionResult collect(RoundResult result, boolean declaredSolo,
      Map<Seat, Integer> balances) {
    Map<Seat, Integer> losses = new LinkedHashMap<>();
    Map<Seat, Integer> claims = new LinkedHashMap<>();
    int collected = 0;
    int wanted = 0;
    for (var entry : nominal(result, declaredSolo).entrySet()) {
      Seat seat = entry.getKey();
      int amount = entry.getValue();
      if (amount > 0) {
        claims.put(seat, amount);
        wanted += amount;
      } else {
        int payment = seat.equals(result.declarer()) ? -amount
            : Math.min(Math.max(0, balances.getOrDefault(seat, 40)), -amount);
        losses.put(seat, -payment);
        collected += payment;
      }
    }
    return new CollectionResult(losses, claims, collected, wanted);
  }

  public record CollectionResult(Map<Seat, Integer> losses, Map<Seat, Integer> claims,
                                 int collected, int wanted) {

    public CollectionResult {
      losses = Map.copyOf(losses);
      claims = Map.copyOf(claims);
    }

    public int shortage() {
      return wanted - collected;
    }
  }

}
