package mighty.ai.play;

import java.util.*;
import mighty.game.Role;
import mighty.game.action.Action;
import mighty.game.action.JokerLead;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.game.trick.PublicPlay;
import mighty.model.*;

/**
 * 이번 회수와 다음 문양 소진을 연결하는 여야 공통 판단입니다.
 */
public final class ControlRecoveryPlan {

  private ControlRecoveryPlan() {
  }

  /**
   * 간으로 회수한 바로 다음 선에서도 같은 카운팅을 읽어 조커 소진 계획을 실행합니다.
   */
  private static Optional<PlayDecision> afterRuff(View view, PlayFacts facts, List<Action> legal) {
    if (view.trickNumber().value() >= 10 || view.context().history().isEmpty()
        || facts.cards().outsideCards().contains(Rules.mighty(view.contract().trump()))) {
      return Optional.empty();
    }
    var trumps = facts.cards().trumps();
    if (trumps.outside().size() != 1 || trumps.own().isEmpty()
        || !trumps.enemiesMayHold(facts)) {
      return Optional.empty();
    }
    var last = view.context().history().get(view.context().history().size() - 1);
    boolean ruffed = last.winner().equals(view.self())
        && !view.contract().trump().matches(Rules.conditionsFor(view.contract().trump(),
        last.number(), last.cards().get(0).action()).leadSuit())
        && last.cards().stream().anyMatch(p -> p.seat().equals(view.self())
        && p.action().card() instanceof StandardCard c && view.contract().trump()
        .matches(c.suit()));
    int blocker = trumps.outside().get(0).rank().value();
    if (!ruffed || trumps.own().stream().noneMatch(c -> c.rank().value() < blocker)) {
      return Optional.empty();
    }
    return legal.stream().filter(a -> a instanceof JokerLead lead
            && view.contract().trump().matches(lead.declaredSuit())).findFirst()
        .map(a -> new PlayDecision(a, "간으로 회수한 뒤 조커컴으로 마지막 외부 상위를 소모합니다."));
  }

  public static Optional<PlayDecision> choose(View view, PlayFacts facts, List<Action> legal) {
    if (view.trick().isEmpty()) {
      return afterRuff(view, facts, legal);
    }
    if (facts.allyWinning() || !view.hand().contains(Joker.INSTANCE)) {
      return Optional.empty();
    }
    var mighty = Rules.mighty(view.contract().trump());
    // 딸마는 조커로 보호하지 않습니다. 강제 문양을 지금 없애야 조커의 선택권이 남습니다.
    if (view.role() != Role.OPPOSITION && view.role() != Role.UNDETERMINED
        && facts.conditions().leadSuit() == mighty.suit()
        && view.hand().stream().filter(c -> c instanceof StandardCard s
        && s.suit() == mighty.suit()).count() == 1) {
      var action = facts.winningActions().stream().filter(legal::contains)
          .filter(a -> a.card().equals(mighty)).findFirst();
      if (action.isPresent()) {
        return action.map(a -> new PlayDecision(a,
            "딸마를 조커로 보호하지 않고 마이티로 회수해 다음 문양 선택권을 남깁니다."));
      }
    }
    if (view.trickNumber().value() >= 9 || facts.cards().outsideCards().contains(mighty)) {
      return Optional.empty();
    }
    var trumps = facts.cards().trumps();
    if (trumps.outside().size() != 1 || trumps.own().size() < 2) {
      return Optional.empty();
    }
    var ruff = RuffPlan.choose(view, facts, legal)
        .filter(a -> facts.isSecured(new PublicPlay(view.self(), a)));
    if (ruff.isEmpty()) {
      return Optional.empty();
    }
    var remaining = new ArrayList<>(trumps.own());
    remaining.remove(ruff.get().card());
    int outside = trumps.outside().get(0).rank().value();
    if (remaining.stream().noneMatch(c -> c.rank().value() < outside)) {
      return Optional.empty();
    }
    return ruff.map(a -> new PlayDecision(a,
        "일반 기루로 선을 회수하고 조커컴으로 마지막 외부 상위를 빼서 남은 기루를 살립니다."));
  }
}
