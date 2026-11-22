package mighty.ai.play.declarer;

import java.util.List;
import mighty.ai.play.PlayDecision;
import mighty.ai.play.PlaySupport;
import mighty.ai.play.SpecialCardPlan;
import mighty.ai.play.TrumpControl;
import mighty.game.action.Action;
import mighty.game.action.JokerLead;
import mighty.game.friend.CardFriend;
import mighty.game.friend.RevealedFriend;
import mighty.game.friend.SpecialFriend;
import mighty.game.rule.Rules;
import mighty.model.Joker;
import mighty.model.Rank;
import mighty.model.StandardCard;
import mighty.model.Suit;
import mighty.model.Trump;

/**
 * 초구 → 호출 → 기루 통제 재평가 → 선 유지 순서로 주공의 리드를 결정합니다.
 */
final class DeclarerLeadStrategy {

  private DeclarerLeadStrategy() {
  }

  static PlayDecision choose(DeclarerPosition position) {
    var view = position.view();
    List<Action> jokers = position.legal().stream().filter(action -> action instanceof JokerLead)
        .toList();
    var bidder = HighNoTrumpPlay.leadToBidder(position);
    if (bidder.isPresent()) {
      return bidder.get();
    }
    var spread = NoTrumpOperations.cashSideTopsBeforeFriend(position);
    if (spread.isPresent()) {
      return spread.get();
    }
    if (view.trickNumber().isFirst() && view.contract().trump() == Trump.NO_TRUMP) {
      var plan = NoTrumpOperations.openingPlan(position);
      if (plan.opening().isPresent()) {
        var action = new mighty.game.action.StandardPlay(plan.opening().get(), false);
        if (!position.legal().contains(action)) {
          throw new IllegalStateException("평가한 노기루 초구를 실행할 수 없습니다.");
        }
        return decision(action, plan.purpose().explanation());
      }
    }
    var continuation = NoTrumpOperations.continueOpeningPlan(position);
    if (continuation.isPresent()) {
      return continuation.get();
    }
    var high = HighNoTrumpPlay.lead(position);
    if (high.isPresent()) {
      return high.get();
    }
    if (view.trickNumber().value() == 1 && view.friendSelection() == SpecialFriend.FIRST_TRICK
        && view.contract().trump() != Trump.NO_TRUMP) {
      var requested = NoTrumpOperations.openingReturnSuit(position);
      var matching = requested.stream().flatMap(suit -> position.inSuit(suit).stream())
          .filter(
              action -> action.card().points() == 0 && !position.facts().isSuitTop(action.card()))
          .toList();
      if (!matching.isEmpty()) {
        return decision(position.lowSide(matching), "공개한 반환 문양에서 초구 프렌드를 찾습니다.");
      }
      var offerings = position.legal().stream()
          .filter(action -> action.card() instanceof StandardCard
              && !action.card().equals(Rules.mighty(view.contract().trump()))
              && !position.facts().isSuitTop(action.card())).toList();
      if (!offerings.isEmpty()) {
        return decision(position.lowSide(offerings), "초구를 프렌드에게 넘길 수 있는 카드로 시작합니다.");
      }
    }
    var middleFriend = NoTrumpOperations.callMiddleFriend(position);
    if (middleFriend.isPresent()) {
      return middleFriend.get();
    }
    if (view.trickNumber().value() == 1 && view.contract().trump() == Trump.NO_TRUMP
        && view.friendSelection() == SpecialFriend.NONE && !view.hand().contains(Joker.INSTANCE)
        && view.hand().contains(Rules.gun(Trump.NO_TRUMP))) {
      var safeOpening = position.sides().stream()
          .filter(action -> position.facts().isSuitTop(action.card()))
          .filter(action -> action.card() instanceof StandardCard card
              && card.suit() != Rules.gun(Trump.NO_TRUMP).suit())
          .findFirst();
      if (safeOpening.isPresent()) {
        return decision(safeOpening.get(), "솔에서는 일반 탑카로 초구를 확보한 뒤 조커를 부릅니다.");
      }
    }
    var call = mighty.ai.play.JokerCallPlan.call(view, position.facts(), position.legal());
    if (call.isPresent()) {
      return decision(call.get(), "조커가 자기 손에 없으므로 총으로 외부 조커 사용을 강제합니다.");
    }
    var jokerRoute = NoTrumpOperations.longJokerFriendRoute(position);
    if (jokerRoute.isPresent()) {
      return jokerRoute.get();
    }
    var sweep = NoTrumpOperations.sweepReachableBlocker(position);
    if (sweep.isPresent()) {
      return sweep.get();
    }
    var earlyLoss = NoTrumpOperations.shedBeforeOppositionDiscards(position);
    if (earlyLoss.isPresent()) {
      return earlyLoss.get();
    }
    var sweepWithReserve = NoTrumpOperations.sweepPrimaryWithMightyReserve(position);
    if (sweepWithReserve.isPresent()) {
      return sweepWithReserve.get();
    }
    var mightyFirst = SpecialCardPlan.leadMighty(position.legal(), view, position.facts());
    if (mightyFirst.isPresent()) {
      return decision(mightyFirst.get(), "마이티를 먼저 사용해 문양 공격에 소모되지 않게 하고 조커의 문양 선택권을 남깁니다.");
    }
    TrumpControl control = new TrumpControl(view, position.facts());
    var aceProbe = control.aceProbe(position.legal());
    boolean probingDone = view.context().history().stream()
        .anyMatch(trick -> trick.leader().equals(view.self())
            && trick.cards().get(0).action().card() instanceof StandardCard card
            && view.contract().trump().matches(card.suit()) && card.rank() == Rank.ACE);
    if (!jokers.isEmpty() && aceProbe.isPresent() && !probingDone
        && !control.shouldPreserveLastTrump()
        && mighty.ai.play.JokerSuitPlan.enemiesMayHoldTrump(view, position.facts())) {
      return decision(aceProbe.get(), "조커를 기루로 고정하기 전에 기루 A로 상위 소모와 지원을 확인합니다.");
    }
    if (view.trickNumber().value() == 1) {
      var delayed = NoTrumpOperations.delaySupport(position);
      if (delayed.isPresent()) {
        return delayed.get();
      }
      return opening(position);
    }
    if (view.contract().trump() == Trump.NO_TRUMP) {
      return noTrump(position, jokers);
    }
    return trump(position, jokers);
  }

