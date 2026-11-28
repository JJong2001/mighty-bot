package mighty.ai.play.declarer;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import mighty.ai.bidding.BiddingHand;
import mighty.ai.planning.HighNoTrumpPlan;
import mighty.ai.planning.BidderSupportPlan;
import mighty.ai.play.PlayDecision;
import mighty.ai.play.PlaySupport;
import mighty.game.action.Action;
import mighty.game.action.JokerLead;
import mighty.game.friend.CardFriend;
import mighty.game.rule.Rules;
import mighty.model.*;

/** 합의한 노기루 운영에서 공개된 소모를 보고 다음 선을 고릅니다. */
final class HighNoTrumpPlay {

  private HighNoTrumpPlay() {
  }

  private static Optional<HighNoTrumpPlan> route(DeclarerPosition position) {
    var bid = position.view().contract();
    if (bid.trump() != Trump.NO_TRUMP || (bid.points() != 16 && bid.points() != 17
        && bid.points() != 20)) {
      return Optional.empty();
    }
    var hand = BiddingHand.from(NoTrumpOperations.initialCards(position));
    return HighNoTrumpPlan.forContract(hand, bid);
  }

  static Optional<PlayDecision> leadToBidder(DeclarerPosition position) {
    var view = position.view();
    if (position.needsFriendCall() && view.contract().trump() == Trump.NO_TRUMP
        && view.contract().points() >= 17 && !view.context().auction().isEmpty()) {
      var bidder = BidderSupportPlan.forContract(BiddingHand.from(NoTrumpOperations.initialCards(position)),
          view.self(), view.context().auction(), view.contract(),
          ((mighty.game.observation.DeclarerInfo) view.privateInfo()).discardedCards())
          .filter(p -> position.calls(p.called()));
      if (bidder.isPresent()) {
        var target = bidder.get().transfer();
        var cash = position.ordinary().stream().filter(a -> ((StandardCard) a.card()).suit() != target)
            .filter(a -> position.facts().isSuitTop(a.card())).toList();
        if (!cash.isEmpty()) {
          return Optional.of(new PlayDecision(PlaySupport.highest(cash),
              "선입찰자에게 선을 넘기기 전에 내 독립 탑을 먼저 처리합니다."));
        }
        if (bidder.get().callsNextHonor()) {
          // 상대의 상위패를 프렌드로 부른 경우 그 위에 있는 내 일반 탑부터 현금화합니다.
          var above = position.inSuit(target).stream().filter(a ->
              ((StandardCard) a.card()).rank().value() > bidder.get().called().rank().value())
              .toList();
          if (!above.isEmpty()) {
            return Optional.of(new PlayDecision(PlaySupport.highest(above),
                "프렌드 위의 내 탑을 먼저 쓰고, 남은 물패로 선을 넘깁니다."));
          }
        }
        var transfer = low(position.inSuit(target), "선입찰 문양의 물패로 프렌드의 추가 승리를 연결합니다.");
        if (transfer.isPresent()) {
          return transfer;
        }
      }
    }
    return Optional.empty();
  }

