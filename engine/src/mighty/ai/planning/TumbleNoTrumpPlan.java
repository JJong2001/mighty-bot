package mighty.ai.planning;

import java.util.*;
import mighty.ai.bidding.BiddingHand;
import mighty.game.bidding.Bid;
import mighty.game.rule.Rules;
import mighty.model.*;

/**
 * 우당탕탕: 마쪼와 마공 받침을 남기고 사이드 K를 먼저 개발하는 제한된 저공약 운영. 일반 A 프렌드 운영이나 조커 한 번만 맞으면 나는 운영과 구분합니다.
 */
public record TumbleNoTrumpPlan(StandardCard friend, StandardCard opening) {

  public static Optional<TumbleNoTrumpPlan> find(BiddingHand hand,
      Collection<? extends Card> buried, Bid contract) {
    if (contract.trump() != Trump.NO_TRUMP || contract.points() < 15
        || contract.points() > 16 || !hand.hasMighty(Trump.NO_TRUMP) || !hand.hasJoker()) {
      return Optional.empty();
    }
    Suit mightySuit = Rules.mighty(Trump.NO_TRUMP).suit();
    if (hand.holding(mightySuit).ranks().stream()
        .noneMatch(rank -> rank.value() < Rank.TEN.value())) {
      return Optional.empty();
    }
    int tops = Arrays.stream(Suit.values())
        .mapToInt(suit -> hand.ordinaryTopRunLength(suit, Trump.NO_TRUMP)).sum();
    // 이미 자력으로 길게 달릴 수 있는 패는 기존 운영에 맡깁니다.
    if (tops >= 3 || Arrays.stream(Suit.values())
        .anyMatch(suit -> hand.ordinaryHolding(suit, Trump.NO_TRUMP).size() >= 5)) {
      return Optional.empty();
    }
    for (Suit primary : Suit.values()) {
      var ace = new StandardCard(primary, Rank.ACE);
      var held = hand.ordinaryHolding(primary, Trump.NO_TRUMP);
      if (primary == mightySuit || hand.contains(ace) || buried.contains(ace)
          || !held.contains(Rank.KING) || !held.contains(Rank.QUEEN)
          || held.size() < 3 || held.size() > 4) {
        continue;
      }
      for (Suit side : Suit.values()) {
        var sideAce = new StandardCard(side, Rank.ACE);
        var cards = hand.ordinaryHolding(side, Trump.NO_TRUMP);
        if (side == primary || side == mightySuit || !cards.contains(Rank.KING)
            || hand.contains(sideAce) || buried.contains(sideAce)) {
          continue;
        }
        var low = cards.ranks().stream().filter(rank -> rank.value() < Rank.TEN.value())
            .min(Comparator.comparingInt(Rank::value));
        if (low.isPresent()) {
          return Optional.of(new TumbleNoTrumpPlan(ace, new StandardCard(side, low.get())));
        }
      }
    }
    return Optional.empty();
  }
}
