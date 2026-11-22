package mighty.ai.bidding;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import mighty.ai.planning.NoTrumpPlan;
import mighty.ai.planning.NoTrumpPlanner;
import mighty.ai.planning.SpreadNoTrumpPlan;
import mighty.game.rule.Rules;
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

  static boolean supportedBySpreadTops(BiddingHand hand, int points) {
    if (points <= 17 && SpreadNoTrumpPlan.available(hand)) {
      return true;
    }
    return available(hand) && Arrays.stream(Suit.values())
        .filter(suit -> hand.ordinaryTopRunLength(suit, Trump.NO_TRUMP) > 0).count() >= 2
        && assess(hand).ceilingPoints() >= points;
  }

  static int ordinaryWins(BiddingHand hand) {
    return Arrays.stream(Suit.values())
        .mapToInt(suit -> hand.ordinaryTopRunLength(suit, Trump.NO_TRUMP))
        .sum();
  }

  static Optional<String> fifteenPointRoute(BiddingHand hand, NoTrumpPlan plan) {
    Suit main = plan.primary();
    if (!plan.available() || hand.ordinaryHolding(main, Trump.NO_TRUMP).size() < 4
        || hand.ordinaryTopRunLength(main, Trump.NO_TRUMP) < 2) {
      return Optional.empty();
    }
    var mighty = Rules.mighty(Trump.NO_TRUMP);
    if (plan.friend().equals(mighty) && !hand.contains(mighty) && hand.hasJoker()) {
      // 주력의 한 장벽을 뽑으면 내 점수 카드가 탑이 되고, 다른 문양으로도 선을 잡을 수 있습니다.
      boolean sideEntry = Arrays.stream(Suit.values()).anyMatch(suit -> suit != main
          && hand.ordinaryTopRunLength(suit, Trump.NO_TRUMP) > 0);
      if (sideEntry && NoTrumpPlanner.jokerSweepSuit(hand).isPresent()) {
        return Optional.of("주력의 한 장벽을 조커로 뽑고 별도 문양의 탑과 마이티 프렌드로 회수합니다.");
      }
    }
    // 조커프를 찾은 뒤 반환받을 긴 문양에 일반 탑이 있어 마이티를 아낄 수 있습니다.
    Suit returned = mighty.suit();
    if (plan.friend() instanceof Joker && hand.contains(mighty) && main != returned
        && hand.holding(returned).size() >= 4
        && hand.ordinaryTopRunLength(returned, Trump.NO_TRUMP) > 0) {
      return Optional.of("주력의 연속 탑을 돌리고 조커 프렌드의 반환을 일반 탑으로 받아 마이티를 남깁니다.");
    }
    return Optional.empty();
  }

}
