package mighty.ai.play;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import mighty.ai.play.declarer.DeclarerStrategy;
import mighty.ai.play.friend.FriendStrategy;
import mighty.ai.play.opposition.OppositionStrategy;
import mighty.game.Role;
import mighty.game.action.Action;
import mighty.game.action.StandardPlay;
import mighty.game.observation.View;
import mighty.model.Trump;

/**
 * 문서의 안전·지원 규칙을 선과 따라내기에 적용하는 공통 플레이 정책입니다.
 */
public final class RulePlayPolicy implements PlayPolicy {

  private final DeclarerStrategy declarerStrategy = new DeclarerStrategy();
  private final RoleStrategy friendStrategy = new FriendStrategy();
  private final RoleStrategy oppositionStrategy = new OppositionStrategy();

  @Override
  public Action choose(View view) {
    return recommend(view).action();
  }

  /**
   * 선택 이유도 함께 반환해 당시 판단과 이후 교정을 구분할 수 있게 합니다.
   */
  public PlayDecision recommend(View view) {
    Objects.requireNonNull(view);
    PlayFacts facts = new PlayFacts(view);
    List<Action> legal = policyCandidates(view, facts);
    var usual = usualDecision(view, facts, legal);
    var top = spreadTopLead(view, facts, legal);
    if (top.isPresent() && !top.get().action().equals(usual.action())) {
      return HonorGambleTrial.compareSpreadLead(view, facts, usual, top.get(), this);
    }
    var ruffLeads = LastTrumpSupport.candidates(view, facts, legal, usual.action());
    if (!ruffLeads.isEmpty()) {
      return HonorGambleTrial.compareRuffLeads(view, facts, usual, ruffLeads, this);
    }
    return HonorGamble.choose(view, facts, legal, usual, this);
  }

  // 비교 대국은 각 자리의 관측만 사용하고 평가 중 다시 가상 대국을 열지 않습니다.
  PlayDecision usualDecision(View view) {
    var facts = new PlayFacts(view);
    return usualDecision(view, facts, policyCandidates(view, facts));
  }

  PlayDecision spreadTopContinuation(View view) {
    var facts = new PlayFacts(view);
    var legal = policyCandidates(view, facts);
    return spreadTopLead(view, facts, legal).orElseGet(() -> usualDecision(view, facts, legal));
  }

  private Optional<PlayDecision> spreadTopLead(View view, PlayFacts facts, List<Action> legal) {
    return declarerStrategy.spreadTopLead(view, facts, legal)
        .map(decision -> finishDecision(decision, view, facts, legal));
  }

  private PlayDecision usualDecision(View view, PlayFacts facts, List<Action> legal) {
    // 이 두 공통 특례는 기존처럼 역할 판단과 후처리보다 먼저 반환합니다.
    var expiry = JokerExpiry.choose(view, facts, legal);
    if (expiry.isPresent()) {
      return expiry.get();
    }
    var firstFriend = FirstFriendStrategy.choose(view, facts, legal);
    if (firstFriend.isPresent()) {
      return firstFriend.get();
    }
    var decision = chooseRole(view, facts, legal);
    var recovery = ControlRecoveryPlan.choose(view, facts, legal);
    if (recovery.isPresent() && !recovery.get().action().equals(decision.action())) {
      decision = recovery.get();
    }
    return finishDecision(decision, view, facts, legal);
  }

  /**
   * 게임 규칙상의 합법 수와 전략상 보존을 적용한 후보를 구분합니다.
   */
  private List<Action> policyCandidates(View view, PlayFacts facts) {
    List<Action> legal = facts.legalActions();
    if (view.role() == Role.FRIEND && view.contract().trump() == Trump.NO_TRUMP) {
      List<Action> withoutCall = legal.stream()
          .filter(action -> !(action instanceof StandardPlay play) || !play.call()).toList();
      if (!withoutCall.isEmpty()) {
        legal = withoutCall;
      }
    }
    // 보존 예외를 한 곳에서 합법 후보에 적용하여 역할별 9구 처리와 충돌하지 않게 합니다.
    if (FriendSupportIntent.preserveJoker(view, facts)) {
      var ordinary = legal.stream().filter(a -> !(a.card() instanceof mighty.model.Joker)).toList();
      if (!ordinary.isEmpty() && (view.trick().isEmpty() || facts.allyWinning())) {
        legal = ordinary;
      }
    }
    return legal;
  }

  /**
   * 역할은 판단 책임만 선택합니다. 역할 내부의 우선순위는 각 전략이 소유합니다.
   */
  private PlayDecision chooseRole(View view, PlayFacts facts, List<Action> legal) {
    return switch (view.role()) {
      case DECLARER -> declarerStrategy.choose(view, facts, legal);
      case FRIEND -> friendStrategy.choose(view, facts, legal);
      case OPPOSITION -> oppositionStrategy.choose(view, facts, legal);
      default -> view.trick().isEmpty()
          ? LeadStrategy.choose(view, facts, legal) : FollowStrategy.choose(view, facts, legal);
    };
  }

  /**
   * 선택 후 안전 보정 → 연결 신호 → 후보 검증 → 추론 설명 순서를 유지합니다.
   */
  private PlayDecision finishDecision(PlayDecision decision, View view, PlayFacts facts,
      List<Action> legal) {
    if (view.role() == Role.DECLARER) {
      decision = mighty.ai.play.declarer.DeclarerLeadSafety.apply(decision,
          view, facts, legal);
    }
    decision = ConnectedCardSignal.apply(decision, view, facts, legal);
    if (!legal.contains(decision.action())) {
      throw new IllegalStateException("전략이 합법 행동 밖의 선택을 반환했습니다.");
    }
    var shortSeats = facts.cards().trumps().likelyShortSeats();
    if (!shortSeats.isEmpty()) {
      decision = new PlayDecision(decision.action(), decision.reason()
          + " 공개 추론: 기루 A에 점수를 따른 자리 " + shortSeats.stream().map(s -> s.index()).sorted().toList()
          + "는 잔여 기루가 적을 가능성이 있습니다. 확정 무기루와는 구분합니다.");
    }
    return decision;
  }

}
