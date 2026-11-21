package mighty.ai.planning;

import java.util.Arrays;
import java.util.Comparator;
import java.util.Optional;
import mighty.ai.bidding.BiddingHand;
import mighty.game.bidding.*;
import mighty.game.observation.FriendView;
import mighty.game.rule.Rules;
import mighty.model.*;

/** 엿노와 곱노의 주력 상위 연결과 별도 초구 회수 수단을 함께 확인합니다. */
public record HighNoTrumpPlan(Suit primary, int tops, boolean mighty, boolean joker) {

  public static Optional<HighNoTrumpPlan> find(BiddingHand hand) {
    if (!hand.holding(Suit.SPADE).contains(Rank.KING)
        || (!hand.hasMighty(Trump.NO_TRUMP) && !hand.hasJoker())) {
      return Optional.empty();
    }
    return Arrays.stream(Suit.values()).filter(s -> s != Suit.SPADE)
        .filter(s -> hand.holding(s).size() >= 4)
        .filter(s -> hand.ordinaryTopRunLength(s, Trump.NO_TRUMP) >= 2)
        .max(Comparator.<Suit>comparingInt(s -> hand.ordinaryTopRunLength(s, Trump.NO_TRUMP))
            .thenComparingInt(s -> hand.holding(s).size()))
        .map(s -> new HighNoTrumpPlan(s, hand.ordinaryTopRunLength(s, Trump.NO_TRUMP),
            hand.hasMighty(Trump.NO_TRUMP), hand.hasJoker()));
  }

  /** 입찰에서 예상하는 주력과 실제 주공이 운영할 주력을 같은 기준으로 정합니다. */
  public static Optional<HighNoTrumpPlan> forContract(BiddingHand hand, Bid bid) {
    if (bid.trump() != Trump.NO_TRUMP || (bid.points() != 16 && bid.points() != 17
        && bid.points() != 20)) {
      return Optional.empty();
    }
    return find(hand).filter(plan -> bid.points() != 20 || plan.canRun(hand));
  }

  public int competitiveCeiling() {
    return joker && tops >= 3 ? 17 : 16;
  }

  /** 이미 자력으로 긴 문양을 완성한 패는 기존 고공약 평가에 맡깁니다. */
  public boolean competitiveShape(BiddingHand hand) {
    return hand.holding(primary).size() == 4 && tops <= 3
        && hand.holding(Suit.SPADE).size() >= 3 && !canRun(hand);
  }

  public Trump suited() {
    return Trump.valueOf(primary.name());
  }

  public boolean hasTwoSources(BiddingHand hand) {
    return mighty && joker && tops >= 3 && hand.holding(Suit.SPADE).size() >= 4;
  }

  public boolean canRun(BiddingHand hand) {
    return hasTwoSources(hand)
        && hand.holding(primary).size() + hand.holding(Suit.SPADE).size() == 9;
  }

  public Optional<Card> support(FriendView view, BiddingHand hand) {
    if (!mighty || !joker) {
      return Optional.of(mighty ? Joker.INSTANCE : Rules.mighty(Trump.NO_TRUMP));
    }
    if (!hasTwoSources(hand)) {
      return Optional.empty();
    }
    // 두 특수카드를 확보했을 때 상대의 공개 문양 입찰에서 추가 지원을 기대합니다.
    for (int index = view.auction().size() - 1; index >= 0; index--) {
      PublicBid entry = view.auction().get(index);
      if (entry.seat().equals(view.self()) || !(entry.action() instanceof BidOffer offer)
          || offer.bid().points() < 16) {
        continue;
      }
      var suit = PublicContractHistory.suitOf(offer.bid().trump());
      if (suit.isPresent() && suit.get() != primary && suit.get() != Suit.SPADE
          && hand.holding(suit.get()).size() == 1) {
        Card ace = new StandardCard(suit.get(), Rank.ACE);
        if (!hand.contains(ace) && !view.discardedCards().contains(ace)) {
          return Optional.of(ace);
        }
      }
    }
    var queen = new StandardCard(Suit.SPADE, Rank.QUEEN);
    return hand.contains(queen) || view.discardedCards().contains(queen)
        ? Optional.empty() : Optional.of(queen);
  }
}