  /** 한 구 지원이 필요한 고공약에서만 기존 운영과 비교할 선을 제시합니다. */
  static Optional<PlayDecision> prepareExtraWinner(DeclarerPosition position) {
    var view = position.view();
    if (view.contract().points() < 17 || !position.needsFriendCall()) {
      return Optional.empty();
    }
    var primary = PlaySupport.announcedReturnSuit(view).orElse(position.primarySuit());
    var main = position.inSuit(primary);
    var facts = position.facts();
    long mainTops = main.stream().filter(a -> facts.isSuitTop(a.card())).count();
    boolean recovery = view.hand().contains(Joker.INSTANCE) || view.hand().contains(Rules.mighty(Trump.NO_TRUMP));
    if (position.initialLength(primary) < 4
        || (mainTops < 3 && mainTops < main.size()) || (main.isEmpty() && !recovery)) {
      return Optional.empty();
    }
    var side = position.ordinary().stream()
        .filter(a -> ((StandardCard) a.card()).suit() != primary).toList();
    var losers = side.stream().filter(a -> !facts.isSuitTop(a.card())).toList();
    if (losers.size() < 2) {
      return Optional.empty();
    }
    var called = ((CardFriend) view.friendSelection()).card();
    // 지정 카드가 빠지는 순간 같은 문양의 내 카드가 승격되면 먼저 친구를 찾는 연결을 지킵니다.
    if (callingPromotesOwnCard(position, called)) {
      return Optional.empty();
    }
    var transfers = losers.stream().filter(a -> called instanceof Joker
        || called.equals(Rules.mighty(Trump.NO_TRUMP))
        || ((StandardCard) called).suit() == ((StandardCard) a.card()).suit()).toList();
    if (transfers.isEmpty()) {
      return Optional.empty();
    }
    // 주력의 반환받을 탑 하나는 남기고, 사이드 탑부터 써서 프렌드 패의 승격을 유도합니다.
    var cash = side.stream().filter(a -> facts.isSuitTop(a.card())).toList();
    if (!cash.isEmpty()) {
      return Optional.of(new PlayDecision(PlaySupport.highest(cash), "사이드 탑을 먼저 써서 프렌드의 추가 승리를 준비합니다."));
    }
    // 회수 수단이 별도로 있으면 주력 탑도 먼저 돌리는 경로를 비교합니다.
    // 낮은 주력이 남을 때에는 반환을 받을 일반 탑 한 장을 보존합니다.
    if (losers.size() == 2 && view.context().history().stream().noneMatch(trick ->
        trick.leader().equals(view.self()) && trick.winner().equals(view.self())
            && trick.cards().get(0).action().card() instanceof StandardCard card
            && !card.equals(Rules.mighty(Trump.NO_TRUMP)) && card.suit() != primary)) {
      // 사이드 탑을 먼저 처리한 적이 없으면 단순 주력 순회를 프렌드 호출로 바꾸지 않습니다.
      return Optional.empty();
    }
    if (losers.size() > 2 && (mainTops > 1 || (mainTops == 1 && main.size() == 1 && recovery))) {
      return Optional.of(new PlayDecision(PlaySupport.highest(main), "남은 주력 탑을 현금화해 추가 지원을 준비합니다."));
    }
    if (view.trickNumber().isFirst() && called instanceof Joker) {
      return Optional.empty();
    }
    return Optional.of(new PlayDecision(position.lowSide(transfers), "남은 약한 문양에서 지정 프렌드에게 선을 넘깁니다."));
  }

  private static boolean callingPromotesOwnCard(DeclarerPosition position, Card called) {
    var mighty = Rules.mighty(Trump.NO_TRUMP);
    if (!(called instanceof StandardCard target) || target.equals(mighty)) {
      return false;
    }
    int highestOther = position.facts().cards().outsideCards(target.suit()).stream()
        .filter(card -> !card.equals(target) && !card.equals(mighty))
        .mapToInt(card -> card.rank().value()).max().orElse(0);
    return position.inSuit(target.suit()).stream().map(a -> (StandardCard) a.card())
        .anyMatch(card -> card.rank().value() < target.rank().value()
            && card.rank().value() > highestOther);
  }

  static Optional<PlayDecision> lead(DeclarerPosition position) {
    var view = position.view();
    var route = route(position);
    if (route.isEmpty() || !(view.friendSelection() instanceof CardFriend friend)) {
      return Optional.empty();
    }
    var plan = route.get();
    var main = position.inSuit(plan.primary());
    boolean first = view.trickNumber().isFirst();
    if (position.needsFriendCall()) {
      if (friend.card() instanceof Joker && plan.tops() == 2) {
        if (first) {
          return playCard(position.legal(), new StandardCard(Suit.SPADE, Rank.KING),
              "별도 초구로 선을 잡고 주력의 낮은 패로 조커를 찾습니다.");
        }
        return low(main, "상위 두 장만으로 주력을 정리하기 어려워 조커 지원을 먼저 받습니다.");
      }
      if (friend.card() instanceof StandardCard called && !called.equals(Rules.mighty(Trump.NO_TRUMP))
          && (view.contract().points() == 20 || called.suit() != Suit.SPADE)) {
        return low(position.inSuit(called.suit()), "지정 문양의 물패로 프렌드를 찾아 반환받습니다.");
      }
    }
    // 스페이드 반환을 K로 회수한 직후에는 외부 상위가 남을 때만 마이티로 한 번 더 뽑습니다.
    if (!first && view.contract().points() == 20 && position.initialLength(Suit.SPADE) >= 5) {
      var last = view.context().history().get(view.context().history().size() - 1);
      if (Rules.conditionsFor(Trump.NO_TRUMP, last.number(), last.cards().get(0).action())
          .leadSuit() == Suit.SPADE && last.winner().equals(view.self())) {
        var spades = position.inSuit(Suit.SPADE);
        if (!spades.isEmpty()) {
          var high = PlaySupport.highest(spades);
          if (position.facts().isSuitTop(high.card())) {
            return Optional.of(new PlayDecision(high, "승격된 스페이드를 먼저 처리합니다."));
          }
          var mighty = playCard(position.legal(), Rules.mighty(Trump.NO_TRUMP),
              "반환 문양의 외부 상위를 마이티로 소모시킵니다.");
          if (mighty.isPresent()) {
            return mighty;
          }
        }
      }
    }
    if (!main.isEmpty()) {
      var high = PlaySupport.highest(main);
      if (position.facts().isSuitTop(high.card())) {
        return Optional.of(new PlayDecision(high, "주력의 남은 탑을 위에서부터 소진합니다."));
      }
      if (!first && plan.joker()) {
        return position.legal().stream().filter(a -> a instanceof JokerLead lead
            && lead.declaredSuit() == plan.primary()).findFirst()
            .map(a -> new PlayDecision(a, "주력의 상위가 남아 조커로 한 번 더 뽑습니다."));
      }
    }
    if (position.needsFriendCall() && friend.card() instanceof StandardCard called
        && called.suit() == Suit.SPADE && called.rank() != Rank.ACE) {
      return low(position.inSuit(Suit.SPADE), "주력을 처리한 뒤 낮은 스페이드로 프렌드를 찾습니다.");
    }
    return Optional.empty();
  }

