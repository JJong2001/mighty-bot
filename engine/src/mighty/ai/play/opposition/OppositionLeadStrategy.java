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

  private static final int POINT_LOAD_FOR_PRESSURE = 3;

  private OppositionLeadStrategy() {
  }

  static PlayDecision choose(View view, PlayFacts facts, List<Action> legal) {
    boolean jokerFriend = view.friendSelection() instanceof mighty.game.friend.CardFriend f
        && f.card() instanceof mighty.model.Joker && view.context()
        .friendStatus() instanceof mighty.game.friend.UnrevealedFriend;
    if (view.contract().trump() == mighty.model.Trump.NO_TRUMP || jokerFriend
        || OppositionJokerPressure.jokerLikelyOnDeclarerTeam(view, facts)) {
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
    var pressureLead = leadPointPressure(view, facts, ordinary);
    if (pressureLead.isPresent()) {
      return new PlayDecision(pressureLead.get(),
          "수비에 필요 없는 점수가 많이 남아 한 점을 먼저 걸고 주공의 흘리기와 간 선택을 압박합니다.");
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

  private static java.util.Optional<Action> leadPointPressure(View view, PlayFacts facts,
      List<Action> ordinary) {
    if (view.hand().stream().mapToInt(card -> card.points()).sum() < POINT_LOAD_FOR_PRESSURE) {
      return java.util.Optional.empty();
    }
    var pressure = new DeclarerPressure(view, facts);
    return ordinary.stream().filter(action -> action.card().points() > 0)
        .map(action -> (mighty.game.action.StandardPlay) action)
        .filter(action -> !action.call() && !pressure.defense(action.card()))
        // 동료의 승리를 확정해야만 걸 수 있는 점수가 아닙니다. 수비 가치는 별도로 남깁니다.
        .min(Comparator.<mighty.game.action.StandardPlay>comparingInt(action ->
                facts.cards().isVoid(view.context().declarer(), action.card().suit()) ? 0 : 1)
            .thenComparingInt(action -> action.card().rank().value()))
        .map(Action.class::cast);
  }

}
