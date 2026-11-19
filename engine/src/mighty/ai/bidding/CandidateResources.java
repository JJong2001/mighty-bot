package mighty.ai.bidding;

import java.util.Arrays;
import java.util.Comparator;
import mighty.game.rule.Rules;
import mighty.model.Rank;
import mighty.model.Suit;
import mighty.model.Trump;

/**
 * 현재 자기 패만 분석합니다. 프렌드의 미확인 손패나 미래 바닥 카드는 추가하지 않습니다.
 */
record CandidateResources(BiddingHand hand, Trump trump, Suit primary) {

  static CandidateResources from(BiddingHand hand, Trump trump) {
    Suit primary = Arrays.stream(Suit.values()).filter(trump::matches).findFirst()
        .orElseGet(() -> Arrays.stream(Suit.values()).max(Comparator
            .<Suit>comparingInt(suit -> hand.holding(suit).size())
            .thenComparingInt(suit -> hand.holding(suit).consecutiveFrom(Rank.ACE))
            .thenComparingInt(suit -> -suit.ordinal())).orElseThrow());
    return new CandidateResources(hand, trump, primary);
  }

  SuitHolding holding() {
    return hand.holding(primary);
  }

  int length() {
    return holding().size();
  }

  boolean mighty() {
    return hand.hasMighty(trump);
  }

  boolean joker() {
    return hand.hasJoker();
  }

  /**
   * 주력 이외 문양의 일반 A만 독립 회수로 계산합니다. 마이티를 중복 계산하지 않습니다.
   */
  int sideAces() {
    return (int) Arrays.stream(Suit.values()).filter(suit -> suit != primary)
        .filter(suit -> hand.ordinaryHolding(suit, trump).contains(Rank.ACE)).count();
  }

  /**
   * 초구 지원 경로입니다. 마이티 문양의 일반 최상위도 포함하되 독립 회수 A로 세지 않습니다.
   */
  boolean hasSideOpening() {
    Suit mightySuit = Rules.mighty(trump).suit();
    return sideAces() > 0
        || mightySuit != primary && hand.ordinaryTopRunLength(mightySuit, trump) > 0;
  }

  /**
   * 사이드의 AK 등 연속 카드와 자기 마이티 아래 KQ의 초구 가치를 확인합니다.
   */
  int sideTopRun() {
    return Arrays.stream(Suit.values()).filter(suit -> suit != primary)
        .filter(suit -> hand.ordinaryHolding(suit, trump).contains(Rank.ACE)
            || (mighty() && hand.holding(suit).contains(Rank.ACE)))
        .mapToInt(suit -> hand.ordinaryTopRunLength(suit, trump)).max().orElse(0);
  }

  /**
   * 확정 상위 연결 밖의 사이드 카드 수입니다. 모두 잃는다고 단정하는 수치가 아닙니다.
   */
  int sideWater() {
    return Arrays.stream(Suit.values()).filter(suit -> suit != primary)
        .mapToInt(suit -> hand.ordinaryHolding(suit, trump).size()
            - hand.ordinaryTopRunLength(suit, trump)).sum();
  }

  boolean has(Rank rank) {
    return holding().contains(rank);
  }

  int pointsAtTop() {
    return (int) holding().ranks().stream().filter(rank -> rank.value() >= Rank.TEN.value())
        .count();
  }

}