  /**
   * 초구프·자력 초구·마이티 초구를 구분하며 조커로 초구를 이길 계획을 만들지 않습니다.
   */
  private static PlayDecision opening(DeclarerPosition position) {
    var view = position.view();
    List<Action> sides = position.sides();
    if (view.contract().trump() != Trump.NO_TRUMP) {
      var hand = mighty.ai.bidding.BiddingHand.from(view.hand());
      var buried = ((mighty.game.observation.DeclarerInfo) view.privateInfo()).discardedCards();
      var planned = mighty.ai.planning.SuitFriendPlan.choose(hand, buried, view.contract().trump());
      if (planned.isPresent() && planned.get().selection().equals(view.friendSelection())) {
        var low = position.inSuit(planned.get().openingSuit()).stream()
            .filter(action -> action.card().points() == 0
                && !action.card().equals(Rules.gun(view.contract().trump()))).toList();
        if (!low.isEmpty()) {
          return decision(PlaySupport.cheapest(low, view),
              "부기루의 연결을 살릴 수 있는 문양에서 초구 지원을 요청합니다.");
        }
      }
    }
    if (position.calls(Rules.mighty(view.contract().trump()))) {
      var middle = SideOpeningPlan.feed(position);
      if (middle.isPresent()) {
        return decision(middle.get(),
            "부기루의 중간 점수를 마이티 프렌드에게 처리하고 높은 카드와 탐색 숫자를 남깁니다.");
      }
    }
    // 풀노의 사이드 물패를 초구 마이티 프렌드에게 맡기는 경로입니다.
    List<Action> water = sides.stream()
        .filter(action -> ((StandardCard) action.card()).suit() != position.primarySuit()
            && action.card().points() == 0 && !position.facts().isSuitTop(action.card())).toList();
    if (view.contract().trump() == Trump.NO_TRUMP && view.contract().points() == 20
        && position.calls(Rules.mighty(view.contract().trump())) && !water.isEmpty()) {
      return decision(position.lowSide(water), "풀노의 사이드 물패를 초구 마이티 프렌드에게 맡겨 처리합니다.");
    }
    List<Action> tops = sides.stream().filter(action -> position.facts().isSuitTop(action.card()))
        .toList();
    if (!tops.isEmpty()) {
      // 마이티 프렌드는 주공의 A를 모릅니다. K/Q 초구로 불필요한 마이티를 요구하지 않습니다.
      if (position.calls(Rules.mighty(view.contract().trump()))) {
        var ace = tops.stream().filter(a -> ((StandardCard) a.card()).rank() == Rank.ACE)
            .findFirst();
        if (ace.isPresent()) {
          return decision(ace.get(), "마이티 프렌드에게는 A 초구로 확실한 승리를 알립니다.");
        }
        var low = sides.stream().filter(a -> a.card().points() == 0).toList();
        if (!low.isEmpty()) {
          return decision(position.lowSide(low),
              "주공의 KQ를 친구가 아는 탑카로 가정하지 않고 낮은 물패로 마이티 지원을 요청합니다.");
        }
      }
      return decision(PlaySupport.cheapest(tops, view),
          "사이드의 상위 연결 카드로 초구와 반환 경로를 확보합니다. 간 위험은 남습니다.");
    }
    if (view.hand().contains(Rules.mighty(view.contract().trump())) && position.calls(
        Joker.INSTANCE)) {
      return decision(position.legal().stream()
          .filter(action -> action.card().equals(Rules.mighty(view.contract().trump())))
          .findFirst().orElseThrow(), "다른 자력 초구가 없어 마이티로 선을 확보한 뒤 조커 프렌드를 찾습니다.");
    }
    if (position.needsFriendCall() && !sides.isEmpty()) {
      return decision(position.lowSide(sides), "자력 초구가 부족해 낮은 사이드로 지정 프렌드의 지원을 요청합니다.");
    }
    return fallback(position);
  }

