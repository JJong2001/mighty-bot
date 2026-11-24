package mighty.ai.bidding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import mighty.ai.planning.BidderSupportPlan;
import mighty.ai.planning.HighNoTrumpPlan;
import mighty.ai.planning.NoTrumpPlanner;
import mighty.ai.preparation.PreparationPlanner;
import mighty.application.round.RoundService;
import mighty.game.GameMode;
import mighty.game.PlayOrder;
import mighty.game.Seat;
import mighty.game.TableLayout;
import mighty.game.bidding.*;
import mighty.game.deal.DeckFactory;
import mighty.game.friend.SpecialFriend;
import mighty.game.friend.CardFriend;
import mighty.game.friend.FriendSelection;
import mighty.game.observation.FriendView;
import mighty.game.observation.BiddingView;
import mighty.game.round.RoundResult;
import mighty.game.round.RoundSettlement;
import mighty.game.round.RoundState;
import mighty.model.Card;
import mighty.model.Rank;
import mighty.model.Suit;
import mighty.model.Trump;

/** 공개 입찰을 설명하는 가상 패에서 패스·맞기루·다른 공약의 내 명목 정산점을 짝지어 비교합니다. */
public final class ContestedSuitAuction {
  private static final int DEALS = 32;
  private static final int DRAW_LIMIT = 64;
  private static final int FRIEND_DEALS = 8;

  private record FriendComparison(FriendView view, BidderSupportPlan plan, FriendSelection usual) {}

  // 준비 단계와 실제 지정 단계가 동일한 관측을 사용하면 짝지은 대국을 다시 열지 않습니다.
  private static final Map<FriendComparison, FriendSelection> FRIEND_CACHE =
      new java.util.concurrent.ConcurrentHashMap<>();

  private ContestedSuitAuction() {}

  static Optional<Comparison> specialCompetition(BiddingView view, BiddingHand own,
      List<BidOffer> legal, List<BidOffer> ordinary) {
    if (view.hand().size() != 10 || view.bidding().highestBid().isEmpty()) {
      return Optional.empty();
    }
    var standing = view.bidding().highestBid().orElseThrow();
    var bid = ((BidOffer) standing.action()).bid();
    var suit = PublicContractHistory.suitOf(bid.trump());
    boolean defense = bid.points() >= 16 && !standing.seat().equals(view.self()) && suit.isPresent()
        && own.holding(suit.get()).size() == 1 && own.holding(suit.get()).contains(Rank.ACE);
    var support = BidderSupportPlan.find(own, view.self(), view.bidding().history());
    if (!defense && support.isEmpty()) {
      return Optional.empty();
    }
    var offers = new LinkedHashSet<>(ordinary);
    if (defense) {
      // 방어비딩은 성공 상한을 넘더라도 실패 폭이 작은 다른 기루를 찾습니다.
      for (var other : Suit.values()) {
        if (other != suit.orElseThrow() && own.holding(other).size() >= 3) {
          legal.stream().filter(o -> o.bid().trump().matches(other))
              .min(Comparator.comparingInt(o -> o.bid().points())).ifPresent(offers::add);
        }
      }
    }
    if (support.isPresent()) {
      legal.stream().filter(o -> o.bid().trump() == Trump.NO_TRUMP && o.bid().points() >= 17
          && o.bid().points() < 20).min(Comparator.comparingInt(o -> o.bid().points()))
          .ifPresent(offers::add);
      var eighteen = new BidOffer(new Bid(18, Trump.NO_TRUMP));
      if (own.hasMighty(Trump.NO_TRUMP) && legal.contains(eighteen)
          && (!support.get().callsNextHonor() || bid.points() >= 18)) {
        offers.add(eighteen);
      }
    }
    if (offers.isEmpty()) {
      return Optional.empty();
    }
    var signal = defense ? standing : support.orElseThrow().signal();
    return Optional.of(evaluate(view, signal, List.copyOf(offers), true,
        support.filter(p -> p.signal().equals(signal) && p.callsNextHonor())));
  }

