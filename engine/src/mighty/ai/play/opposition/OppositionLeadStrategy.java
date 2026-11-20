package mighty.ai.play.opposition;

import mighty.ai.play.PlayDecision;
import mighty.ai.play.PlayFacts;
import mighty.ai.play.PlaySupport;
import mighty.ai.play.TrumpControl;
import java.util.Comparator;
import java.util.List;
import mighty.game.action.Action;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.StandardCard;

/**
 * 야당의 기루 리드는 주공의 마지막 기루를 이겨 빼는 공개 근거가 있을 때만 선택합니다.
 */
final class OppositionLeadStrategy {

  private OppositionLeadStrategy() {
  }

  static PlayDecision choose(View view, PlayFacts facts, List<Action> legal) {
    boolean jokerFriend = view.friendSelection() instanceof mighty.game.friend.CardFriend f
        && f.card() instanceof mighty.model.Joker && view.context()
        .friendStatus() instanceof mighty.game.friend.UnrevealedFriend;
    if (view.contract().trump() == mighty.model.Trump.NO_TRUMP || jokerFriend
        || OppositionJokerPressure.passedJokerOpportunity(view, facts)) {
      var call = mighty.ai.play.JokerCallPlan.call(view, facts, legal);
      if (call.isPresent()) {
        return new PlayDecision(call.get(), "공개 진행을 근거로 여당에 있을 수 있는 조커를 총으로 압박합니다.");
      }
    }
    TrumpControl trumps = new TrumpControl(view, facts);
    var stripping = legal.stream().filter(trumps::canStripDeclarer).findFirst();
    if (stripping.isPresent()) {
      return new PlayDecision(stripping.get(), "공개 카운팅으로 주공의 마지막 기루를 이겨 탈기시킵니다.");
    }
    var trumpCall = OppositionTrumpCall.choose(view, facts, legal);
    if (trumpCall.isPresent()) {
      return new PlayDecision(trumpCall.get(), "조커 기루컴으로 주공을 탈기시키거나 남은 점수 기루를 끌어냅니다.");
    }
    List<Action> side = legal.stream().filter(action -> !view.contract().trump().matches(
            Rules.conditionsFor(view.contract().trump(), view.trickNumber(), action).leadSuit()))
        .toList();
    if (side.isEmpty()) {
      return new PlayDecision(PlaySupport.cheapest(legal, view), "다른 문양의 합법 리드가 없어 강제로 기루를 냅니다.");
    }
    List<Action> ordinary = PlaySupport.ordinary(side, view);
    var failurePressure = ContractFailurePressure.lead(view, facts, ordinary);
    if (failurePressure.isPresent()) {
      return failurePressure.get();
    }
    var pressureLead = pressureKnownVoid(view, facts, ordinary);
    if (pressureLead.isPresent()) {
      return new PlayDecision(pressureLead.get(),
          "주공이 비운 문양의 남은 점수로 압박해 물패 한 장을 공짜로 털지 못하게 합니다.");
    }
    var pointLead = leadExcessPoints(view, facts, ordinary);
    if (pointLead.isPresent()) {
      return new PlayDecision(pointLead.get(),
          "점수가 많이 남아 뒤 야당이 받을 수 있는 문양부터 점수로 압박합니다.");
    }
    var development = DevelopmentAttack.choose(view, facts, ordinary);
    if (development.isPresent()) {
      return development.get();
    }
    DeclarerPressure pressure = new DeclarerPressure(view, facts);
    List<Action> masters = ordinary.stream().filter(facts::isMasterLead)
        .filter(a -> !pressure.defense((StandardCard) a.card())).toList();
    if (!masters.isEmpty()) {
      return new PlayDecision(PlaySupport.highest(masters), "기루를 빼지 않고 사이드 승리 카드로 공격합니다.");
    }
    var attacking = ordinary.stream()
        .filter(a -> !pressure.operating(((StandardCard) a.card()).suit())).toList();
    if (attacking.isEmpty()) {
      attacking = ordinary.stream().filter(a -> !pressure.defense((StandardCard) a.card()))
          .toList();
    }
    List<Action> candidates =
        !attacking.isEmpty() ? attacking : ordinary.isEmpty() ? side : ordinary;
    return new PlayDecision(PlaySupport.cheapest(candidates, view),
        "탈기 근거 없는 기루 리드는 피하고 사이드로 공격합니다.");
  }

  private static java.util.Optional<Action> leadExcessPoints(View view, PlayFacts facts,
      List<Action> ordinary) {
    if (view.hand().stream().mapToInt(card -> card.points()).sum() < 3) {
      return java.util.Optional.empty();
    }
    var pressure = new DeclarerPressure(view, facts);
    return ordinary.stream().filter(action -> action.card().points() > 0)
        .map(action -> (mighty.game.action.StandardPlay) action)
        .filter(action -> !action.call() && !pressure.defense(action.card()))
        .filter(action -> facts.isMasterLead(action) || view.context().playOrder().seats().stream()
            .filter(seat -> !seat.equals(view.self()) && facts.teams().isAlly(seat))
            .anyMatch(seat -> facts.cards().isVoid(seat, action.card().suit())
                && facts.cards().trumps().expectedCount(seat, facts) > 0.5))
        .max(Comparator.comparingInt(action -> action.card().rank().value()))
        .map(Action.class::cast);
  }

  private static java.util.Optional<Action> pressureKnownVoid(View view, PlayFacts facts,
      List<Action> ordinary) {
    if (view.contract().trump() == mighty.model.Trump.NO_TRUMP) {
      return java.util.Optional.empty();
    }
    return ordinary.stream().filter(action -> action.card().points() > 0)
        .filter(action -> {
          var card = (StandardCard) action.card();
          return facts.cards().isVoid(view.context().declarer(), card.suit())
              && facts.cards().outsideCards().stream()
              .noneMatch(outside -> outside instanceof StandardCard other
                  && other.suit() == card.suit() && other.points() > 0
                  && !other.equals(Rules.mighty(view.contract().trump())));
        })
        .max(Comparator.comparingInt(action -> ((StandardCard) action.card()).rank().value()));
  }

}
