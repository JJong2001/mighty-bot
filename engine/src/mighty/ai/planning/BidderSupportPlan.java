package mighty.ai.planning;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import mighty.ai.bidding.BiddingHand;
import mighty.game.Seat;
import mighty.game.bidding.*;
import mighty.game.rule.Rules;
import mighty.model.*;

/** 선입찰자의 공개 문양과 내 회수 자원으로 납치 가능성을 탐색합니다. 카드 소유자는 추정입니다. */
public record BidderSupportPlan(PublicBid signal, Suit transfer, StandardCard called) {

  public static Optional<BidderSupportPlan> find(BiddingHand hand, Seat self, List<PublicBid> bids) {
    if (!hand.hasJoker()) {
      return Optional.empty();
    }
    for (int i = bids.size() - 1; i >= 0; i--) {
      var signal = bids.get(i);
      if (signal.seat().equals(self) || !(signal.action() instanceof BidOffer offer)
          || offer.bid().points() < 17 || offer.bid().trump() == Trump.NO_TRUMP) {
        continue;
      }
      var suit = PublicContractHistory.suitOf(offer.bid().trump()).orElseThrow();
      boolean entry = hand.holding(suit).ranks().stream()
          .anyMatch(rank -> new StandardCard(suit, rank).points() == 0);
      if (!entry) {
        continue;
      }
      var mighty = Rules.mighty(Trump.NO_TRUMP);
      if (hand.hasMighty(Trump.NO_TRUMP)) {
        var ace = new StandardCard(suit, Rank.ACE);
        boolean side = Arrays.stream(Suit.values()).anyMatch(s -> s != suit
            && hand.holding(s).size() >= 4);
        if (side && !hand.contains(ace)) {
          return Optional.of(new BidderSupportPlan(signal, suit, ace));
        }
      } else if (Rules.mighty(offer.bid().trump()).equals(mighty)) {
        // 조커와 독립적인 두 상위 연결을 내가 보유하면 외부 마이티의 추가 지원을 시험합니다.
        long runs = Arrays.stream(Suit.values()).filter(s -> s != suit
            && hand.ordinaryTopRunLength(s, Trump.NO_TRUMP) >= 2).count();
        if (runs >= 2) {
          return Optional.of(new BidderSupportPlan(signal, suit, mighty));
        }
      }
    }
    return Optional.empty();
  }

  public static Optional<BidderSupportPlan> forContract(BiddingHand hand, Seat self,
      List<PublicBid> bids, Bid contract) {
    if (contract.trump() != Trump.NO_TRUMP || contract.points() < 17 || contract.points() == 20) {
      return Optional.empty();
    }
    return find(hand, self, bids);
  }
}