  private static PlayDecision noTrump(DeclarerPosition position, List<Action> jokers) {
    var view = position.view();
    var returnedSuit = NoTrumpOperations.openingReturnSuit(position);
    if (returnedSuit.isPresent()) {
      var route = NoTrumpOperations.continueOpeningReturn(position, jokers, returnedSuit.get());
      if (route.isPresent()) {
        return route.get();
      }
    }
    List<Action> main = position.inSuit(position.primarySuit());
    var delayed = NoTrumpOperations.delaySupport(position);
    if (delayed.isPresent()) {
      return delayed.get();
    }
    var splitContinuation = NoTrumpOperations.continueSplitWithJoker(position);
    if (splitContinuation.isPresent()) {
      return splitContinuation.get();
    }
    if (!jokers.isEmpty() && !main.isEmpty() && SpecialCardPlan.usedOwnMighty(view)
        && main.stream().noneMatch(action -> position.facts().isSuitTop(action.card()))) {
      return joker(jokers, position.primarySuit(),
          "마이티를 사용한 뒤 조커로 주력을 지정해 외부 문양 소모와 선 유지를 연결합니다.");
    }
    var sideProbe = NoTrumpOperations.probeSideWithJokerReserve(position);
    if (sideProbe.isPresent()) {
      return sideProbe.get();
    }
    var removalRoute = NoTrumpOperations.continueMightyRemovalRoute(position);
    if (removalRoute.isPresent()) {
      return removalRoute.get();
    }
    List<Action> calling = main;
    if (position.view().friendSelection() instanceof CardFriend friend
        && friend.card() instanceof StandardCard card
        && !card.equals(Rules.mighty(position.view().contract().trump()))) {
      calling = position.inSuit(card.suit());
    }
    if (position.needsFriendCall() && !calling.isEmpty()) {
      return decision(PlaySupport.cheapest(calling, position.view()),
          "지정 카드의 문양 또는 주력의 낮은 카드로 프렌드를 찾고 반환 자원을 남깁니다.");
    }
    var probe = NoTrumpOperations.probeWithJokerReserve(position);
    if (probe.isPresent()) {
      return probe.get();
    }
    var development = NoTrumpOperations.developPrimary(position);
    if (development.isPresent()) {
      return development.get();
    }
    List<Action> masters = position.ordinary().stream().filter(position.facts()::isMasterLead)
        .toList();
    if (!masters.isEmpty()) {
      List<Action> mainMasters = masters.stream()
          .filter(action -> ((StandardCard) action.card()).suit() == position.primarySuit())
          .toList();
      return decision(PlaySupport.highest(mainMasters.isEmpty() ? masters : mainMasters),
          "카운팅으로 굳은 주력 승리 카드를 선이 있을 때 사용합니다.");
    }
    List<Action> tops = main.stream().filter(action -> position.facts().isSuitTop(action.card()))
        .toList();
    if (!tops.isEmpty()) {
      return decision(PlaySupport.cheapest(tops, position.view()),
          "주력의 상위 연결로 상대 문양을 소모시킵니다. 외부 특수카드는 별도 위험입니다.");
    }
    if (!jokers.isEmpty() && !main.isEmpty()) {
      return joker(jokers, position.primarySuit(), "조커를 주력에 사용해 상위 카드를 소모시키고 긴 문양을 연결합니다.");
    }
    return fallback(position);
  }

