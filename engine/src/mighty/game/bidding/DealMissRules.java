package mighty.game.bidding;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import mighty.game.deal.DealFormat;
import mighty.model.Card;
import mighty.model.Joker;
import mighty.model.Rank;
import mighty.model.StandardCard;
import mighty.model.Suit;

/**
 * 딜미스 자격 판정에 사용하는 손패 평가 규칙입니다.
 */
public final class DealMissRules {

  private DealMissRules() {
  }

  /**
   * 시작 배분 형식에 맞는 손패를 평가합니다. 실제 대국의 카드 점수와는 별도의 평가값입니다.
   */
  public static double evaluateHand(DealFormat format, List<Card> hand) {
    Objects.requireNonNull(format, "배분 형식이 필요합니다.");
    List<Card> cards = List.copyOf(hand);
    if (cards.size() != format.handSize()) {
      throw new IllegalArgumentException(
          "배분 형식과 손패의 카드 수가 일치하지 않습니다."
      );
    }
    if (new HashSet<>(cards).size() != cards.size()) {
      throw new IllegalArgumentException(
          "손패에 중복된 카드가 있습니다."
      );
    }
    return cards.stream()
        .mapToDouble(DealMissRules::cardValue)
        .sum();
  }

  /**
   * 손패와 보유 점수가 딜미스 조건을 만족하는지 확인합니다. 차례와 이전 입찰 여부는 비딩 진행 상태에서 별도로 검사합니다.
   */
  public static boolean isEligible(
      DealFormat format,
      List<Card> hand,
      int playerScore
  ) {
    double handValue = evaluateHand(format, hand);
    // 보유 점수가 정확히 5점인 경우에도 선언할 수 없습니다.
    if (playerScore <= 5) {
      return false;
    }
    return switch (format) {
      case FIVE_PLAYER_OPENING -> handValue < 1.0;
      case SIX_PLAYER_OPENING -> handValue <= 0.0;
    };
  }

  public static boolean canDeclare(
      BiddingState state,
      List<Card> hand,
      int playerScore
  ) {
    Objects.requireNonNull(state, "비딩 상태가 필요합니다.");
    var currentSeat = state.currentSeat();
    // 풀 낙찰 등을 포함해 종료된 비딩에는 선언할 수 없습니다.
    if (currentSeat.isEmpty()) {
      return false;
    }
    // 해당 배분에서 이미 공약을 제시했다면 선언할 수 없습니다.
    if (state.hasOffered(currentSeat.orElseThrow())) {
      return false;
    }
    return isEligible(
        state.participants().format(),
        hand,
        playerScore
    );
  }

  private static double cardValue(Card card) {
    if (card instanceof Joker) {
      return -1.0;
    }
    StandardCard standard = (StandardCard) card;
    // 비딩 중의 기루와 관계없이 스페이드 A는 0점입니다.
    if (standard.suit() == Suit.SPADE && standard.rank() == Rank.ACE) {
      return 0.0;
    }
    return switch (standard.rank()) {
      case TEN -> 0.5;
      case JACK, QUEEN, KING, ACE -> 1.0;
      default -> 0.0;
    };
  }

}
