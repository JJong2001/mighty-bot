package mighty.ai.play.declarer;

import java.util.*;
import mighty.ai.bidding.BiddingHand;
import mighty.ai.planning.*;
import mighty.ai.play.PlaySupport;
import mighty.ai.play.SpecialCardPlan;
import mighty.game.action.*;
import mighty.game.friend.CardFriend;
import mighty.game.observation.DeclarerInfo;
import mighty.game.rule.Rules;
import mighty.game.trick.PublicTrick;
import mighty.model.*;

/**
 * 비딩 때 사용한 자기 운영 계획을 공개 진행으로 다시 확인합니다.
 */
final class NoTrumpOperations {

  private NoTrumpOperations() {
  }

  /**
   * 자기 초기 손패와 공개 공약으로 동일한 불변 계획을 복원합니다.
   */
  static mighty.ai.planning.NoTrumpOpeningPlan openingPlan(DeclarerPosition position) {
    var view = position.view();
    List<Card> initial = initialCards(position);
    return mighty.ai.planning.NoTrumpOpeningPlanner.plan(BiddingHand.from(initial),
        ((DeclarerInfo) view.privateInfo()).discardedCards(), view.friendSelection(),
        view.contract().points(), view.context().contractHistory());
  }

  private static List<Card> initialCards(DeclarerPosition position) {
    List<Card> initial = new ArrayList<>(position.view().hand());
    position.facts().cards().publicHistory().completedPlays().stream()
        .filter(play -> play.seat().equals(position.view().self()))
        .forEach(play -> initial.add(play.action().card()));
    return initial;
  }

  static Optional<mighty.ai.play.PlayDecision> runPrimaryBeforeMightyFriend(
      DeclarerPosition position) {
    var view = position.view();
    if (view.contract().trump() != Trump.NO_TRUMP || view.contract().points() != 15
        || !position.calls(Rules.mighty(Trump.NO_TRUMP))) {
      return Optional.empty();
    }
    var primary = NoTrumpPlanner.jokerSweepSuit(BiddingHand.from(initialCards(position)));
    if (primary.isEmpty()) {
      return Optional.empty();
    }
    Suit suit = primary.get();
    for (var trick : view.context().history()) {
      var lead = trick.cards().get(0).action();
      boolean sameSuit = lead.card() instanceof StandardCard card && card.suit() == suit
          || lead instanceof JokerLead joker && joker.declaredSuit() == suit;
      if (!sameSuit || !trick.leader().equals(view.self()) || !trick.winner().equals(view.self())) {
        return Optional.empty();
      }
    }
    var held = position.inSuit(suit);
    if (held.isEmpty()) {
      return Optional.empty();
    }
    var highest = PlaySupport.highest(held);
    var outside = position.facts().cards().outsideCards(suit).stream()
        .filter(card -> !card.equals(Rules.mighty(Trump.NO_TRUMP)))
        .filter(card -> card.rank().value() > ((StandardCard) highest.card()).rank().value())
        .count();
    if (outside == 0) {
      return Optional.of(new mighty.ai.play.PlayDecision(highest,
          "주력의 일반 탑을 이어서 돌리고 마이티 프렌드 호출을 늦춥니다."));
    }
    if (outside == 1 && highest.card().points() > 0) {
      return position.legal().stream().filter(action -> action instanceof JokerLead lead
          && lead.declaredSuit() == suit).findFirst().map(action -> new mighty.ai.play.PlayDecision(
          action, "주력 점수 카드 위에 한 장만 남아 조커로 그 문양을 돌립니다."));
    }
    return Optional.empty();
  }

  private static NoTrumpPlan initialPlan(DeclarerPosition position) {
    var view = position.view();
    List<Card> initial = initialCards(position);
    Card friend =
        view.friendSelection() instanceof CardFriend f ? f.card() : Rules.mighty(Trump.NO_TRUMP);
    var buried = ((DeclarerInfo) view.privateInfo()).discardedCards();
    return NoTrumpPlanner.withFriend(BiddingHand.from(initial), buried, friend);
  }

  /**
   * 마이티 제거 뒤에는 주력 탑카를 잇고, 조커는 회수 수단을 남긴 다른 문양에서 찾습니다.
   */
  static Optional<mighty.ai.play.PlayDecision> continueMightyRemovalRoute(
      DeclarerPosition position) {
    if (!mighty.ai.play.FriendSupportIntent.mightyRemovalRoute(position.view())) {
      return Optional.empty();
    }
    var facts = position.facts();
    var tops = position.inSuit(Rules.mighty(Trump.NO_TRUMP).suit()).stream()
        .filter(action -> facts.isSuitTop(action.card())).toList();
    if (!tops.isEmpty()) {
      return Optional.of(new mighty.ai.play.PlayDecision(PlaySupport.highest(tops),
          "마이티가 제거된 주력은 남은 탑카부터 돌리고 조커를 다른 문양 회수에 남깁니다."));
    }
    if (!position.calls(Joker.INSTANCE)) {
      return Optional.empty();
    }
    var requests = position.ordinary().stream()
        .filter(action -> action.card().points() == 0 && !facts.isSuitTop(action.card()))
        .filter(action -> {
          Suit suit = ((StandardCard) action.card()).suit();
          return position.inSuit(suit).stream().anyMatch(held -> !held.card().equals(action.card())
              && facts.isSuitTop(held.card()));
        })
        .toList();
    if (requests.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new mighty.ai.play.PlayDecision(position.lowSide(requests),
        "조커 지원 뒤 같은 문양의 자기 탑카로 선을 돌려받을 수 있는 곳에서 찾습니다."));
  }

