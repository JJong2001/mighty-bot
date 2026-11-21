package mighty.ai.play.declarer;

import java.util.*;
import mighty.ai.play.*;
import mighty.game.action.Action;
import mighty.game.rule.Rules;
import mighty.model.*;

/**
 * 마이티 지원으로 줄일 손실과 스스로 운영할 주력을 분리합니다.
 */
final class SupportCallPlan {

  private SupportCallPlan() {
  }

  static Optional<PlayDecision> choose(DeclarerPosition position) {
    var view = position.view();
    if (view.contract().trump() != Trump.NO_TRUMP || !position.calls(
        Rules.mighty(Trump.NO_TRUMP))) {
      return Optional.empty();
    }
    var main = position.inSuit(position.primarySuit());
    var earlyRequest = requestBeforeSpendingEntry(position, main);
    if (earlyRequest.isPresent()) {
      return earlyRequest;
    }
    long connected = main.stream().filter(a -> position.facts().isSuitTop(a.card())).count();
    long alreadyWon = view.context().history().stream().filter(t -> t.winner().equals(view.self()))
        .flatMap(t -> t.cards().stream()).filter(p -> p.seat().equals(view.self()))
        .filter(
            p -> p.action().card() instanceof StandardCard c && c.suit() == position.primarySuit()
                && c.points() > 0 && !c.equals(Rules.mighty(Trump.NO_TRUMP))).count();
    boolean selfControlled = position.initialLength(position.primarySuit()) >= 5
        && connected + alreadyWon >= 2 && view.hand().contains(Joker.INSTANCE);
    if (!selfControlled) {
      return Optional.empty();
    }
    var establishedRun = playEstablishedRun(position, main);
    if (establishedRun.isPresent()) {
      return establishedRun;
    }
    return position.ordinary().stream()
        .filter(a -> ((StandardCard) a.card()).suit() != position.primarySuit())
        .filter(a -> a.card().points() == 0 && !position.facts().isSuitTop(a.card()))
        .max(Comparator.<Action>comparingInt(a -> benefit(position, (StandardCard) a.card()))
            .thenComparingInt(a -> -((StandardCard) a.card()).rank().value()))
        .map(a -> new PlayDecision(a, "주력의 자력 운영을 남기고 사이드 손실과 상위 연결을 보완할 문양에서 마이티를 찾습니다."));
  }

  /**
   * 상위가 끊긴 문양은 먼저 지원을 받아 탑카 회수와 조커컴을 연결합니다.
   */
  private static Optional<PlayDecision> requestBeforeSpendingEntry(DeclarerPosition position,
      List<Action> main) {
    var view = position.view();
    if (!view.hand().contains(Joker.INSTANCE)) {
      return Optional.empty();
    }

    var mightySuit = Rules.mighty(Trump.NO_TRUMP).suit();
    var holding = mightySuit == position.primarySuit() ? main : position.inSuit(mightySuit);
    var mightySuitRequest = requestInSuit(position, holding);
    if (mightySuitRequest.isPresent()) {
      return mightySuitRequest;
    }
    return requestInSuit(position, main);
  }

  private static Optional<PlayDecision> requestInSuit(DeclarerPosition position,
      List<Action> holding) {
    if (holding.stream().noneMatch(action -> action.card().points() > 0
        && position.facts().isSuitTop(action.card()))
        || holding.stream().noneMatch(action -> action.card().points() > 0
        && !position.facts().isSuitTop(action.card()))) {
      return Optional.empty();
    }

    return holding.stream()
        .filter(action -> action.card().points() == 0 && !position.facts().isSuitTop(action.card()))
        .min(Comparator.comparingInt(action -> ((StandardCard) action.card()).rank().value()))
        .map(action -> new PlayDecision(action,
            "막힌 상위를 뺄 수 있도록 낮은 카드로 마이티를 먼저 찾고 자기 탑카와 조커를 남깁니다."));
  }

  private static Optional<PlayDecision> playEstablishedRun(DeclarerPosition position,
      List<Action> main) {
    var view = position.view();
    var facts = position.facts();
    // 짧은 연결은 먼저 지원을 받는 기존 운영을 유지합니다.
    var initial = new ArrayList<Card>(view.hand());
    facts.cards().publicHistory().completedPlays().stream()
        .filter(play -> play.seat().equals(view.self()))
        .forEach(play -> initial.add(play.action().card()));
    var original = mighty.ai.bidding.BiddingHand.from(initial);
    // 일반 상위가 이어져도 지정 마이티 문양을 먼저 돌리면 친구의 지원을 강제로 씁니다.
    if (position.primarySuit() == Rules.mighty(Trump.NO_TRUMP).suit()
        || original.ordinaryTopRunLength(position.primarySuit(), Trump.NO_TRUMP) < 4) {
      return Optional.empty();
    }
    var tops = main.stream()
        .filter(action -> action.card().points() > 0 && facts.isSuitTop(action.card())).toList();
    if (!tops.isEmpty()) {
      return Optional.of(new PlayDecision(PlaySupport.highest(tops),
          "자력으로 이기는 주력을 먼저 쓰고 사이드 상위가 빠지는지 확인합니다."));
    }
    var sides = position.sides().stream()
        .filter(action -> ((StandardCard) action.card()).suit() != position.primarySuit()).toList();
    var sideTops = sides.stream().filter(action -> facts.isSuitTop(action.card())).toList();
    if (!sideTops.isEmpty()) {
      return Optional.of(new PlayDecision(PlaySupport.highest(sideTops),
          "주력을 돌리는 동안 승격된 사이드 탑카를 사용합니다."));
    }
    return sides.stream().filter(action -> action.card().points() > 0)
        .filter(action -> {
          var card = (StandardCard) action.card();
          return position.inSuit(card.suit()).stream().anyMatch(higher ->
              ((StandardCard) higher.card()).rank().value() > card.rank().value());
        })
        .min(Comparator.comparingInt(action -> ((StandardCard) action.card()).rank().value()))
        .map(action -> new PlayDecision(action,
            "사이드 중간 점수로 마이티 지원을 요청하고 남은 상위를 압박합니다."));
  }

  private static int benefit(DeclarerPosition position, StandardCard low) {
    var held = position.inSuit(low.suit());
    int highest = held.stream().mapToInt(a -> ((StandardCard) a.card()).rank().value()).max()
        .orElseThrow();
    long blockers = position.facts().cards().outsideCards().stream()
        .filter(c -> c instanceof StandardCard higher
            && higher.suit() == low.suit() && !c.equals(
            Rules.mighty(position.view().contract().trump()))
            && higher.rank().value() > highest).count();
    return 100 + (held.size() == 1 ? 20 : 0) + (highest >= Rank.TEN.value() ? 30 : 0)
        - (int) blockers * 5;
  }

}
