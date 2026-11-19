package mighty.game.observation;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import mighty.game.Seat;
import mighty.game.GameMode;
import mighty.game.bidding.BiddingState;
import mighty.model.Card;

public record BiddingView(
    Seat self,
    List<Card> hand,
    int playerScore,
    BiddingState bidding,
    GameMode mode
) {

  public BiddingView {
    Objects.requireNonNull(self, "행동자 자리가 필요합니다.");
    Objects.requireNonNull(bidding, "비딩 상태가 필요합니다.");
    Objects.requireNonNull(mode, "게임 모드가 필요합니다.");
    if (mode == GameMode.FIVE_PLAYER
        && bidding.participants().format() == mighty.game.deal.DealFormat.SIX_PLAYER_OPENING) {
      throw new IllegalArgumentException("육마 최초 배분은 육마 모드여야 합니다.");
    }
    hand = List.copyOf(hand);
    validateActingSeat(self, bidding);
    validateHand(hand, bidding);
  }

  /**
   * 기존 호출은 배분 형식으로 기본 모드를 정하고, 육마 휴식 판은 모드를 명시합니다.
   */
  public BiddingView(Seat self, List<Card> hand, int playerScore, BiddingState bidding) {
    this(self, hand, playerScore, bidding,
        bidding.participants().format() == mighty.game.deal.DealFormat.SIX_PLAYER_OPENING
            ? GameMode.SIX_PLAYER : GameMode.FIVE_PLAYER);
  }

  private static void validateActingSeat(
      Seat self,
      BiddingState bidding
  ) {
    Seat expectedSeat = bidding.currentSeat().orElseThrow(
        () -> new IllegalArgumentException(
            "종료된 비딩에서는 행동용 관측을 만들 수 없습니다."
        )
    );
    if (!self.equals(expectedSeat)) {
      throw new IllegalArgumentException(
          "현재 비딩할 참가자의 관측이어야 합니다."
      );
    }
  }

  private static void validateHand(
      List<Card> hand,
      BiddingState bidding
  ) {
    int expectedSize = bidding.participants().format().handSize();
    if (hand.size() != expectedSize) {
      throw new IllegalArgumentException(
          "배분 형식과 손패의 카드 수가 일치하지 않습니다."
      );
    }
    if (new HashSet<>(hand).size() != hand.size()) {
      throw new IllegalArgumentException(
          "손패에 중복된 카드가 있습니다."
      );
    }
  }

}