  /**
   * 프렌드의 추가 탑카를 가정하지 않고 여덟 구가 자체 연결되는 경우에만 호출을 미룹니다.
   */
  static Optional<mighty.ai.play.PlayDecision> delaySupport(DeclarerPosition position) {
    var view = position.view();
    if (view.contract().trump() != Trump.NO_TRUMP || !position.calls(Joker.INSTANCE)
        || view.trickNumber().value() >= 9 || !initialPlan(position).canDelaySupport()) {
      return Optional.empty();
    }
    List<Action> ordinary = position.ordinary().stream().filter(a ->
        position.facts().isSuitTop(a.card()) || primaryConnected(position, a)).toList();
    if (!ordinary.isEmpty()) {
      return Optional.of(new mighty.ai.play.PlayDecision(
          mighty.ai.play.PlaySupport.highest(ordinary),
          "자력으로 여덟 구를 연결할 계획이 있어 프렌드 호출을 늦추고 승리 카드를 먼저 사용합니다."));
    }
    return position.legal().stream().filter(a -> a.card().equals(Rules.mighty(Trump.NO_TRUMP)))
        .findFirst()
        .map(a -> new mighty.ai.play.PlayDecision(a, "남은 자력 승리인 마이티를 사용하고 아홉 구에 조커 프렌드를 찾습니다."));
  }

  /**
   * 긴 주력은 상위 소모 결과를 보고 조커를 부를 문양을 고릅니다.
   */
  static Optional<mighty.ai.play.PlayDecision> longJokerFriendRoute(DeclarerPosition position) {
    var view = position.view();
    var facts = position.facts();
    var primary = position.primarySuit();
    if (view.contract().trump() != Trump.NO_TRUMP || !position.calls(Joker.INSTANCE)
        || !view.hand().contains(Rules.mighty(Trump.NO_TRUMP))
        || position.initialLength(primary) < 7 || initialPlan(position).canDelaySupport()) {
      return Optional.empty();
    }
    var main = position.inSuit(primary);
    var water = position.sides().stream()
        .filter(action -> ((StandardCard) action.card()).suit() != primary)
        .filter(action -> action.card().points() == 0 && !facts.isSuitTop(action.card())).toList();
    if (view.contract().points() == 20 && !water.isEmpty()) {
      if (view.trickNumber().isFirst()) {
        var entry = position.sides().stream()
            .filter(action -> ((StandardCard) action.card()).suit() != primary)
            .filter(action -> ((StandardCard) action.card()).rank() == Rank.ACE)
            .filter(action -> facts.isSuitTop(action.card())).findFirst();
        if (entry.isPresent()) {
          return entry.map(action -> new mighty.ai.play.PlayDecision(action,
              "사이드 A로 초구를 잡고 마이티를 주력 반환에 남깁니다."));
        }
      } else {
        return Optional.of(new mighty.ai.play.PlayDecision(position.lowSide(water),
            "사이드 물패에서 조커를 부르고 긴 주력 반환은 마이티로 받습니다."));
      }
    }
    var top = main.stream()
        .filter(action -> action.card().points() > 0 && facts.isSuitTop(action.card())).toList();
    // 초구 조커는 이길 수 없으므로 불확실한 주력 호출에 초구를 낭비하지 않습니다.
    if (view.trickNumber().isFirst() && top.isEmpty()) {
      var opening = position.ordinary().stream()
          .filter(action -> facts.isSuitTop(action.card()))
          .filter(action -> !action.card().equals(Rules.mighty(Trump.NO_TRUMP)))
          .findFirst();
      if (opening.isPresent()) {
        return Optional.of(new mighty.ai.play.PlayDecision(opening.get(),
            "일반 탑카로 초구를 확보하고 조커가 유효한 차례에 주력 지원을 요청합니다."));
      }
    }
    if (!top.isEmpty()) {
      return Optional.of(new mighty.ai.play.PlayDecision(
          mighty.ai.play.PlaySupport.highest(top), "긴 주력의 탑카로 외부 상위를 먼저 확인합니다."));
    }
    var request =
        main.stream().anyMatch(action -> !facts.isSuitTop(action.card())) || water.isEmpty()
            ? main : water;
    if (request.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new mighty.ai.play.PlayDecision(mighty.ai.play.PlaySupport.cheapest(request, view),
            "남은 주력 상위에 따라 조커 호출 문양을 고릅니다."));
  }

