package mighty.ai.play.declarer;

import mighty.model.Trump;
import mighty.model.Suit;
import mighty.model.StandardCard;
import mighty.model.Joker;
import mighty.game.trick.PublicPlay;
import mighty.game.rule.Rules;
import java.util.Optional;
import java.util.Comparator;
import java.util.Arrays;
import mighty.ai.play.PlayDecision;
import mighty.ai.play.FollowStrategy;
import mighty.ai.play.PlaySupport;
import mighty.game.action.Action;

/**
 * 주공 후수의 반환 예외 → 동료 보호 → 회수 → 손실 관리 우선순위를 소유합니다.
 */
final class DeclarerFollowStrategy {

  private DeclarerFollowStrategy() {
  }

  /**
   * 먼저 해당하는 판단을 채택합니다. 순서 변경은 구조 정리가 아니라 전략 변경입니다.
   */
  static PlayDecision choose(DeclarerPosition position) {
    var view = position.view();
    var facts = position.facts();
    var legal = position.legal();
    var primaryReturn = NoTrumpOperations.recoverPrimaryReturn(position);
    if (primaryReturn.isPresent()) {
      return primaryReturn.get();
    }
    var mightyReturn = NoTrumpOperations.mightyOnOpeningFriendReturn(position);
    if (mightyReturn.isPresent()) {
      return mightyReturn.get();
    }
    var sideReturn = DeclarerFollowStrategy.recoverSideReturn(position);
    if (sideReturn.isPresent()) {
      return sideReturn.get();
    }
    var equivalent = mighty.ai.play.EquivalentPressure.pass(view, facts, legal);
    if (equivalent.isPresent()) {
      return equivalent.get();
    }
    var repeatedReturn = recoverRepeatedSideReturn(position);
    if (repeatedReturn.isPresent()) {
      return repeatedReturn.get();
    }
    var splitReturn = NoTrumpOperations.recoverSplitReturn(position);
    if (splitReturn.isPresent()) {
      return splitReturn.get();
    }
    var cover = DeclarerShedding.keepSideCover(position);
    if (cover.isPresent()) {
      return cover.get();
    }
    // 개인 회수 계획보다 공통 동료 탑카 보호가 먼저입니다.
    var protectedAlly = FollowStrategy.protectAlly(view, facts, legal);
    if (protectedAlly.isPresent()) {
      return protectedAlly.get();
    }
    var regain = NoTrumpOperations.regain(position);
    if (regain.isPresent()) {
      return regain.get();
    }
    if (position.calls(Joker.INSTANCE)) {
      var ruff = mighty.ai.play.RuffPlan.choose(view, facts, legal);
      if (ruff.isPresent()) {
        return new PlayDecision(ruff.get(),
            "간으로 선을 잡아 아직 나오지 않은 조커 프렌드를 찾을 기회를 만듭니다.");
      }
    }
    var urgent = DeclarerShedding.preventLargeLoss(position);
    if (urgent.isPresent()) {
      return urgent.get();
    }
    var shedding = DeclarerShedding.choose(position);
    if (shedding.isPresent()) {
      return shedding.get();
    }
    return recoverTrumpReturn(position)
        .orElseGet(() -> FollowStrategy.choose(view, facts, legal));
  }

  /**
   * 프렌드의 물패 반환은 안전한 사이드 탑카로 받아 주공이 선을 가져옵니다.
   */
  static Optional<PlayDecision> recoverSideReturn(DeclarerPosition position) {
    var view = position.view();
    var facts = position.facts();
    var lead = view.trick().get(0);
    if (view.contract().trump() == Trump.NO_TRUMP
        || view.contract().trump().matches(facts.conditions().leadSuit())
        || !facts.teams().isAlly(lead.seat())
        || !(lead.action().card() instanceof StandardCard card)
        || card.points() > 0 || facts.isSuitTop(card)) {
      return Optional.empty();
    }
    if (position.enemiesMayHaveTrump()) {
      return Optional.empty();
    }
    var winner = facts.currentWinner().orElseThrow();
    if (!winner.seat().equals(lead.seat()) && facts.allyWinning()) {
      return Optional.empty();
    }
    return mighty.ai.play.PlaySupport.ordinary(facts.winningActions(), view).stream()
        .filter(action -> ((StandardCard) action.card()).suit() == facts.conditions().leadSuit())
        .filter(action -> facts.isSecured(new PublicPlay(view.self(), action)))
        .max(Comparator.comparingInt(action -> ((StandardCard) action.card()).rank().value()))
        .map(action -> new PlayDecision(action, "프렌드의 물패 반환을 안전한 사이드 탑카로 회수합니다."));
  }

