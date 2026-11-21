package mighty.ai.bidding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import mighty.ai.preparation.PreparationPlanner;
import mighty.application.round.RoundService;
import mighty.game.GameMode;
import mighty.game.PlayOrder;
import mighty.game.Seat;
import mighty.game.TableLayout;
import mighty.game.bidding.*;
import mighty.game.deal.DeckFactory;
import mighty.game.friend.SpecialFriend;
import mighty.game.observation.BiddingView;
import mighty.game.round.RoundResult;
import mighty.game.round.RoundSettlement;
import mighty.game.round.RoundState;
import mighty.model.Card;
import mighty.model.Trump;

/** 공개 입찰을 설명하는 가상 패에서 패스·맞기루·다른 공약의 내 명목 정산점을 짝지어 비교합니다. */
final class ContestedSuitAuction {
  private static final int DEALS = 32;
  private static final int DRAW_LIMIT = 64;

  private ContestedSuitAuction() {}

  static Optional<PublicBid> signal(BiddingView view, BiddingHand own, List<BidOffer> legal) {
    if (view.hand().size() != 10) {
      return Optional.empty();
    }
    var history = view.bidding().history();
    for (int i = history.size() - 1; i >= 0; i--) {
      var entry = history.get(i);
      if (entry.seat().equals(view.self()) || !(entry.action() instanceof BidOffer offer)
          || offer.bid().points() < 16 || offer.bid().trump() == Trump.NO_TRUMP) {
        continue;
      }
      var trump = offer.bid().trump();
      var ownSuit = SuitBidEvaluator.assess(CandidateResources.from(own, trump));
      if (ownSuit.canCompete() && legal.stream().map(BidOffer::bid)
          .anyMatch(b -> b.trump() == trump && b.points() <= ownSuit.ceilingPoints())) {
        return Optional.of(entry);
      }
    }
    return Optional.empty();
  }

  static final class Outcomes {
    int deals;
    int successes;
    int backRuns;
    int settlement;

    void add(RoundResult result, int amount) {
      deals++;
      successes += result.succeeded() ? 1 : 0;
      backRuns += result.teamPoints() <= result.contract().points() - 10 ? 1 : 0;
      settlement += amount;
    }
  }

  record Estimate(Outcomes bidderIsAlly, Outcomes bidderIsOpponent) {
    Estimate() {
      this(new Outcomes(), new Outcomes());
    }

    int total() {
      return bidderIsAlly.settlement + bidderIsOpponent.settlement;
    }
  }

  record Comparison(BiddingAction choice, Map<BiddingAction, Estimate> estimates) {}

  static Comparison evaluate(BiddingView view, PublicBid signal, List<BidOffer> alternatives) {
    var seats = view.bidding().participants().seats();
    var order = PlayOrder.from(new TableLayout(GameMode.FIVE_PLAYER, seats), Set.copyOf(seats));
    var unseen = DeckFactory.create().stream().filter(c -> !view.hand().contains(c)).toList();
    var random = new Random(view.hand().stream().map(Card::toCode).sorted().toList().hashCode());
    var results = new LinkedHashMap<BiddingAction, Estimate>();
    results.put(BidPass.INSTANCE, new Estimate());
    alternatives.forEach(offer -> results.put(offer, new Estimate()));
    var standing = view.bidding().highestBid().orElseThrow();
    var service = RoundService.strategic();
    for (int sample = 0; sample < DEALS; sample++) {
      var pool = draw(unseen, ((BidOffer) signal.action()).bid(), random);
      var hands = new HashMap<Seat, List<Card>>();
      hands.put(view.self(), view.hand());
      hands.put(signal.seat(), pool.subList(0, 10));
      int offset = 10;
      for (Seat seat : seats) {
        if (!hands.containsKey(seat)) {
          hands.put(seat, pool.subList(offset, offset + 10));
          offset += 10;
        }
      }
      for (var entry : results.entrySet()) {
        var action = entry.getKey();
        var award = action instanceof BidOffer offer ? new PublicBid(view.self(), offer) : standing;
        var bid = ((BidOffer) award.action()).bid();
        var history = new ArrayList<>(view.bidding().history());
        history.add(new PublicBid(view.self(), action));
        var round = RoundState.start(order, new BiddingResult(award.seat(), bid), hands,
            pool.subList(40, 43), history);
        // 테이블탑에서 사용하는 엿노·곱노 바닥 전 변경 후 실제 버리기·프렌드·플레이 정책을 적용합니다.
        var before = PreparationPlanner.highBeforeKitty(BiddingHand.from(hands.get(award.seat())),
            bid, bid).orElse(bid);
        if (!before.equals(bid)) {
          round.reviseContract(award.seat(), before);
        }
        round.takeKitty(award.seat());
        var result = service.playToEnd(round);
        var settlement = RoundSettlement.nominal(result,
            round.friendSelection().orElseThrow() == SpecialFriend.NONE);
        boolean allied = onDeclarerTeam(result, view.self()) == onDeclarerTeam(result, signal.seat());
        var group = allied ? entry.getValue().bidderIsAlly() : entry.getValue().bidderIsOpponent();
        group.add(result, settlement.get(view.self()));
      }
    }
    // 프렌드가 될 가능성과 야당일 때의 큰 손실은 실제 표본 빈도대로 평균에 포함합니다.
    BiddingAction best = BidPass.INSTANCE;
    for (var entry : results.entrySet()) {
      if (entry.getValue().total() > results.get(best).total()) {
        best = entry.getKey();
      }
    }
    return new Comparison(best, Map.copyOf(results));
  }

  private static boolean onDeclarerTeam(RoundResult result, Seat seat) {
    return result.declarer().equals(seat) || result.friend().filter(seat::equals).isPresent();
  }

  static List<Card> draw(List<Card> unseen, Bid signal, Random random) {
    var pool = new ArrayList<>(unseen);
    List<Card> closest = null;
    int best = Integer.MIN_VALUE;
    for (int attempt = 0; attempt < DRAW_LIMIT; attempt++) {
      Collections.shuffle(pool, random);
      var hand = BiddingHand.from(pool.subList(0, 10));
      var suit = CandidateResources.from(hand, signal.trump());
      var assessment = SuitBidEvaluator.assess(suit);
      if (assessment.canCompete() && assessment.ceilingPoints() >= signal.points()) {
        return pool;
      }
      // 내 손에 기루가 몰려 통상 입찰 기준을 만족하기 어려우면 가장 근접한 배분을 사용합니다.
      int strength = assessment.ceilingPoints() * 100 + suit.length() * 10 + suit.pointsAtTop();
      if (strength > best) {
        best = strength;
        closest = List.copyOf(pool);
      }
    }
    return closest;
  }
}