  /**
   * 자기 상위에 지정 카드가 따라 나오기 전에 낮은 카드로 지원을 요청합니다.
   */
  static Optional<mighty.ai.play.PlayDecision> callMiddleFriend(DeclarerPosition position) {
    var view = position.view();
    if (view.contract().trump() != Trump.NO_TRUMP
        || !(view.friendSelection() instanceof CardFriend friend)
        || !(friend.card() instanceof StandardCard target) || !position.calls(target)
        || target.equals(Rules.mighty(Trump.NO_TRUMP))) {
      return Optional.empty();
    }
    var suit = position.inSuit(target.suit());
    boolean mayPullFriend = view.hand().stream()
        .anyMatch(card -> card instanceof StandardCard higher
            && higher.suit() == target.suit() && higher.rank().value() > target.rank().value());
    boolean expendableRequest = view.trickNumber().isFirst()
        && view.hand().contains(Rules.mighty(Trump.NO_TRUMP)) && view.hand()
        .contains(Joker.INSTANCE)
        && suit.size() >= 2 && suit.stream().allMatch(action -> action.card().points() == 0);
    if (!mayPullFriend && !expendableRequest) {
      return Optional.empty();
    }
    var lower = suit.stream().filter(action ->
        ((StandardCard) action.card()).rank().value() < target.rank().value()).toList();
    if (lower.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(
        new mighty.ai.play.PlayDecision(mighty.ai.play.PlaySupport.cheapest(lower, view),
            "자기 탑카에 지정 카드가 빨리기 전에 같은 문양의 낮은 카드로 호출합니다."));
  }

  static Optional<mighty.ai.play.PlayDecision> recoverSplitReturn(DeclarerPosition position) {
    var view = position.view();
    var facts = position.facts();
    if (view.contract().trump() != Trump.NO_TRUMP
        || !(view.friendSelection() instanceof CardFriend friend)
        || !(friend.card() instanceof StandardCard target)
        || !facts.teams().friendSeat().filter(view.trick().get(0).seat()::equals).isPresent()
        || facts.conditions().leadSuit() == target.suit()) {
      return Optional.empty();
    }
    List<Card> initial = new ArrayList<>(view.hand());
    facts.cards().publicHistory().completedPlays().stream()
        .filter(play -> play.seat().equals(view.self()))
        .forEach(play -> initial.add(play.action().card()));
    if (!NoTrumpPlanner.splitControl(BiddingHand.from(initial), target)) {
      return Optional.empty();
    }
    var main = facts.conditions().leadSuit();
    if (initial.stream().filter(card -> card instanceof StandardCard standard
        && standard.suit() == main && !card.equals(Rules.mighty(Trump.NO_TRUMP))).count() < 4) {
      return Optional.empty();
    }
    return facts.winningActions().stream()
        .filter(action -> action.card().equals(Rules.mighty(Trump.NO_TRUMP)))
        .findFirst().map(action -> new mighty.ai.play.PlayDecision(action,
            "지정 프렌드가 맡은 문양과 다른 긴 문양의 반환을 마이티로 회수합니다."));
  }

  /** 지정 문양의 반환을 받은 뒤 주력을 연달아 돌리되, 마이티는 후속 회수에 남깁니다. */
  static Optional<mighty.ai.play.PlayDecision> sweepPrimaryWithMightyReserve(
      DeclarerPosition position) {
    var view = position.view();
    var history = view.context().history();
    if (view.contract().trump() != Trump.NO_TRUMP || view.contract().points() != 15
        || !view.hand().contains(Rules.mighty(Trump.NO_TRUMP))
        || !view.hand().contains(Joker.INSTANCE) || history.size() < 2
        || !(view.friendSelection() instanceof CardFriend friend)
        || !(friend.card() instanceof StandardCard target)
        || !NoTrumpPlanner.splitControl(BiddingHand.from(initialCards(position)), target)) {
      return Optional.empty();
    }
    var primary = position.primarySuit();
    var main = position.inSuit(primary);
    if (main.isEmpty()) {
      return Optional.empty();
    }
    var facts = position.facts();
    var outside = facts.cards().outsideCards(primary);
    var last = history.get(history.size() - 1);
    if (wonFriendReturn(position, last, target.suit())) {
      var top = PlaySupport.highest(main);
      var remaining = main.stream().filter(action -> !action.equals(top)).toList();
      if (!remaining.isEmpty() && facts.isMasterLead(top)
          && shouldSweepBeforeJokerCall(position, remaining, outside)) {
        return Optional.of(new mighty.ai.play.PlayDecision(top,
            "주력 탑카로 한 번 돌려 남은 상위를 확인하고 마이티는 선 회수에 남깁니다."));
      }
    }
    var previous = history.get(history.size() - 2);
    if (wonFriendReturn(position, previous, target.suit())
        && last.leader().equals(view.self()) && last.winner().equals(view.self())
        && last.cards().get(0).action().card() instanceof StandardCard lead
        && lead.suit() == primary && !lead.equals(Rules.mighty(Trump.NO_TRUMP))
        && shouldSweepBeforeJokerCall(position, main, outside)) {
      return position.legal().stream().filter(action ->
              action instanceof JokerLead joker && joker.declaredSuit() == primary)
          .findFirst().map(action -> new mighty.ai.play.PlayDecision(action,
              "낮은 주력으로 선을 내주기 전에 조커컴을 하고 마이티를 회수에 남깁니다."));
    }
    return Optional.empty();
  }

  private static boolean wonFriendReturn(DeclarerPosition position, PublicTrick trick, Suit suit) {
    return trick.winner().equals(position.view().self())
        && position.facts().teams().friendSeat().filter(trick.leader()::equals).isPresent()
        && trick.cards().get(0).action() instanceof StandardPlay lead
        && lead.card().suit() == suit;
  }

  /**
   * 두 문양을 나눠 맡긴 뒤 마이티와 일반 탑카로 소모한 결과를 보고 조커의 용도를 고릅니다.
   */
  static Optional<mighty.ai.play.PlayDecision> continueSplitWithJoker(DeclarerPosition position) {
    var view = position.view();
    var facts = position.facts();
    if (!splitPlanStillHasJoker(position)) {
      return Optional.empty();
    }
    var suit = position.primarySuit();
    var main = position.inSuit(suit);
    if (main.isEmpty() || main.stream().anyMatch(action -> facts.isSuitTop(action.card()))) {
      return Optional.empty();
    }
    var outside = facts.cards().outsideCards(suit);
    boolean useBeforeCall = outside.size() > 1
        && shouldSweepBeforeJokerCall(position, main, outside);
    if (outside.size() == 1 || useBeforeCall) {
      return position.legal().stream().filter(action -> action instanceof JokerLead lead
              && lead.declaredSuit() == suit).findFirst()
          .map(action -> new mighty.ai.play.PlayDecision(action, useBeforeCall
              ? "낮게 열면 주력의 마지막 상위가 남고 총을 맞을 수 있어 먼저 조커컴으로 압박합니다."
              : "주력에 남은 상위 한 장을 조커로 빼고 나머지 주력을 연결합니다."));
    }
    return Optional.of(
        new mighty.ai.play.PlayDecision(mighty.ai.play.PlaySupport.cheapest(main, view),
            "조커컴 한 번으로 주력이 정리되지 않을 수 있어 낮게 돌리고 조커를 회수에 남깁니다."));
  }

  private static boolean shouldSweepBeforeJokerCall(DeclarerPosition position, List<Action> main,
      List<StandardCard> outside) {
    var view = position.view();
    var facts = position.facts();
    var gun = Rules.gun(Trump.NO_TRUMP);
    if (view.contract().points() != 15 || !facts.cards().outsideCards().contains(gun)) {
      return false;
    }
    var highest = (StandardCard) PlaySupport.highest(main).card();
    if (highest.points() == 0 || outside.stream()
        .filter(card -> card.rank().value() > highest.rank().value()).count() != 1) {
      return false;
    }
    // 낮은 카드가 외부에 더 있어도 마지막 점수 저지 카드를 빼면 선을 유지할 수 있습니다.
    return view.context().playOrder().seats().stream()
        .filter(seat -> !seat.equals(view.self()) && !facts.teams().isAlly(seat))
        .anyMatch(seat -> !facts.cards().isVoid(seat, gun.suit()));
  }

  static boolean splitPlanStillHasJoker(DeclarerPosition position) {
    var view = position.view();
    if (!view.hand().contains(Joker.INSTANCE)
        || !mighty.ai.play.SpecialCardPlan.usedOwnMighty(view)
        || !(view.friendSelection() instanceof CardFriend friend)) {
      return false;
    }
    List<Card> initial = initialCards(position);
    return NoTrumpPlanner.splitControl(BiddingHand.from(initial), friend.card());
  }

  private static boolean lostSplitPrimaryProbe(DeclarerPosition position) {
    var history = position.view().context().history();
    if (history.isEmpty() || !splitPlanStillHasJoker(position)) {
      return false;
    }
    var last = history.get(history.size() - 1);
    return last.leader().equals(position.view().self())
        && !position.facts().teams().isAlly(last.winner())
        && last.cards().get(0).action() instanceof StandardPlay lead
        && lead.card().suit() == position.primarySuit();
  }

  /**
   * 탑카로 한 번 더 돌릴 수 없다면 남은 상위를 조커로 뺄 가치부터 확인합니다.
   */
  static Optional<mighty.ai.play.PlayDecision> sweepReachableBlocker(DeclarerPosition position) {
    var view = position.view();
    var facts = position.facts();
    var mighty = Rules.mighty(Trump.NO_TRUMP);
    if (view.contract().trump() != Trump.NO_TRUMP || position.needsFriendCall()
        || facts.cards().outsideCards().contains(mighty)) {
      return Optional.empty();
    }

    Action best = null;
    int bestGain = 0;
    for (Action action : position.legal()) {
      if (!(action instanceof JokerLead joker) || !facts.isMasterLead(action)) {
        continue;
      }
      var held = position.inSuit(joker.declaredSuit());
      if (held.isEmpty() || held.stream().anyMatch(card -> facts.isMasterLead(card))) {
        continue;
      }

      var outside = facts.cards().outsideCards(joker.declaredSuit()).stream()
          .filter(card -> !card.equals(mighty)).toList();
      // 외부 카드가 두 장이면 같은 자리에 몰려 한 장이 남을 수도 있습니다.
      if (outside.isEmpty() || outside.size() > 2) {
        continue;
      }
      int highest = outside.stream().mapToInt(card -> card.rank().value()).max().orElseThrow();
      long gain = held.stream()
          .filter(card -> ((StandardCard) card.card()).rank().value() < highest).count();
      // 두 장이 남았을 때는 조커 한 장으로 두 장 이상이 살아날 가능성이 있어야 시도합니다.
      if (gain > bestGain && (outside.size() == 1 || gain >= 2)) {
        best = action;
        bestGain = (int) gain;
      }
    }
    return Optional.ofNullable(best).map(action -> new mighty.ai.play.PlayDecision(action,
        "물패를 내주기 전에 조커컴으로 남은 상위를 빼서 뒤의 카드를 살립니다."));
  }

  /**
   * 회수가 확보된 한 장짜리 물패는 야당이 점수만 남기기 전에 처리합니다.
   */
  static Optional<mighty.ai.play.PlayDecision> shedBeforeOppositionDiscards(
      DeclarerPosition position) {
    var view = position.view();
    var facts = position.facts();
    if (view.contract().trump() != Trump.NO_TRUMP || position.needsFriendCall()
        || !view.hand().contains(Rules.mighty(Trump.NO_TRUMP))
        || !view.hand().contains(Joker.INSTANCE) || view.trickNumber().value() >= 8
        || new DeclarerPlan(position).lossAllowance() < 3) {
      return Optional.empty();
    }
    var ordinary = position.ordinary();
    var losers = ordinary.stream().filter(action -> !facts.isMasterLead(action)).toList();
    if (losers.size() != 1 || losers.get(0).card().points() != 0
        || losers.get(0).card().equals(Rules.gun(Trump.NO_TRUMP))) {
      return Optional.empty();
    }
    var water = (StandardCard) losers.get(0).card();
    long outsideWater = facts.cards().outsideCards().stream().filter(card ->
        card instanceof StandardCard standard && standard.suit() == water.suit()
            && standard.points() == 0).count();
    if (outsideWater < 3) {
      return Optional.empty();
    }
    return Optional.of(new mighty.ai.play.PlayDecision(losers.get(0),
        "야당에 물패가 남아 있을 때 한 번 맞고 마이티로 선을 회수할 수 있습니다."));
  }

  /**
   * 조커 외에 회수가 없으면 긴 점수 연결로 유일한 상위를 한 번 압박합니다.
   */
  static Optional<mighty.ai.play.PlayDecision> probeWithJokerReserve(DeclarerPosition position) {
    var view = position.view();
    if (position.needsFriendCall() || !view.hand().contains(Joker.INSTANCE)
        || view.hand().contains(Rules.mighty(Trump.NO_TRUMP))
        || position.facts().cards().outsideCards().contains(Rules.mighty(Trump.NO_TRUMP))) {
      return Optional.empty();
    }
    var main = position.inSuit(position.primarySuit());
    var facts = position.facts();
    if (main.isEmpty() || position.ordinary().stream().anyMatch(facts::isMasterLead)) {
      return Optional.empty();
    }
    var removed = new ArrayList<Card>(facts.cards().publicHistory().cards());
    removed.addAll(((DeclarerInfo) view.privateInfo()).discardedCards());
    var control = mighty.ai.analysis.HoldingControl.analyze(view.hand(), removed, Trump.NO_TRUMP,
        position.primarySuit());
    if (control.blockers() != 1 || control.connected() < 4
        || main.stream().filter(action -> action.card().points() > 0).count() < 4) {
      return Optional.empty();
    }
    return Optional.of(new mighty.ai.play.PlayDecision(mighty.ai.play.PlaySupport.highest(main),
        "연속 점수로 유일한 상위를 압박하고 조커는 선 회수에 남깁니다."));
  }

  static Optional<Suit> openingReturnSuit(DeclarerPosition position) {
    var view = position.view();
    if (view.contract().trump() != Trump.NO_TRUMP
        || view.friendSelection() != mighty.game.friend.SpecialFriend.FIRST_TRICK
        || view.contract().points() != mighty.game.bidding.Bid.MAX_POINTS) {
      return Optional.empty();
    }
    return openingPlan(position).returnSuit();
  }

  static Optional<mighty.ai.play.PlayDecision> mightyOnOpeningFriendReturn(
      DeclarerPosition position) {
    var view = position.view();
    var facts = position.facts();
    if (openingReturnSuit(position).filter(facts.conditions().leadSuit()::equals).isEmpty()
        || !facts.teams().friendSeat().filter(view.trick().get(0).seat()::equals).isPresent()) {
      return Optional.empty();
    }
    Suit suit = facts.conditions().leadSuit();
    var mighty = Rules.mighty(Trump.NO_TRUMP);
    long remaining = view.hand().stream().filter(card -> card instanceof StandardCard standard
        && standard.suit() == suit && !card.equals(mighty)).count();
    boolean needsSweep = remaining > 1 && facts.cards().outsideCards(suit).stream()
        .anyMatch(card -> !card.equals(mighty)
            && view.hand().stream().anyMatch(held -> held instanceof StandardCard standard
            && standard.suit() == suit && !held.equals(mighty)
            && card.rank().value() > standard.rank().value()));
    if (!needsSweep) {
      return Optional.empty();
    }
    return facts.winningActions().stream().filter(action -> action.card().equals(mighty))
        .findFirst()
        .map(action -> new mighty.ai.play.PlayDecision(action,
            "초구프의 요청 문양 반환에 마이티를 써서 상위를 소모시키고 일반 탑카를 남깁니다."));
  }

  static Optional<mighty.ai.play.PlayDecision> continueOpeningReturn(
      DeclarerPosition position, List<Action> jokers, Suit returnedSuit
  ) {
    var view = position.view();
    if (!SpecialCardPlan.usedOwnMighty(view) || jokers.isEmpty()) {
      return Optional.empty();
    }
    var facts = position.facts();
    var tops = position.ordinary().stream().filter(facts::isMasterLead).toList();
    var runningTops = tops.stream().filter(action -> {
      Suit suit = ((StandardCard) action.card()).suit();
      return (suit == returnedSuit || suit == position.primarySuit())
          && position.inSuit(suit).stream().anyMatch(held -> !facts.isSuitTop(held.card()));
    }).toList();
    if (!runningTops.isEmpty()) {
      return Optional.of(new mighty.ai.play.PlayDecision(PlaySupport.highest(runningTops),
          "조커를 쓰기 전에 일반 탑카로 남은 외부 장수를 확인합니다."));
    }
    for (Suit suit : List.of(returnedSuit, position.primarySuit())) {
      boolean blocked = position.inSuit(suit).stream()
          .anyMatch(action -> !facts.isSuitTop(action.card()));
      if (blocked) {
        return jokers.stream().filter(action -> ((JokerLead) action).declaredSuit() == suit)
            .findFirst()
            .map(action -> new mighty.ai.play.PlayDecision(action,
                "카운팅으로 상위가 남은 문양에 조커를 사용합니다."));
      }
    }
    if (tops.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new mighty.ai.play.PlayDecision(PlaySupport.highest(tops),
        "두 문양의 탑카가 살아 있어 조커를 남기고 승리를 이어갑니다."));
  }

  /**
   * 주력 반환은 높은 카드의 중복 소모보다 선을 주공에게 연결하는 목적을 먼저 봅니다.
   */
  static Optional<mighty.ai.play.PlayDecision> recoverPrimaryReturn(DeclarerPosition position) {
    var view = position.view();
    var facts = position.facts();
    if (view.contract().trump() != Trump.NO_TRUMP || view.trick().isEmpty()
        || !facts.teams().isAlly(view.trick().get(0).seat())
        || facts.conditions().leadSuit() != position.primarySuit()
        || position.inSuit(position.primarySuit()).size() < 2) {
      return Optional.empty();
    }
    var winner = facts.currentWinner().orElseThrow().action().card();
    if (!facts.allyWinning() || !facts.isSuitTop(winner)
        || winner instanceof Joker || winner.equals(Rules.mighty(Trump.NO_TRUMP))) {
      return Optional.empty();
    }
    return PlaySupport.ordinary(facts.winningActions(), view).stream()
        .filter(action -> ((StandardCard) action.card()).suit() == position.primarySuit())
        .filter(action -> facts.isSuitTop(action.card()))
        .max(Comparator.comparingInt(action -> ((StandardCard) action.card()).rank().value()))
        .map(action -> new mighty.ai.play.PlayDecision(action,
            "프렌드의 주력 반환을 탑카로 받아 남은 주력 승리를 직접 연결합니다."));
  }

  static Optional<mighty.ai.play.PlayDecision> regain(DeclarerPosition position) {
    var view = position.view();
    var facts = position.facts();
    if (view.contract().trump() != Trump.NO_TRUMP) {
      return Optional.empty();
    }
    var winner = facts.currentWinner().orElseThrow();
    if (facts.allyWinning() && (facts.isSuitTop(winner.action().card()) || facts.isSecured(
        winner))) {
      return Optional.empty();
    }
    boolean longJokerReturn = view.friendSelection() instanceof CardFriend friend
        && friend.card() instanceof Joker
        && facts.teams().isAlly(view.trick().get(0).seat())
        && facts.conditions().leadSuit() == position.primarySuit()
        && position.initialLength(position.primarySuit()) >= 7;
    if (longJokerReturn) {
      var mighty = facts.winningActions().stream()
          .filter(action -> action.card().equals(Rules.mighty(Trump.NO_TRUMP))).findFirst();
      if (mighty.isPresent()) {
        return Optional.of(new mighty.ai.play.PlayDecision(mighty.get(),
            "조커 프렌드의 긴 주력 반환을 마이티로 받아 외부 상위를 소모시킵니다."));
      }
    }
    var ordinaryTops = mighty.ai.play.PlaySupport.ordinary(facts.winningActions(), view).stream()
        .filter(a -> facts.isSuitTop(a.card())
            || facts.isSecured(new mighty.game.trick.PublicPlay(view.self(), a))).toList();
    if (!ordinaryTops.isEmpty()) {
      if (facts.teams().isAlly(view.trick().get(0).seat())) {
        return Optional.of(new mighty.ai.play.PlayDecision(
            mighty.ai.play.PlaySupport.highest(ordinaryTops),
            "친구의 불확실한 반환을 일반 탑카로 회수해 주공의 운영을 재개합니다."));
      }
      if (usedPrimaryJoker(position) && facts.conditions().leadSuit() != position.primarySuit()) {
        return Optional.of(new mighty.ai.play.PlayDecision(
            mighty.ai.play.PlaySupport.cheapest(ordinaryTops, view),
            "주력 상위를 빼는 동안 남겨 둔 사이드 탑카로 선을 회수합니다."));
      }
      return Optional.empty();
    }
    var history = view.context().history();
    if (!history.isEmpty()) {
      var last = history.get(history.size() - 1);
      if (last.leader().equals(view.self()) && !facts.teams().isAlly(last.winner())) {
        var mighty = facts.winningActions().stream()
            .filter(action -> action.card().equals(Rules.mighty(Trump.NO_TRUMP))).findFirst();
        if (mighty.isPresent()) {
          return Optional.of(new mighty.ai.play.PlayDecision(mighty.get(),
              "맞으러 낸 뒤 남겨 둔 마이티로 선을 회수합니다."));
        }
      }
    }
    boolean enemyJokerSpent = facts.cards().publicHistory().cards().contains(Joker.INSTANCE);
    boolean friendReturning = facts.teams().isAlly(view.trick().get(0).seat());
    if (enemyJokerSpent || view.contract().points() == 20 || friendReturning
        || lostSideProbe(position) || lostSplitPrimaryProbe(position) || lostConnectedPrimaryProbe(
        position)) {
      var special = facts.winningActions().stream().filter(a ->
          a.card().equals(Rules.mighty(Trump.NO_TRUMP)) || a.card() instanceof Joker).findFirst();
      if (special.isPresent()) {
        return Optional.of(new mighty.ai.play.PlayDecision(special.get(),
            "노기루의 선 단절을 방치하지 않고 남겨 둔 특수카드로 운영을 재개합니다."));
      }
    }
    return Optional.empty();
  }

  private static boolean lostConnectedPrimaryProbe(DeclarerPosition position) {
    var history = position.view().context().history();
    if (history.isEmpty() || !hasMasterPrimaryAndJoker(position)) {
      return false;
    }
    var last = history.get(history.size() - 1);
    return last.leader().equals(position.view().self())
        && !position.facts().teams().isAlly(last.winner())
        && last.cards().get(0).action() instanceof StandardPlay lead
        && lead.card().suit() == position.primarySuit() && lead.card().points() > 0;
  }

  /**
   * 주력이 굳었으면 조커를 선 회수에 남기고, 사이드 연결을 막는 상위를 확인합니다.
   */
  static Optional<mighty.ai.play.PlayDecision> probeSideWithJokerReserve(
      DeclarerPosition position) {
    if (!hasMasterPrimaryAndJoker(position)) {
      return Optional.empty();
    }
    var view = position.view();
    List<Card> removed = new ArrayList<>(position.facts().cards().publicHistory().cards());
    removed.addAll(((DeclarerInfo) view.privateInfo()).discardedCards());
    if (view.friendSelection() instanceof CardFriend friend) {
      // 지정 프렌드 카드는 야당이 가진 상위 장벽으로 세지 않습니다.
      removed.add(friend.card());
    }
    for (Suit suit : Suit.values()) {
      if (suit == position.primarySuit()) {
        continue;
      }
      var control = mighty.ai.analysis.HoldingControl.analyze(view.hand(), removed, Trump.NO_TRUMP,
          suit);
      if (control.blockers() != 1 || control.connected() < 2) {
        continue;
      }
      return Optional.of(
          new mighty.ai.play.PlayDecision(mighty.ai.play.PlaySupport.highest(position.inSuit(suit)),
              "굳은 주력은 그대로 두고 사이드 상위를 확인합니다. 조커는 선 회수에 남깁니다."));
    }
    return Optional.empty();
  }

  private static boolean hasMasterPrimaryAndJoker(DeclarerPosition position) {
    var hand = position.view().hand();
    Suit primary = position.primarySuit();
    var main = hand.stream().filter(StandardCard.class::isInstance).map(StandardCard.class::cast)
        .filter(card -> card.suit() == primary && !card.equals(Rules.mighty(Trump.NO_TRUMP)))
        .toList();
    return hand.contains(Joker.INSTANCE) && !main.isEmpty()
        && main.stream()
        .allMatch(card -> position.facts().isMasterLead(new StandardPlay(card, false)));
  }

  private static boolean lostSideProbe(DeclarerPosition position) {
    var view = position.view();
    if (!hasMasterPrimaryAndJoker(position) || view.context().history().isEmpty()) {
      return false;
    }
    var last = view.context().history().get(view.context().history().size() - 1);
    return last.leader().equals(view.self())
        && !position.facts().teams().isAlly(last.winner())
        && last.cards().get(0).action() instanceof StandardPlay lead
        && lead.card().suit() != position.primarySuit();
  }

  /**
   * 주력을 반환받았으면 사이드 탑카를 회수용으로 남기고 주력 상위부터 소모시킵니다.
   */
  static Optional<mighty.ai.play.PlayDecision> developPrimary(DeclarerPosition position) {
    var view = position.view();
    var facts = position.facts();
    Suit primary = position.primarySuit();
    var main = position.inSuit(primary);
    if (main.size() < 2 || main.stream().anyMatch(a -> facts.isSuitTop(a.card()))) {
      return Optional.empty();
    }
    boolean hasSideEntry = position.ordinary().stream()
        .filter(a -> ((StandardCard) a.card()).suit() != primary)
        .anyMatch(facts::isMasterLead);
    if (!hasSideEntry) {
      return Optional.empty();
    }
    boolean friendReturnedPrimary = view.context().history().stream().anyMatch(trick ->
        facts.teams().friendSeat().filter(trick.leader()::equals).isPresent()
            && trick.winner().equals(view.self())
            && trick.cards().get(0).action() instanceof StandardPlay play
            && play.card().suit() == primary);
    boolean usedPrimaryJoker = usedPrimaryJoker(position);
    if (!friendReturnedPrimary && !usedPrimaryJoker) {
      return Optional.empty();
    }
    var joker = position.legal().stream().filter(a ->
        a instanceof JokerLead lead && lead.declaredSuit() == primary).findFirst();
    if (joker.isPresent()) {
      return Optional.of(new mighty.ai.play.PlayDecision(joker.get(),
          "사이드 회수를 남기고 조커로 주력의 미소모 상위를 빼냅니다."));
    }
    if (!usedPrimaryJoker) {
      return Optional.empty();
    }
    return Optional.of(
        new mighty.ai.play.PlayDecision(mighty.ai.play.PlaySupport.cheapest(main, view),
            "조커 뒤에 남은 주력 상위를 낮은 카드로 빼고 사이드 탑카로 회수할 준비를 합니다."));
  }

  private static boolean usedPrimaryJoker(DeclarerPosition position) {
    return position.view().context().history().stream().anyMatch(trick ->
        trick.leader().equals(position.view().self())
            && trick.cards().get(0).action() instanceof JokerLead lead
            && lead.declaredSuit() == position.primarySuit());
  }

  private static boolean primaryConnected(DeclarerPosition position, Action action) {
    if (!(action.card() instanceof StandardCard c) || c.suit() != position.primarySuit()) {
      return false;
    }
    // 계획은 미확인 분포의 추정입니다. 실제로 상위가 남으면 무리하게 확정 승리로 취급하지 않습니다.
    return position.facts().cards().outsideCards().stream()
        .noneMatch(other -> other instanceof StandardCard s
            && s.suit() == c.suit() && !s.equals(Rules.mighty(Trump.NO_TRUMP))
            && s.rank().value() > c.rank().value());
  }

}