  static Optional<PublicBid> signal(BiddingView view, BiddingHand own, List<BidOffer> candidates) {
    if (view.hand().size() != 10) {
      return Optional.empty();
    }
    // 손패 평가와 현재 입찰 상한을 통과한 노기루에 대해서만 운영 문양을 확인합니다.
    var primary = candidates.stream().filter(c -> c.bid().trump() == Trump.NO_TRUMP)
        .findFirst().flatMap(c -> runningSuit(own, c.bid()));
    var history = view.bidding().history();
    for (int i = history.size() - 1; i >= 0; i--) {
      var entry = history.get(i);
      if (entry.seat().equals(view.self()) || !(entry.action() instanceof BidOffer offer)
          || offer.bid().points() < 16 || offer.bid().trump() == Trump.NO_TRUMP) {
        continue;
      }
      var trump = offer.bid().trump();
      if (candidates.stream().anyMatch(c -> c.bid().trump() == trump)
          || primary.filter(trump::matches).isPresent()) {
        return Optional.of(entry);
      }
    }
    return Optional.empty();
  }

  private static Optional<Suit> runningSuit(BiddingHand hand, Bid contract) {
    // 여러 문양의 연속 탑만으로 이 공약을 운영하면 낮은 꼬리패의 승격에 의존하지 않습니다.
    if (FlexibleNoTrumpOpening.supportedBySpreadTops(hand, contract.points())) {
      return Optional.empty();
    }
    var planned = HighNoTrumpPlan.forContract(hand, contract).map(HighNoTrumpPlan::primary);
    if (planned.isPresent()) {
      return planned;
    }
    var primary = NoTrumpPlanner.analyze(hand).primary();
    var holding = hand.ordinaryHolding(primary, Trump.NO_TRUMP);
    return holding.size() >= 4 && holding.size() > hand.ordinaryTopRunLength(primary, Trump.NO_TRUMP)
        ? Optional.of(primary) : Optional.empty();
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
    return evaluate(view, signal, alternatives, false, Optional.empty());
  }

