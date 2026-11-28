package mighty.ai.play.opposition;

import mighty.game.rule.Rules;
import mighty.game.friend.CardFriend;
import java.util.Optional;
import java.util.*;
import mighty.ai.play.*;
import mighty.game.action.*;
import mighty.game.observation.View;
import mighty.model.*;

/**
 * 야당의 기루 압박·장수 수비와 점수 지원을 전용 우선순위로 처리합니다.
 */
final class OppositionFollowStrategy {

  static PlayDecision choose(View view, PlayFacts facts, List<Action> legal) {
    var continuation = new TrickContinuation(view, facts);
    if (ScoreSupport.decisive(view, facts, legal).isEmpty()) {
      var points = PlaySupport.ordinary(legal, view).stream().filter(a -> a.card().points() > 0).toList();
      if (!points.isEmpty() && points.stream()
          .allMatch(a -> continuation.spendsLastPointGuard((StandardCard) a.card()))) {
        var low = facts.passingActions(PlaySupport.ordinary(legal, view)).stream()
            .filter(a -> a.card().points() == 0).toList();
        if (!low.isEmpty()) return new PlayDecision(PlaySupport.cheapest(low, view),
            "동료가 이미 확보 중인 점수에 마지막 탑까지 겹치지 않고 주공의 다음 점수 카드를 막습니다.");
      }
    }
    var defense = OppositionPressure.preserveOperatingDefense(view, facts, legal);
    if (defense.isPresent()) {
      return defense.get();
    }
    var specialAttack = chooseSpecialAttack(view, facts, legal);
    if (specialAttack.isPresent()) {
      return specialAttack.get();
    }
    var jokerSupport = OppositionSupport.feedSecuredJoker(view, facts, legal);
    if (jokerSupport.isPresent()) {
      return jokerSupport.get();
    }
    var lowLeadAttack = OppositionPointCapture.attackLowLead(view, facts);
    if (lowLeadAttack.isPresent()) {
      return lowLeadAttack.get();
    }
    var decisive = ScoreSupport.decisive(view, facts, legal);
    if (decisive.isPresent()) {
      return new PlayDecision(decisive.get(), "현재 성패와 남은 공격 기회를 고려해 확보된 야당 승리에 점수를 지원합니다.");
    }
    var failurePressure = ContractFailurePressure.follow(view, facts, legal);
    if (failurePressure.isPresent()) {
      return failurePressure.get();
    }
    var securedSide = OppositionRuffTiming.keepSecuredSide(view, facts, legal);
    if (securedSide.isPresent()) {
      return securedSide.get();
    }
    var openingPressure = OppositionPressure.choose(view, facts, legal);
    if (openingPressure.isPresent()) {
      return openingPressure.get();
    }
    boolean declarerDiscarded = facts.discardedOffSuit(view.context().declarer());
    if (declarerDiscarded) {
      var support = OppositionSupport.choose(view, facts, legal);
      if (support.isPresent()) {
        return support.get();
      }
    }
    var pointCapture = OppositionPointCapture.choose(view, facts, legal);
    if (pointCapture.isPresent()) {
      return pointCapture.get();
    }
    var deferredRuff = OppositionRuffTiming.choose(view, facts, legal);
    if (deferredRuff.isPresent()) {
      return deferredRuff.get();
    }
    if (!facts.allyWinning() && !view.contract().trump().matches(facts.conditions().leadSuit())) {
      List<Action> winners = PlaySupport.ordinary(facts.winningActions(), view);
      if (!winners.isEmpty()) {
        return new PlayDecision(RuffPlan.choose(view, facts, winners)
            .orElseGet(() -> PlaySupport.cheapest(winners, view)),
            "문양과 숫자에 관계없이 여당의 현재 선두를 최소 일반 승리 카드로 공격합니다.");
      }
    }
    var feed = OppositionSupport.choose(view, facts, legal);
    if (feed.isPresent()) {
      return feed.get();
    }
    if (view.contract().trump().matches(facts.conditions().leadSuit())) {
      return OppositionTrumpFollow.choose(view, facts, legal);
    }
    return FollowStrategy.choose(view, facts, legal);
  }

