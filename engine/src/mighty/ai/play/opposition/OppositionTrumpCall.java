package mighty.ai.play.opposition;

import java.util.List;
import java.util.Optional;
import mighty.ai.play.PlayFacts;
import mighty.game.action.Action;
import mighty.game.action.JokerLead;
import mighty.game.observation.View;

/**
 * 공개 카운팅에 근거하여 조커 기루컴의 이득을 확인합니다.
 */
final class OppositionTrumpCall {

  private OppositionTrumpCall() {
  }

  static Optional<Action> choose(View view, PlayFacts facts, List<Action> legal) {
    var call = legal.stream().filter(action -> action instanceof JokerLead joker
        && view.contract().trump().matches(joker.declaredSuit())).findFirst();
    if (call.isEmpty() || !facts.isMasterLead(call.get())) {
      return Optional.empty();
    }

    var suit = ((JokerLead) call.get()).declaredSuit();
    var declarer = view.context().declarer();
    var outside = facts.cards().trumps().outside();
    if (outside.isEmpty() || facts.cards().isVoid(declarer, suit)) {
      return Optional.empty();
    }
    var otherSeats = view.context().playOrder().seats().stream()
        .filter(seat -> !seat.equals(view.self()) && !seat.equals(declarer)).toList();

    // 마지막 외부 기루의 위치를 프렌드와 혼동하지 않습니다.
    boolean stripsDeclarer = outside.size() == 1
        && otherSeats.stream().allMatch(seat -> facts.cards().isVoid(seat, suit));
    boolean drawsPoints = outside.stream().allMatch(card -> card.points() > 0)
        && otherSeats.stream()
        .allMatch(seat -> facts.teams().isAlly(seat) || facts.cards().isVoid(seat, suit))
        && otherSeats.stream().filter(facts.teams()::isAlly)
        .allMatch(seat -> facts.cards().isVoid(seat, suit))
        && otherSeats.stream().anyMatch(facts.teams()::isAlly);
    boolean establishesTail = new mighty.ai.play.TrumpControl(view, facts)
        .canEstablishTailAfterSweep();
    return stripsDeclarer || drawsPoints || establishesTail ? call : Optional.empty();
  }
}
