package mighty.ai.play.friend;

import java.util.*;
import mighty.ai.play.*;
import mighty.game.action.*;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.*;

/**
 * 선을 먹은 목적은 주공에게 연결하는 것입니다. 특수카드 순서가 반환을 가로막지 않습니다.
 */
final class FriendLeadStrategy {

  private FriendLeadStrategy() {
  }

  static PlayDecision choose(View view, PlayFacts facts, List<Action> legal) {
    // 안전한 대안이 있으면 탑카 리드와 최후의 반환에서도 마공을 피합니다.
    if (view.contract().trump() != Trump.NO_TRUMP) {
      var safe = legal.stream().filter(action -> !FriendReturnSafety.risksMightyAttack(
          Rules.conditionsFor(view.contract().trump(), view.trickNumber(), action).leadSuit(), view,
          facts)).toList();
      if (!safe.isEmpty()) {
        legal = safe;
      }
    }
    List<Action> ordinary = PlaySupport.ordinary(legal, view).stream()
        .filter(a -> !((StandardPlay) a).call()).toList();
    List<Action> jokers = legal.stream().filter(JokerLead.class::isInstance).toList();
    var cash = FriendSideCash.choose(view, facts, ordinary);
    if (cash.isPresent()) {
      return cash.get();
    }
    var sideRequest = FriendSupportIntent.sideRequest(view);
    if (FriendSupportIntent.preserveJoker(view, facts) && sideRequest.isPresent()) {
      var returning = ordinary.stream()
          .filter(a -> ((StandardCard) a.card()).suit() == sideRequest.get()).toList();
      if (!returning.isEmpty()) {
        return decision(PlaySupport.highest(returning),
            "일반 카드로 요청을 해결했으므로 조커를 남기고 요청 문양을 반환합니다.");
      }
    }
    boolean alreadyCashedTrump = false;
    if (view.contract().trump() != Trump.NO_TRUMP) {
      alreadyCashedTrump = view.context().history().stream().anyMatch(trick ->
          trick.winner().equals(view.self())
              && view.contract().trump().matches(Rules.conditionsFor(view.contract().trump(),
              trick.number(), trick.cards().get(0).action()).leadSuit())
              && trick.cards().stream().anyMatch(play -> play.seat().equals(view.self())
              && play.action().card() instanceof StandardCard card
              && view.contract().trump().matches(card.suit())
              && !card.equals(Rules.mighty(view.contract().trump()))));
      List<Action> trumps = ordinary.stream().filter(a -> view.contract().trump()
          .matches(((StandardCard) a.card()).suit())).toList();
      boolean mightyOnTrump = view.context().history().stream().anyMatch(t ->
          view.contract().trump().matches(
              Rules.conditionsFor(view.contract().trump(), t.number(), t.cards().get(0).action())
                  .leadSuit())
              && t.cards().stream().anyMatch(p -> p.seat().equals(view.self())
              && p.action().card().equals(Rules.mighty(view.contract().trump()))));
      // 일반 탑카가 이어지면 먼저 확인합니다. 조커는 그 연결이 막힐 때 씁니다.
      if (mightyOnTrump && !trumps.isEmpty() && JokerSuitPlan.enemiesMayHoldTrump(view, facts)) {
        Action highest = PlaySupport.highest(trumps);
        if (facts.isSuitTop(highest.card())) {
          return decision(highest,
              "마이티 다음에는 남은 최고 일반 기루로 연결하고 조커의 선택권을 남깁니다.");
        }
      }
      boolean uncertainReturn = !trumps.isEmpty()
          && trumps.stream().noneMatch(action -> facts.isSuitTop(action.card()));
      if ((mightyOnTrump || uncertainReturn) && !alreadyCashedTrump && !jokers.isEmpty()
          && !view.hand().contains(Rules.mighty(view.contract().trump()))
          && JokerSuitPlan.enemiesMayHoldTrump(view, facts)) {
        return decision(jokers.stream()
            .filter(a -> view.contract().trump().matches(((JokerLead) a).declaredSuit()))
            .findFirst().orElseThrow(), "일반 기루 탑카로 연결할 수 없어 조커로 외부 상위를 소모시키고 주공에게 연결합니다.");
      }
    }
    var returning = FriendReturnPlan.choose(view, facts, ordinary);
    if (returning.isPresent()) {
      return returning.get();
    }
    boolean enemyTrump = JokerSuitPlan.enemiesMayHoldTrump(view, facts);
    List<Action> candidates = ordinary.stream().filter(a -> {
      Suit suit = ((StandardCard) a.card()).suit();
      return (enemyTrump || !view.contract().trump().matches(suit))
          && FriendReturnSafety.allows(suit, view, facts);
    }).toList();
    // 안전 후보가 비면 금지 문양 전체를 되살리지 않고, 아래에서 위험을 비교합니다.
    if (!candidates.isEmpty()) {
      ordinary = candidates;
    } else {
      var alternatives = ordinary.stream().filter(a -> !((StandardCard) a.card()).suit()
          .equals(Rules.mighty(view.contract().trump()).suit())).toList();
      if (!alternatives.isEmpty()) {
        ordinary = alternatives;
      }
    }
    List<Action> safeSide = ordinary.stream().filter(a -> !view.contract().trump()
            .matches(((StandardCard) a.card()).suit())).filter(a -> sideCanLead(a, view, facts))
        .toList();
    if (!safeSide.isEmpty()) {
      return decision(PlaySupport.highest(safeSide),
          "장수·문양 소모·간 위험을 확인한 사이드 승리를 확보합니다.");
    }
    if (view.contract().trump() != Trump.NO_TRUMP && facts.cards().trumps().own().isEmpty()
        && facts.cards().outsideCards().contains(Rules.mighty(view.contract().trump()))) {
      var development = ordinary.stream()
          .filter(
              action -> !FriendReturnSafety.risksMightyAttack(((StandardCard) action.card()).suit(),
                  view, facts))
          .filter(action -> facts.declarerSuits().assess(((StandardCard) action.card()).suit())
              .beyondOpeningSignal())
          .filter(action -> FriendReturnSafety.allows(((StandardCard) action.card()).suit(), view,
              facts))
          .toList();
      if (!development.isEmpty()) {
        return decision(PlaySupport.highest(development),
            "돌려줄 기루가 없어 주공이 공개한 부기루에 마이티를 쓸 기회를 만듭니다.");
      }
    }
    if (alreadyCashedTrump && !ordinary.isEmpty() && !jokers.isEmpty()) {
      return decision(PlaySupport.cheapest(ordinary, view),
          "기루 탑카로 이미 지원했으므로 일반 카드로 돌리고 조커를 후속 지원에 남깁니다.");
    }
    if (FriendSupportIntent.mightyRemovalRoute(view) && !ordinary.isEmpty() && !jokers.isEmpty()) {
      return decision(PlaySupport.cheapest(ordinary, view),
          "마이티를 제거한 뒤 반환할 주력이 없으므로 조커를 남기고 다른 문양으로 연결합니다.");
    }
    if (!jokers.isEmpty()) {
      return decision(PlaySupport.jokerLead(jokers, view, facts),
          "일반 반환 경로가 없어 조커의 문양 선택권으로 연결합니다.");
    }
    List<Action> low = ordinary.stream().filter(a -> a.card().points() == 0).toList();
    return decision(
        PlaySupport.cheapest(!low.isEmpty() ? low : !ordinary.isEmpty() ? ordinary : legal, view),
        "확인되지 않은 사이드 점수카드의 도박성 선취를 피합니다.");
  }