  private static Comparison evaluate(BiddingView view, PublicBid signal, List<BidOffer> alternatives,
      boolean inferControls, Optional<BidderSupportPlan> nextHonor) {
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
      var pool = draw(unseen, ((BidOffer) signal.action()).bid(), random, inferControls, nextHonor);
      var hands = dealHands(seats, view.self(), view.hand(), signal.seat(), pool);
      for (var entry : results.entrySet()) {
        var action = entry.getKey();
        var award = action instanceof BidOffer offer ? new PublicBid(view.self(), offer) : standing;
        var bid = ((BidOffer) award.action()).bid();
        var history = new ArrayList<>(view.bidding().history());
        history.add(new PublicBid(view.self(), action));
        var round = RoundState.start(order, new BiddingResult(award.seat(), bid), hands,
            pool.subList(40, 43), history);
        // 테이블탑에서 사용하는 엿노·곱노 바닥 전 변경 후 실제 버리기·프렌드·플레이 정책을 적용합니다.
        var before = PreparationPlanner.beforePublicBid(BiddingHand.from(hands.get(award.seat())),
            bid, bid, award.seat(), history).orElse(bid);
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
    return new Comparison(bestChoice(results), Map.copyOf(results));
  }

  static BiddingAction bestChoice(Map<BiddingAction, Estimate> results) {
    BiddingAction best = BidPass.INSTANCE;
    for (var entry : results.entrySet()) {
      if (entry.getValue().total() > results.get(best).total()) {
        best = entry.getKey();
      }
    }
    return best;
  }

  /** 버리기 뒤 동일한 공개 입찰 조건에서 차상위 납치와 기존 프렌드의 내 정산을 비교합니다. */
  public static FriendSelection preferredFriend(FriendView view, BidderSupportPlan plan,
      FriendSelection usual) {
    var called = new CardFriend(plan.called());
    if (usual.equals(called)) {
      return usual;
    }
    if (FRIEND_CACHE.size() >= 256) {
      FRIEND_CACHE.clear();
    }
    return FRIEND_CACHE.computeIfAbsent(new FriendComparison(view, plan, usual),
        key -> compareFriends(view, plan, usual, called));
  }

  private static FriendSelection compareFriends(FriendView view, BidderSupportPlan plan,
      FriendSelection usual, CardFriend called) {
    var seen = new ArrayList<>(view.hand());
    seen.addAll(view.discardedCards());
    var unseen = DeckFactory.create().stream().filter(c -> !seen.contains(c)).toList();
    var random = new Random(seen.stream().map(Card::toCode).sorted().toList().hashCode());
    var seats = new LinkedHashSet<Seat>();
    view.auction().forEach(b -> seats.add(b.seat()));
    seats.add(view.self());
    if (seats.size() > GameMode.PLAYING_PLAYER_COUNT) {
      // 육마 최초 입찰만으로 제외된 자리를 특정할 수 없습니다.
      return usual;
    }
    // 전체 입찰 이력이면 첫 입찰자부터의 원형 순서가 그대로 복원됩니다.
    // 일부 자리만 기록한 입력에서는 나머지 가상 자리를 보충합니다.
    for (int i = 0; seats.size() < GameMode.PLAYING_PLAYER_COUNT; i++) {
      seats.add(new Seat(i));
    }
    var table = List.copyOf(seats);
    var order = PlayOrder.from(new TableLayout(GameMode.FIVE_PLAYER, table), seats);
    var service = RoundService.strategic();
    int gain = 0;
    for (int sample = 0; sample < FRIEND_DEALS; sample++) {
      var pool = draw(unseen, ((BidOffer) plan.signal().action()).bid(), random, true,
          Optional.of(plan));
      var hands = dealHands(table, view.self(), view.hand(), plan.signal().seat(), pool);
      for (var friend : List.of(called, usual)) {
        // 버리기·프렌드를 고정하고 플레이부터 실행해야 평가 중 준비 결정을 재호출하지 않습니다.
        var round = RoundState.start(order, new BiddingResult(view.self(), view.contract()),
            hands, view.discardedCards(), view.auction());
        round.takeKitty(view.self());
        round.discard(view.self(), view.discardedCards());
        round.chooseFriend(view.self(), friend);
        int net = RoundSettlement.nominal(service.playToEnd(round), friend == SpecialFriend.NONE)
            .get(view.self());
        gain += friend.equals(called) ? net : -net;
      }
    }
    return gain > 0 ? called : usual;
  }

  private static Map<Seat, List<Card>> dealHands(List<Seat> seats, Seat self, List<Card> own,
      Seat bidder, List<Card> pool) {
    var hands = new HashMap<Seat, List<Card>>();
    hands.put(self, own);
    hands.put(bidder, pool.subList(0, 10));
    int offset = 10;
    for (var seat : seats) {
      if (!hands.containsKey(seat)) {
        hands.put(seat, pool.subList(offset, offset + 10));
        offset += 10;
      }
    }
    return hands;
  }

  private static boolean onDeclarerTeam(RoundResult result, Seat seat) {
    return result.declarer().equals(seat) || result.friend().filter(seat::equals).isPresent();
  }

  private static List<Card> draw(List<Card> unseen, Bid signal, Random random, boolean inferControls,
      Optional<BidderSupportPlan> nextHonor) {
    var pool = new ArrayList<>(unseen);
    if (signal.points() < 16) {
      // 낮은 문양 입찰에는 강제 첫 공약도 있으므로 특정 기루 장수나 탑을 전제하지 않습니다.
      Collections.shuffle(pool, random);
      return pool;
    }
    List<Card> closest = null;
    int best = Integer.MIN_VALUE;
    int plausible = 0;
    for (int attempt = 0; attempt < DRAW_LIMIT; attempt++) {
      Collections.shuffle(pool, random);
      var hand = BiddingHand.from(pool.subList(0, 10));
      var suit = CandidateResources.from(hand, signal.trump());
      var assessment = SuitBidEvaluator.assess(suit);
      boolean qualifies = assessment.canCompete() && assessment.ceilingPoints() >= signal.points();
      if (qualifies && (!inferControls || signal.points() < 17)) {
        return pool;
      }
      // 고공약을 설명할 수 있는 두 가상 패 중 초구와 조커 대응 수단이 나은 쪽을 씁니다.
      // 특정 A나 총의 소유를 강제하지 않으며 어느 공약을 시험하든 같은 배분을 사용합니다.
      int strength;
      if (qualifies) {
        plausible++;
        int opening = suit.hasSideOpening() || suit.mighty() ? 1 : 0;
        int jokerControl = suit.joker() || hand.hasGun(signal.trump()) ? 1 : 0;
        strength = 10000 + opening + jokerControl;
      } else {
        strength = assessment.ceilingPoints() * 100 + suit.length() * 10 + suit.pointsAtTop();
      }
      if (nextHonor.isPresent()) {
        // 외부 차상위 한 장만으로 고공약을 설명하지 않습니다. 문양 길이, 상위 연결,
        // 별도 초구를 함께 본 뒤 가장 가까운 배분을 쓰며 카드 주인을 고정하지 않습니다.
        var target = nextHonor.get().called();
        int run = hand.holding(target.suit()).consecutiveFrom(target.rank());
        strength = strength * 4 + run * 12 + (suit.hasSideOpening() ? 5 : 0);
      }
      if (strength > best) {
        best = strength;
        closest = List.copyOf(pool);
      }
      if (plausible == 2) {
        break;
      }
    }
    return closest;
  }
}
