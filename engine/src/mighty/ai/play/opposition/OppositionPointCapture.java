package mighty.ai.play.opposition;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import mighty.ai.play.*;
import mighty.game.action.Action;
import mighty.game.observation.View;
import mighty.model.StandardCard;

/**
 * 주공이 흘린 트릭에서는 현재 점수 확보와 부기루의 후속 수비 가치를 비교합니다.
 */
final class OppositionPointCapture {

  private OppositionPointCapture() {
  }

  /**
   * 주공의 낮은 사이드 리드에는 짧은 점수 묶음의 작은 승리 카드로 압박합니다.
   */
  static Optional<PlayDecision> attackLowLead(View view, PlayFacts facts) {
    var lead = view.trick().get(0);
    var winner = facts.currentWinner().orElseThrow();
    var suit = facts.conditions().leadSuit();
    if (!lead.seat().equals(view.context().declarer())
        || !(lead.action().card() instanceof StandardCard card) || card.points() != 0
        || !winner.seat().equals(lead.seat()) || view.contract().trump().matches(suit)) {
      return Optional.empty();
    }
    var holding = view.hand().stream()
        .filter(candidate -> candidate instanceof StandardCard standard
            && standard.suit() == suit
            && !candidate.equals(mighty.game.rule.Rules.mighty(view.contract().trump()))).toList();
    boolean friendMayFollow = facts.remainingSeats().stream()
        .anyMatch(seat -> !facts.teams().isAlly(seat));
    if (!friendMayFollow
        || holding.stream().filter(candidate -> candidate.points() > 0).count() < 2) {
      return Optional.empty();
    }
    return PlaySupport.ordinary(facts.winningActions(), view).stream()
        .filter(action -> holding.contains(action.card()) && action.card().points() > 0)
        // 최고 수비까지 소모하지 않고, 중간 점수로 뒤 프렌드의 값싼 승리를 막습니다.
        .filter(action -> holding.stream().anyMatch(c -> c instanceof StandardCard higher
            && higher.rank().value() > ((StandardCard) action.card()).rank().value())
            || holding.size() <= 3)
        .min(Comparator.comparingInt(action -> ((StandardCard) action.card()).rank().value()))
        .map(action -> new PlayDecision(action, "주공의 낮은 사이드에 작은 승리 점수로 압박하고 높은 카드를 남깁니다."));
  }

  static Optional<PlayDecision> choose(View view, PlayFacts facts, List<Action> legal) {
    if (facts.allyWinning() || view.contract().trump().matches(facts.conditions().leadSuit())) {
      return Optional.empty();
    }
    boolean declarerOut = view.trick().stream().anyMatch(p ->
        p.seat().equals(view.context().declarer())
            && p.action().card() instanceof StandardCard card
            && card.suit() != facts.conditions().leadSuit()
            && !view.contract().trump().matches(card.suit())
            && !card.equals(mighty.game.rule.Rules.mighty(view.contract().trump())));
    if (!declarerOut) {
      return Optional.empty();
    }
    var pressure = new DeclarerPressure(view, facts);
    int points = facts.trickPoints();
    boolean lastChance = facts.cards().trumps().likelyLastSide(view.context().declarer(), facts);
    return PlaySupport.ordinary(facts.winningActions(), view).stream()
        .filter(legal::contains)
        .filter(a -> ((StandardCard) a.card()).suit() == facts.conditions().leadSuit())
        .filter(a -> a.card().points() > 0)
        // 수비할 근거가 있고 다음 공격 기회도 남은 경우에만 점수를 보존합니다.
        .filter(a -> points >= 2 || lastChance || !pressure.defense((StandardCard) a.card()))
        .min(Comparator.comparingInt(a -> ((StandardCard) a.card()).rank().value()))
        .map(a -> new PlayDecision(a,
            "주공이 흘린 기회에 점수로 선을 잡습니다. 후속 부기루 수비가 있는 카드는 별도로 보존합니다."));
  }

}
