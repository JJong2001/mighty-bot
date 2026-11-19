package mighty.ai.play.friend;

import mighty.model.Trump;
import mighty.model.StandardCard;
import mighty.model.Rank;
import mighty.model.Joker;
import mighty.game.trick.PublicPlay;
import mighty.game.rule.Rules;
import mighty.game.friend.CardFriend;
import java.util.Optional;
import java.util.Comparator;
import java.util.List;
import mighty.ai.play.*;
import mighty.game.action.Action;
import mighty.game.observation.View;

/**
 * 프렌드 후수 우선순위: 초구 신호 → 주력 순회 → 호출 → 점수 지원 → 동료 보호 → 승리 확보.
 */
final class FriendFollowStrategy {

  static PlayDecision choose(View view, PlayFacts facts, List<Action> legal) {
    var openingSignal = respectOpeningSignal(view, facts, legal);
    if (openingSignal.isPresent()) {
      return openingSignal.get();
    }
    var mightyAttack = FriendRiskSupport.onOppositionMightyAttack(view, facts, legal);
    if (mightyAttack.isPresent()) {
      return mightyAttack.get();
    }
    var requested = FriendSupportIntent.requestedSpecial(view, facts);
    if (requested.isPresent()) {
      return new PlayDecision(requested.get(),
          "주공이 요청한 특수카드로 응답하고 일반 카드는 반환과 후속 승리에 남깁니다.");
    }
    var aceRun = FriendAceRun.answer(view, facts, legal);
    if (aceRun.isPresent()) {
      return aceRun.get();
    }
    var continuingRun = supportContinuingRun(view, facts, legal);
    if (continuingRun.isPresent()) {
      return continuingRun.get();
    }
    var probe = FriendTrumpProbe.choose(view, facts, legal);
    if (probe.isPresent()) {
      return probe.get();
    }
    var lowTrump = FriendRiskSupport.onLowTrumpProbe(view, facts, legal);
    if (lowTrump.isPresent()) {
      return lowTrump.get();
    }
    var points = FriendPointSupport.choose(view, facts, legal);
    if (points.isPresent()) {
      return points.get();
    }
    var protectedAlly = FollowStrategy.protectAlly(view, facts, legal);
    if (protectedAlly.isPresent()) {
      return protectedAlly.get();
    }
    var jokerReserve = FriendRiskSupport.reserveJokerForHigherTrump(view, facts, legal);
    if (jokerReserve.isPresent()) {
      return jokerReserve.get();
    }
    var winningSupport = FriendWinningSupport.choose(view, facts, legal);
    if (winningSupport.isPresent()) {
      return winningSupport.get();
    }
    var ruff = RuffPlan.choose(view, facts, legal);
    if (ruff.isPresent()) {
      return new PlayDecision(ruff.get(),
          "프렌드도 상위 소모를 버틸 장수를 보고 점수 간과 낮은 간을 구분합니다.");
    }
    var securedSupport = secureWinningSupport(view, facts, legal);
    if (securedSupport.isPresent()) {
      return securedSupport.get();
    }
    var passing = FriendRiskSupport.choose(view, facts, legal);
    if (passing.isPresent()) {
      return passing.get();
    }
    return FollowStrategy.choose(view, facts, legal);
  }

  /**
   * 초구 A 프렌드의 무효 조커 처리와 조커프 주공의 사이드 신호를 우선합니다.
   */
  private static Optional<PlayDecision> respectOpeningSignal(View view, PlayFacts facts,
      List<Action> legal) {
    if (view.contract().trump() == Trump.NO_TRUMP && view.trickNumber().value() == 1
        && view.friendSelection() instanceof CardFriend friend
        && friend.card() instanceof StandardCard c && c.rank() == Rank.ACE
        && !c.equals(Rules.mighty(view.contract().trump()))) {
      var deadJoker = legal.stream().filter(a -> a.card() instanceof Joker).findFirst();
      if (deadJoker.isPresent()) {
        return Optional.of(new PlayDecision(deadJoker.get(),
            "A 프렌드는 초구의 무효 조커를 버려 주공의 총이 지정 A의 응답을 방해하지 않게 합니다."));
      }
    }
    if (view.contract().trump() != Trump.NO_TRUMP
        && DeclarerOpeningSignal.sidePointLead(view).isPresent()) {
      var passing = legal.stream().filter(action -> action.card() instanceof StandardCard card
          && !view.contract().trump().matches(card.suit())
          && !card.equals(Rules.mighty(view.contract().trump()))
          && !facts.winningActions().contains(action)).toList();
      if (!passing.isEmpty()) {
        return Optional.of(new PlayDecision(PlaySupport.cheapest(passing, view),
            "조커프 주공의 사이드 초구에 간으로 선을 빼앗지 않습니다."));
      }
    }
    return Optional.empty();
  }