  private static boolean sideCanLead(Action action, View view, PlayFacts facts) {
    StandardCard card = (StandardCard) action.card();
    if (!facts.isSuitTop(card)) {
      return false;
    }
    if (view.contract().trump() == Trump.NO_TRUMP) {
      return true;
    }
    if (!JokerSuitPlan.enemiesMayHoldTrump(view, facts)) {
      return facts.isMasterLead(action);
    }
    // 외부 일반 기루가 한 장까지 줄었으면 모든 미확인 위치를 이유로 탑카 시도를 영구 봉쇄하지 않습니다.
    if (new TrumpControl(view, facts).outside().size() <= 1) {
      return true;
    }
    long length = view.hand().stream()
        .filter(c -> c instanceof StandardCard s && s.suit() == card.suit()).count();
    long tops = view.hand().stream()
        .filter(c -> c instanceof StandardCard s && s.suit() == card.suit())
        .filter(facts::isSuitTop).count();
    if (tops >= 2 || length >= 4) {
      return false;
    }
    boolean alreadyLed = view.context().history().stream().anyMatch(t -> Rules.conditionsFor(
        view.contract().trump(), t.number(), t.cards().get(0).action()).leadSuit() == card.suit());
    return !JokerSuitPlan.enemiesMayHoldTrump(view, facts) || (!alreadyLed && facts.isMasterLead(
        action));
  }

  private static PlayDecision decision(Action action, String reason) {
    return new PlayDecision(action, reason);
  }

}
