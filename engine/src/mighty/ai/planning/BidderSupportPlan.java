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
    return find(hand, self, bids, List.of());
  }

  private static Optional<BidderSupportPlan> find(BiddingHand hand, Seat self, List<PublicBid> bids,
      List<Card> buried) {
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
        if (side && !hand.contains(ace) && !buried.contains(ace)) {
          return Optional.of(new BidderSupportPlan(signal, suit, ace));
        }
        // 선입찰 문양의 탑이 내 쪽에 있으면 바깥의 다음 상위가 연결점입니다.
        // 독립 탑을 여러 문양에서 돌릴 수 있어야 선을 넘긴 뒤의 추가 지원도 기대합니다.
        var sideRuns = Arrays.stream(Suit.values()).filter(s -> s != suit)
            .mapToInt(s -> hand.ordinaryTopRunLength(s, Trump.NO_TRUMP)).filter(n -> n > 0)
            .toArray();
        if ((hand.contains(ace) || buried.contains(ace)) && sideRuns.length >= 2
            && Arrays.stream(sideRuns).sum() >= 3) {
          var called = Arrays.stream(Rank.values()).filter(rank -> rank.value() >= Rank.TEN.value())
              .map(rank -> new StandardCard(suit, rank))
              .filter(card -> !hand.contains(card) && !buried.contains(card))
              .max(java.util.Comparator.comparingInt(card -> card.rank().value()));
          if (called.isPresent()) {
            return Optional.of(new BidderSupportPlan(signal, suit, called.get()));
          }
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
    return forContract(hand, self, bids, contract, List.of());
  }

  public static Optional<BidderSupportPlan> forContract(BiddingHand hand, Seat self,
      List<PublicBid> bids, Bid contract, List<Card> buried) {
    if (contract.trump() != Trump.NO_TRUMP || contract.points() < 17 || contract.points() == 20) {
      return Optional.empty();
    }
    return find(hand, self, bids, buried);
  }

  public boolean callsNextHonor() {
    return called.suit() == transfer && called.rank() != Rank.ACE;
  }
}