  /**
   * 이미 공개된 주력 순회를 새 호출로 오인하지 않고 점수 지원과 조커 보존을 판단합니다.
   */
  private static Optional<PlayDecision> supportContinuingRun(View view, PlayFacts facts,
      List<Action> legal) {
    if (FriendSupportIntent.primaryRunContinuing(view, facts)) {
      var passing = facts.passingActions(PlaySupport.ordinary(legal, view));
      FriendResources resources = new FriendResources(view, facts);
      var points = passing.stream().filter(a -> a.card().points() > 0)
          .filter(a -> !resources.preserve((StandardCard) a.card()))
          .min(Comparator.comparingInt(a -> resources.supportCost((StandardCard) a.card())));
      if (points.isPresent()) {
        return Optional.of(new PlayDecision(points.get(),
            "공개된 주력 순회에 점수를 미리 지원해 마지막 구의 강제 점수 제출을 막습니다."));
      }
      if (!passing.isEmpty()) {
        return Optional.of(new PlayDecision(passing.stream().min(Comparator.comparingInt(
            a -> resources.discardCost((StandardCard) a.card()))).orElseThrow(),
            "공개된 주력 승리 순회를 새 호출로 오해하지 않고 조커를 사이드 요청에 남깁니다."));
      }
    }
    return Optional.empty();
  }

  /**
   * 앞선 호출·동료 보호가 해당하지 않을 때 일반 승리, 마이티, 조커 순으로 확인합니다.
   */
  private static Optional<PlayDecision> secureWinningSupport(View view, PlayFacts facts,
      List<Action> legal) {
    if (view.trick().size() == 4 && !facts.allyWinning()) {
      var winners = PlaySupport.ordinary(facts.winningActions(), view);
      if (!winners.isEmpty()) {
        return Optional.of(new PlayDecision(PlaySupport.cheapest(winners, view),
            "마지막 자리에서 실제 승리를 확인하고 가장 작은 일반 카드로 이깁니다."));
      }
    }
    if (!facts.allyWinning()) {
      var tops = PlaySupport.ordinary(facts.winningActions(), view).stream()
          .filter(a -> facts.isSuitTop(a.card())).toList();
      if (!tops.isEmpty()) {
        return Optional.of(new PlayDecision(PlaySupport.cheapest(tops, view),
            "주공의 앞뒤 위치와 관계없이 실제 야당 선두를 일반 탑카로 잡아 지원합니다."));
      }
      var mightyAction = legal.stream().filter(a -> a.card().equals(
          Rules.mighty(view.contract().trump()))).findFirst();
      List<Action> ordinaryWinners = PlaySupport.ordinary(facts.winningActions(), view);
      boolean ordinaryTop = ordinaryWinners.stream().anyMatch(a -> facts.isSuitTop(a.card())
          || facts.isSecured(new PublicPlay(view.self(), a)));
      if (mightyAction.isPresent() && !ordinaryTop) {
        return Optional.of(
            new PlayDecision(mightyAction.get(), "실제 야당 선두를 불확실한 Q로 덮지 않고 마이티로 잡아 주공에게 연결합니다."));
      }
    }
    if (!view.contract().trump().matches(facts.conditions().leadSuit())) {
      List<Action> tops = PlaySupport.ordinary(facts.winningActions(), view).stream()
          .filter(a -> ((StandardCard) a.card()).suit() == facts.conditions().leadSuit())
          .filter(a -> facts.isSuitTop(a.card())).toList();
      if (!tops.isEmpty()) {
        return Optional.of(new PlayDecision(PlaySupport.cheapest(tops, view),
            "주공이 이미 확보한 승리가 아니므로 돌아온 문양을 일반 탑카로 잡아 반환 기회를 만듭니다."));
      }
    }
    if (!facts.allyWinning()) {
      var joker = facts.winningActions().stream().filter(a -> a.card() instanceof Joker)
          .findFirst();
      if (joker.isPresent()) {
        return Optional.of(new PlayDecision(joker.get(),
            "불확실한 일반 역전 대신 조커로 야당 선두를 잡고 주공에게 반환합니다."));
      }
    }
    return Optional.empty();
  }

}