  static Optional<PlayDecision> follow(DeclarerPosition position) {
    var view = position.view();
    var facts = position.facts();
    // 곱노 이상의 긴 AT 주력은 KQJ 분산을 가정하지 않습니다. 반환받은 지금
    // 마이티로 한 바퀴를 더 빼고 A와 조커의 소모 기회를 남깁니다.
    if (view.contract().trump() == Trump.NO_TRUMP && view.contract().points() >= 17
        && facts.teams().friendSeat().filter(view.trick().get(0).seat()::equals).isPresent()) {
      var suit = facts.conditions().leadSuit();
      var main = position.inSuit(suit);
      boolean aceTen = main.stream().anyMatch(a -> ((StandardCard) a.card()).rank() == Rank.ACE)
          && main.stream().anyMatch(a -> ((StandardCard) a.card()).rank() == Rank.TEN);
      long honors = facts.cards().outsideCards(suit).stream()
          .filter(c -> c.rank().value() > Rank.TEN.value() && c.rank().value() < Rank.ACE.value())
          .count();
      if (suit != Suit.SPADE && position.initialLength(suit) >= 6 && aceTen && honors >= 2
          && view.hand().contains(Joker.INSTANCE)) {
        var sweep = playCard(facts.winningActions(), Rules.mighty(Trump.NO_TRUMP),
            "긴 AT 주력의 상위가 몰렸을 가능성을 남기고 마이티로 반환을 받아 한 바퀴 더 소모합니다.");
        if (sweep.isPresent()) {
          return sweep;
        }
      }
    }
    var route = route(position);
    if (!facts.teams().isAlly(view.trick().get(0).seat()) || route.isEmpty()) {
      return Optional.empty();
    }
    if (facts.conditions().leadSuit() == Suit.SPADE) {
      return playCard(facts.winningActions(), new StandardCard(Suit.SPADE, Rank.KING),
          "프렌드의 스페이드 반환을 K로 회수해 마이티를 남깁니다.");
    }
    if (facts.conditions().leadSuit() == route.get().primary()
        && (view.trick().get(0).action().card().points() == 0 || view.contract().points() == 20)) {
      var winners = facts.winningActions().stream().filter(position.legal()::contains)
          .filter(a -> a.card() instanceof StandardCard c && c.suit() == facts.conditions().leadSuit())
          .filter(a -> facts.isSuitTop(a.card())).toList();
      if (!winners.isEmpty()) {
        return Optional.of(new PlayDecision(PlaySupport.highest(winners), "반환받은 주력 탑으로 선을 회수합니다."));
      }
    }
    if (view.friendSelection() instanceof CardFriend friend
        && friend.card() instanceof StandardCard called && called.rank() == Rank.ACE
        && called.suit() != Suit.SPADE && facts.allyWinning()
        && facts.isSuitTop(facts.currentWinner().orElseThrow().action().card())
        && view.hand().contains(Rules.mighty(Trump.NO_TRUMP)) && view.hand().contains(Joker.INSTANCE)
        && position.inSuit(facts.conditions().leadSuit()).isEmpty()) {
      return low(position.inSuit(Suit.SPADE), "프렌드의 승리 카드에 물스페이드를 버려 반환을 요청합니다.");
    }
    return Optional.empty();
  }

  private static Optional<PlayDecision> low(List<Action> actions, String reason) {
    return actions.stream().filter(a -> a.card().points() == 0)
        .min(Comparator.comparingInt(a -> ((StandardCard) a.card()).rank().value()))
        .map(a -> new PlayDecision(a, reason));
  }

  private static Optional<PlayDecision> playCard(List<Action> legal, Card card, String reason) {
    return legal.stream().filter(a -> a.card().equals(card)).findFirst()
        .map(a -> new PlayDecision(a, reason));
  }
}
