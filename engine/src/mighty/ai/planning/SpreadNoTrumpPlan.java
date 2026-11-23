package mighty.ai.planning;

import mighty.ai.bidding.BiddingHand;
import mighty.model.Suit;
import mighty.model.Trump;

/** 긴 한 문양 없이 여러 문양의 탑과 두 특수카드로 선을 이어 가는 열 장의 패입니다. */
public final class SpreadNoTrumpPlan {

  private SpreadNoTrumpPlan() {
  }

  public static boolean available(BiddingHand hand) {
    if (!hand.hasMighty(Trump.NO_TRUMP) || !hand.hasJoker()) {
      return false;
    }
    int cards = 0;
    int suits = 0;
    int extendable = 0;
    int winners = 0;
    for (Suit suit : Suit.values()) {
      int length = hand.ordinaryHolding(suit, Trump.NO_TRUMP).size();
      if (length == 0) {
        continue;
      }
      int run = hand.ordinaryTopRunLength(suit, Trump.NO_TRUMP);
      if (run == 0) {
        return false;
      }
      cards += length;
      suits++;
      winners += run;
      if (length >= 3) {
        extendable++;
      }
    }
    // 일반 탑과 마이티를 중복 계산하지 않습니다. 빈 문양은 물패를 버릴 기회가 됩니다.
    return cards == 8 && suits == 3 && extendable >= 2 && winners >= 4;
  }
}