  private static Optional<PlayDecision> recoverTrumpReturn(DeclarerPosition position) {
    var view = position.view();
    var facts = position.facts();
    var leader = view.trick().get(0);
    boolean friendReturning = facts.teams().isAlly(leader.seat());
    boolean trumpLead = view.contract().trump().matches(facts.conditions().leadSuit());
    var mighty = position.legal().stream()
        .filter(action -> action.card().equals(Rules.mighty(view.contract().trump()))).findFirst();
    // 탑카 보호를 통과한 낮은 친구 기루만 회수합니다. 미출현 조커 자체는 사용 근거가 아닙니다.
    if (friendReturning && trumpLead && mighty.isPresent()
        && !facts.isSuitTop(facts.currentWinner().orElseThrow().action().card())) {
      return Optional.of(new PlayDecision(mighty.get(), "친구의 낮은 기루 반환을 마이티로 회수해 기루 장수와 선을 확보합니다."));
    }
    if (friendReturning && trumpLead) {
      var ordinary = facts.winningActions().stream()
          .filter(action -> action.card() instanceof StandardCard card
              && view.contract().trump().matches(card.suit())).toList();
      var tops = ordinary.stream().filter(action -> facts.isSuitTop(action.card())
          || facts.isSecured(new PublicPlay(view.self(), action))).toList();
      // 일반 기루가 뒤 상위에 막히면 조커로 선을 확보하고 자기 점수 기루를 남깁니다.
      if (mighty.isEmpty() && !ordinary.isEmpty() && ordinary.stream()
          .noneMatch(action -> facts.isSecured(new PublicPlay(view.self(), action)))) {
        var joker = facts.winningActions().stream()
            .filter(position.legal()::contains)
            .filter(action -> action.card() instanceof Joker)
            .filter(action -> facts.isSecured(new PublicPlay(view.self(), action)))
            .findFirst();
        if (joker.isPresent()) {
          return Optional.of(new PlayDecision(joker.get(),
              "친구의 기루 반환을 조커로 잡고 뒤 상위에 점수 기루를 내주지 않습니다."));
        }
      }
      if (mighty.isEmpty() && tops.isEmpty() && ordinary.stream()
          .anyMatch(a -> a.card().points() > 0)) {
        var low = position.trumps().stream().filter(a -> a.card().points() == 0).toList();
        if (!low.isEmpty()) {
          return Optional.of(new PlayDecision(PlaySupport.cheapest(low, view),
              "뒤의 상위 기루에 점수를 내주지 않도록 낮은 기루로 따릅니다."));
        }
      }
      var winning = mighty.isPresent() ? mighty : !tops.isEmpty()
          ? tops.stream()
          .min(Comparator.comparingInt(action -> ((StandardCard) action.card()).rank().value()))
          : ordinary.stream().max(
              Comparator.comparingInt(action -> ((StandardCard) action.card()).rank().value()));
      if (winning.isPresent()) {
        return Optional.of(new PlayDecision(winning.get(),
            "친구의 기루 반환에서 실제 야당이 앞서므로 기루 승리 카드로 선을 회수합니다."));
      }
    }
    return new DeclarerPlan(position).follow();
  }

  /**
   * 무기루 친구의 반복 사이드 반환에서 마이티로 간을 막고 A를 남기는 기존 예외입니다.
   */
  private static Optional<PlayDecision> recoverRepeatedSideReturn(DeclarerPosition position) {
    var view = position.view();
    var facts = position.facts();
    var legal = position.legal();
    var leader = view.trick().get(0);
    if (view.contract().trump() != Trump.NO_TRUMP
        && facts.teams().isAlly(leader.seat()) && !view.contract().trump()
        .matches(facts.conditions().leadSuit())
        && (facts.cards().isVoid(leader.seat(), Arrays.stream(Suit.values())
        .filter(view.contract().trump()::matches).findFirst().orElse(facts.conditions().leadSuit()))
        || !facts.isSuitTop(leader.action().card()))
        && !(facts.allyWinning() && facts.isSuitTop(
        facts.currentWinner().orElseThrow().action().card()))
        && view.context().history().stream()
        .anyMatch(t -> Rules.conditionsFor(view.contract().trump(),
            t.number(), t.cards().get(0).action()).leadSuit() == facts.conditions().leadSuit())) {
      var mightyAction = legal.stream()
          .filter(a -> a.card().equals(Rules.mighty(view.contract().trump())))
          .filter(facts.winningActions()::contains).findFirst();
      if (mightyAction.isPresent()) {
        return Optional.of(new PlayDecision(mightyAction.get(),
            "기루가 없는 친구의 반복 사이드 반환에 마이티를 사용해 간을 막고 이후 A를 남깁니다."));
      }
    }
    return Optional.empty();
  }

}