  private static boolean protectsTrumpLineOnMighty(View view, PlayFacts facts,
      List<StandardCard> trumps) {
    if (trumps.size() == 1) {
      return facts.isSuitTop(trumps.get(0));
    }
    if (!view.contract().trump().matches(facts.conditions().leadSuit()) || trumps.size() < 2) {
      return false;
    }
    var highest = trumps.stream().max(Comparator.comparingInt(card -> card.rank().value()))
        .orElseThrow();
    long higher = facts.cards().trumps().outside().stream()
        .filter(card -> card.rank().value() > highest.rank().value()).count();
    long honours = trumps.stream().filter(card -> card.points() > 0).count();
    // 조커를 내면 이후 상위를 따라낼 장수와 점수 기루를 함께 남길 수 있습니다.
    return highest.points() > 0 && trumps.size() > higher + 1
        && (honours >= 2 || trumps.size() >= 4);
  }

  /**
   * 공통 기한 판단을 통과한 뒤 마이티 소모와 조커 공격의 가치를 판단합니다.
   */
  private static Optional<PlayDecision> chooseSpecialAttack(View view, PlayFacts facts,
      List<Action> legal) {
    var winner = facts.currentWinner().orElseThrow();
    if (winner.action().card().equals(Rules.mighty(view.contract().trump()))) {
      var trumps = new TrumpControl(view, facts).own();
      var joker = legal.stream().filter(a -> a.card() instanceof Joker).findFirst();
      if (joker.isPresent() && protectsTrumpLineOnMighty(view, facts, trumps)) {
        return Optional.of(
            new PlayDecision(joker.get(), "마이티에 조커를 소모하고 일반 기루 상위와 그 아래 보호 장수를 남깁니다."));
      }
    }
    if (!facts.allyWinning()) {
      boolean mightyFriend = view.friendSelection() instanceof CardFriend f
          && f.card().equals(Rules.mighty(view.contract().trump()));
      if (mightyFriend && winner.seat().equals(view.context().declarer())
          && winner.action().card() instanceof StandardCard current
          && current.suit() == facts.conditions().leadSuit()
          && facts.cards().outsideCards().contains(Rules.mighty(view.contract().trump()))
          && facts.remainingSeats().stream().anyMatch(seat -> facts.teams().friendSeat()
          .map(seat::equals).orElse(true))) {
        var ownTrump = new TrumpControl(view, facts).own();
        boolean trumpLead = view.contract().trump().matches(current.suit());
        boolean survives = trumpLead && ownTrump.stream()
            .max(Comparator.comparingInt(card -> card.rank().value()))
            .filter(card -> new TrumpSurvival(view, facts).survives(card)).isPresent();
        var pressure = PlaySupport.ordinary(facts.winningActions(), view).stream()
            .filter(legal::contains)
            .filter(action -> ((StandardCard) action.card()).suit() == current.suit())
            .filter(action -> action.card().points() > 0 || facts.isSuitTop(action.card()))
            .filter(action -> !trumpLead || !survives && !facts.isSuitTop(action.card()))
            .min(Comparator.comparingInt(action -> ((StandardCard) action.card()).rank().value()));
        if (pressure.isPresent()) {
          return Optional.of(new PlayDecision(pressure.get(),
              "주공의 현재 카드를 최소 상위로 압박해 뒤 마이티 프렌드의 지원을 요구합니다."));
        }
      }
      int points = facts.trickPoints();
      var joker = facts.winningActions().stream().filter(a -> a.card() instanceof Joker)
          .findFirst();
      if (joker.isPresent() && points >= 2 && new TrumpControl(view, facts).own().size() >= 3
          && !view.contract().trump().matches(facts.conditions().leadSuit())) {
        return Optional.of(new PlayDecision(joker.get(),
            "조커로 주공의 선과 점수를 빼앗고 여러 장의 기루를 이후 간 공격에 남깁니다."));
      }
    }
    var jokerPressure = OppositionJokerPressure.choose(view, facts, legal);
    if (jokerPressure.isPresent()) {
      return jokerPressure;
    }
    if (!facts.allyWinning() && view.contract().trump().matches(facts.conditions().leadSuit())) {
      var own = new TrumpControl(view, facts).own();
      var joker = facts.winningActions().stream().filter(a -> a.card() instanceof Joker)
          .findFirst();
      if (joker.isPresent() && own.size() >= 2 && winner.action().card().points() > 0
          && PlaySupport.ordinary(facts.winningActions(), view).isEmpty()) {
        return Optional.of(
            new PlayDecision(joker.get(), "조커로 점수 기루를 잡고 자기 기루의 장수와 후속 공격 선택권을 남깁니다."));
      }
    }
    return Optional.empty();
  }

}
