package mighty.ai.play.declarer;

import java.util.*;
import mighty.ai.bidding.BiddingHand;
import mighty.ai.planning.TumbleNoTrumpPlan;
import mighty.ai.play.*;
import mighty.game.action.*;
import mighty.game.friend.CardFriend;
import mighty.game.observation.DeclarerInfo;
import mighty.game.rule.Rules;
import mighty.model.*;

/**
 * 자기 초기 손패에서만 복원하는 계획. 상대의 미공개 손패는 조회하지 않습니다.
 */
final class TumbleNoTrumpPlay {

  private TumbleNoTrumpPlay() {
  }

  private static Optional<TumbleNoTrumpPlan> plan(DeclarerPosition position) {
    var view = position.view();
    if (view.contract().trump() != Trump.NO_TRUMP || view.contract().points() < 15
        || view.contract().points() > 16 || !(view.friendSelection() instanceof CardFriend friend)
        || !(friend.card() instanceof StandardCard called) || called.rank() != Rank.ACE
        || called.equals(Rules.mighty(Trump.NO_TRUMP))) {
      return Optional.empty();
    }
    return TumbleNoTrumpPlan.find(BiddingHand.from(NoTrumpOperations.initialCards(position)),
            ((DeclarerInfo) view.privateInfo()).discardedCards(), view.contract())
        .filter(p -> view.friendSelection().equals(new CardFriend(p.friend())));
  }

  static Optional<PlayDecision> lead(DeclarerPosition position) {
    return plan(position).flatMap(plan -> {
      var view = position.view();
      var facts = position.facts();
      if (view.trickNumber().isFirst()) {
        return Optional.of(new PlayDecision(new StandardPlay(plan.opening(), false),
            "우당탕탕: 주력 요청 전에 낮은 사이드로 K의 상위를 확인합니다."));
      }
      var primary = position.inSuit(plan.friend().suit());
      if (!facts.cards().publicHistory().cards().contains(plan.friend()) && !primary.isEmpty()) {
        return Optional.of(new PlayDecision(PlaySupport.cheapest(primary, view),
            "회수한 선에서 A 프렌드를 찾아 주력의 상위를 연결합니다."));
      }
      var tops = position.ordinary().stream().filter(a -> facts.isSuitTop(a.card())).toList();
      if (!tops.isEmpty()) {
        var mainTops = tops.stream()
            .filter(a -> ((StandardCard) a.card()).suit() == plan.friend().suit()).toList();
        return Optional.of(
            new PlayDecision(PlaySupport.highest(mainTops.isEmpty() ? tops : mainTops),
                "우당탕탕: 공개 소모로 살아난 일반 탑카를 쓰고 특수 회수는 남깁니다."));
      }
      // 더 맞을 이유가 없으면 조커로 남은 문양을 정리합니다. 미소모 상위를 탑으로 가정하지 않습니다.
      var target = !primary.isEmpty() ? plan.friend().suit() : plan.opening().suit();
      return position.legal().stream().filter(a -> a instanceof JokerLead lead
              && lead.declaredSuit() == target).findFirst()
          .map(a -> new PlayDecision(a, "우당탕탕: 조커로 남은 운영 문양의 상위를 소모합니다."));
    });
  }

  static Optional<PlayDecision> follow(DeclarerPosition position) {
    return plan(position).flatMap(plan -> {
      var view = position.view();
      var facts = position.facts();
      var mighty = Rules.mighty(Trump.NO_TRUMP);
      var winning = facts.winningActions().stream().filter(position.legal()::contains).toList();
      if (!facts.allyWinning()) {
        var tops = PlaySupport.ordinary(winning, view).stream()
            .filter(a -> facts.isSuitTop(a.card())).toList();
        if (!tops.isEmpty()) {
          return Optional.of(new PlayDecision(PlaySupport.cheapest(tops, view),
              "우당탕탕: 살아난 일반 탑으로 회수하고 마쪼를 남깁니다."));
        }
        boolean call = view.trick().stream()
            .anyMatch(p -> p.action() instanceof StandardPlay s && s.call());
        boolean singleton = view.hand().contains(mighty) && view.hand().stream()
            .filter(c -> c instanceof StandardCard s && s.suit() == mighty.suit()).count() == 1;
        if (call || facts.trickPoints() >= 3 || ScoreSupport.lossWouldDefeatContract(view, facts)
            || singleton || view.trickNumber().value() >= 9) {
          var recovery = winning.stream().filter(a -> a.card().equals(mighty)).findFirst()
              .or(() -> winning.stream().filter(a -> a.card() instanceof Joker).findFirst());
          if (recovery.isPresent()) {
            return recovery.map(a -> new PlayDecision(a,
                "우당탕탕: 실린 점수·콜·마이티 받침 소진을 보고 선을 회수합니다."));
          }
        }
      }
      var passing = PlaySupport.ordinary(facts.passingActions(position.legal()), view).stream()
          .filter(a -> a.card().points() == 0).toList();
      if (passing.isEmpty()) {
        return Optional.empty();
      }
      var preserveCover = passing.stream().filter(a ->
          ((StandardCard) a.card()).suit() != mighty.suit()
              || facts.conditions().leadSuit() == mighty.suit()).toList();
      return Optional.of(new PlayDecision(PlaySupport.cheapest(
          preserveCover.isEmpty() ? passing : preserveCover, view),
          "우당탕탕: 작은 손실은 물패로 따르고 마공 받침과 특수 회수를 보존합니다."));
    });
  }
}
