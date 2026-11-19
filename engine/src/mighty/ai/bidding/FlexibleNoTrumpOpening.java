package mighty.ai.bidding;

import java.util.Arrays;
import java.util.List;
import mighty.model.*;

/**
 * 바닥 전 분산된 승리 자원과 회수·보강 선택지로 노기루 경쟁 한도를 평가합니다.
 */
final class FlexibleNoTrumpOpening {

  private FlexibleNoTrumpOpening() {
  }

  static boolean available(BiddingHand hand) {
    if ((hand.hasMighty(Trump.NO_TRUMP) || hand.hasJoker()) && ordinaryWins(hand) >= 5) {
      return true;
    }
    if (!hand.hasMighty(Trump.NO_TRUMP) || !hand.hasJoker()) {
      return false;
    }
    long aces = Arrays.stream(Suit.values())
        .filter(s -> hand.ordinaryHolding(s, Trump.NO_TRUMP).contains(Rank.ACE)).count();
    long seconds = Arrays.stream(Suit.values())
        .flatMap(s -> hand.ordinaryHolding(s, Trump.NO_TRUMP).ranks().stream())
        .filter(rank -> rank == Rank.KING || rank == Rank.QUEEN).count();
    return aces >= 1 && seconds >= 2;
  }

  static BidAssessment assess(BiddingHand hand) {
    int ordinary = ordinaryWins(hand);
    // 조커를 직접 보유하면 일반 승리와 마이티 지원을 별개 구에 배치할 수 있습니다.
    int controls = ordinary + (hand.hasJoker() ? 1 : 0) + 1;
    int ceiling = hand.hasJoker() && controls >= 7 ? 16 : 15;
    return new BidAssessment(Trump.NO_TRUMP, true, 15, ceiling, ceiling * 10 + 15, List.of(
        "일반 연속 승리 " + ordinary + "구와 특수카드·프렌드 지원을 경쟁 자원으로 평가합니다.",
        "바닥의 미확인 카드는 승리 수에 더하지 않고 실제 버리기 이후 공약을 다시 평가합니다."));
  }

  private static int ordinaryWins(BiddingHand hand) {
    return Arrays.stream(Suit.values())
        .mapToInt(suit -> hand.ordinaryTopRunLength(suit, Trump.NO_TRUMP))
        .sum();
  }

}