  private static PlayDecision trump(DeclarerPosition position, List<Action> jokers) {
    var view = position.view();
    List<Action> trumps = position.trumps();
    TrumpControl control = new TrumpControl(view, position.facts());
    if (position.calls(Rules.mighty(view.contract().trump())) && position.enemiesMayHaveTrump()
        && trumps.size() >= 3) {
      var high = PlaySupport.highest(trumps);
      var card = (StandardCard) high.card();
      java.util.List<mighty.model.Card> removed = new java.util.ArrayList<>(
          position.facts().cards().publicHistory().cards());
      removed.addAll(((mighty.game.observation.DeclarerInfo) view.privateInfo()).discardedCards());
      var connected = mighty.ai.analysis.HoldingControl.analyze(view.hand(), removed,
          view.contract().trump(),
          card.suit());
      if (connected.blockers() == 1 && connected.connected() >= 2) {
        return decision(high,
            "연결된 기루의 유일한 상위를 먼저 확인하고 마이티 지원은 부기루에 남깁니다.");
      }
    }
    if (position.justRuffed() && position.calls(Joker.INSTANCE)) {
      var development = new DeclarerPlan(position).developments().stream().findFirst();
      if (development.isPresent()) {
        return decision(new mighty.game.action.StandardPlay(
            development.get().probingCard(), false), "간으로 되찾은 선에서 부기루로 조커 프렌드를 찾습니다.");
      }
      if (position.enemiesMayHaveTrump() && !trumps.isEmpty()
          && !control.shouldPreserveLastTrump()) {
        return decision(PlaySupport.cheapest(trumps, view), "간으로 되찾은 선에서 기루로 조커 프렌드를 찾습니다.");
      }
    }
    var outside = control.outside();
    if (!jokers.isEmpty() && trumps.size() >= 2 && outside.size() == 1
        && position.enemiesMayHaveTrump()
        && outside.get(0).rank().value() > ((StandardCard) PlaySupport.highest(trumps)
        .card()).rank().value()
        && !position.facts().cards().outsideCards()
        .contains(Rules.mighty(view.contract().trump()))) {
      return joker(jokers, outside.get(0).suit(),
          "조커로 마지막 외부 상위 기루를 빼고 남은 기루를 모두 살립니다.");
    }
    // 공개 야당 무기루 뒤 친구의 기루만 빼는 순회를 중지합니다.
    if (!position.enemiesMayHaveTrump() && !jokers.isEmpty() && view.context()
        .friendStatus() instanceof RevealedFriend friend
        && !trumps.isEmpty()) {
      List<Action> water = position.sides().stream()
          .filter(action -> !position.facts().isSuitTop(action.card())
              && !position.facts().cards()
              .isVoid(friend.seat(), ((StandardCard) action.card()).suit())).toList();
      boolean friendMayHaveTrump = position.facts().cards().outsideCards().stream()
          .anyMatch(card -> card instanceof StandardCard standard
              && view.contract().trump().matches(standard.suit())
              && !position.facts().cards().isVoid(friend.seat(), standard.suit()));
      if (friendMayHaveTrump && !water.isEmpty()) {
        Suit suit = ((StandardCard) position.lowSide(water).card()).suit();
        return joker(jokers, suit, "야당은 공개 무기루이므로 조커로 친구의 사이드를 소모시켜 후속 간 지원을 준비합니다.");
      }
    }
    if (!position.enemiesMayHaveTrump() && !jokers.isEmpty() && !view.hand()
        .contains(Rules.mighty(view.contract().trump()))
        && !position.sides().isEmpty()) {
      return decision(PlaySupport.jokerLead(jokers, view, position.facts()),
          "야당 기루가 정리된 뒤 조커로 사이드 소모와 선 유지를 연결합니다.");
    }
    if (control.shouldPreserveLastTrump() && !position.sides().isEmpty()) {
      List<Action> tops = position.sides().stream()
          .filter(action -> position.facts().isSuitTop(action.card())).toList();
      Action side = tops.isEmpty() ? position.lowSide(position.sides()) : PlaySupport.highest(tops);
      return decision(side, "남은 두 장의 기루를 빼면 회수 수단이나 선을 잃으므로 사이드를 먼저 운영합니다.");
    }
    if (position.enemiesMayHaveTrump() && !trumps.isEmpty()) {
      boolean trumpStrengthChanged = position.facts().cards().publicHistory().completedPlays()
          .stream()
          .anyMatch(p -> p.action().card() instanceof StandardCard c
              && view.contract().trump().matches(c.suit()) && c.points() > 0);
      Action next = PlaySupport.highest(trumps);
      if (position.facts().isSuitTop(next.card())
          && ((!position.calls(Joker.INSTANCE)
          && trumps.stream().filter(a -> position.facts().isSuitTop(a.card())).count() >= 3)
          || trumpStrengthChanged || !position.needsFriendCall() || !jokers.isEmpty())) {
        return decision(next, "카운팅으로 승격된 최고 기루를 연결하고 불필요한 프렌드 호출을 피합니다.");
      }
      if (!jokers.isEmpty() && trumpStrengthChanged && !position.facts().isSuitTop(next.card())) {
        return joker(jokers, mainSuit(position), "A 이후에도 남은 상위를 조커로 소모시켜 기루를 연결합니다.");
      }
      if (!jokers.isEmpty() && SpecialCardPlan.usedOwnMighty(view)) {
        return joker(jokers, mainSuit(position), "마이티 다음 조커로 기루를 지정해 두 특수카드를 기루 소모에 연결합니다.");
      }
      if (position.needsFriendCall()) {
        java.util.List<mighty.model.Card> initial = new java.util.ArrayList<>(view.hand());
        view.context().history()
            .forEach(t -> t.cards().stream().filter(p -> p.seat().equals(view.self()))
                .forEach(p -> initial.add(p.action().card())));
        if (position.calls(Joker.INSTANCE) && mighty.ai.bidding.BiddingHand.from(initial)
            .hasSolidTrumpCore(view.contract().trump()) && !position.sides().isEmpty()) {
          List<Action> water = position.sides().stream()
              .filter(a -> !position.facts().isSuitTop(a.card())).toList();
          if (!water.isEmpty()) {
            return decision(position.lowSide(water),
                "기루는 AKQJ와 장수로 운영하고 조커 프렌드는 사이드 물패를 처리하도록 찾습니다.");
          }
        }
        List<Action> calling = trumps;
        if (position.calls(Joker.INSTANCE) && view.hand()
            .contains(Rules.mighty(view.contract().trump()))) {
          List<Action> points = trumps.stream().filter(action -> action.card().points() > 0
              && ((StandardCard) action.card()).rank().value() < Rank.KING.value()).toList();
          if (!points.isEmpty()) {
            calling = points;
          }
        }
        return decision(PlaySupport.cheapest(calling, view), "기루로 프렌드를 호출하고 반환을 회수할 자원을 남깁니다.");
      }
      var pressure = control.pressureLead(trumps);
      if (pressure.isPresent()) {
        return decision(pressure.get(),
            "잔여 장수와 프렌드의 마이티 지원을 보고 흩어진 기루의 상위를 더 확인합니다.");
      }
      Action highest = PlaySupport.highest(trumps);
      if (!position.facts().isSuitTop(highest.card()) && !position.sides().isEmpty()) {
        var sideTops = position.sides().stream().filter(a -> position.facts().isSuitTop(a.card()))
            .toList();
        return decision(
            sideTops.isEmpty() ? position.lowSide(position.sides()) : PlaySupport.cheapest(sideTops,
                view),
            "추가 교환의 장수나 지원이 부족해 사이드로 전환하고 회수 수단을 유지합니다.");
      }
      return decision(PlaySupport.highest(trumps), "야당에게 남을 수 있는 기루를 높은 기루로 소모시킵니다.");
    }
    var availableTops = position.sides().stream().filter(a -> position.facts().isSuitTop(a.card()))
        .toList();
    if (!availableTops.isEmpty()) {
      return decision(PlaySupport.cheapest(availableTops, view),
          "선이 있을 때 사이드 탑카를 시도해 문양을 소모시키고 연결을 만듭니다.");
    }
    var planned = new DeclarerPlan(position).lead();
    if (planned.isPresent()) {
      return planned.get();
    }
    List<Action> sideTops = position.sides().stream()
        .filter(action -> position.facts().isSuitTop(action.card())).toList();
    if (!sideTops.isEmpty()) {
      return decision(PlaySupport.cheapest(sideTops, view), "야당 기루 정리가 필요 없어 사이드 승리 카드를 먼저 사용합니다.");
    }
    List<Action> masters = position.ordinary().stream().filter(position.facts()::isMasterLead)
        .toList();
    if (!masters.isEmpty()) {
      return decision(PlaySupport.highest(masters), "공개 카운팅으로 굳은 승리 카드를 사용합니다.");
    }
    return fallback(position);
  }

  private static Suit mainSuit(DeclarerPosition position) {
    return java.util.Arrays.stream(Suit.values())
        .filter(position.view().contract().trump()::matches)
        .findFirst().orElseGet(position::primarySuit);
  }

  private static PlayDecision joker(List<Action> jokers, Suit suit, String reason) {
    Action action = jokers.stream()
        .filter(candidate -> ((JokerLead) candidate).declaredSuit() == suit)
        .findFirst().orElseThrow();
    return decision(action, reason);
  }

  private static PlayDecision fallback(DeclarerPosition position) {
    if (!position.ordinary().isEmpty()) {
      return decision(position.lowSide(position.ordinary()), "확실한 연결이 없어 낮은 일반 카드로 회수 자원을 보존합니다.");
    }
    return decision(PlaySupport.cheapest(position.legal(), position.view()), "남은 합법 행동으로 진행합니다.");
  }

  private static PlayDecision decision(Action action, String reason) {
    return new PlayDecision(action, reason);
  }

}
