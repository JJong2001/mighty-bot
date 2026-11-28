package mighty.ai.play.opposition;

import java.util.*;
import mighty.ai.play.*;
import mighty.game.action.Action;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.*;

/**
 * 주공의 운영을 막는 수비를 남기고, 다른 문양의 점수는 현재 공격 기회에 처리합니다.
 */
final class OppositionSupport {

  private OppositionSupport() {
  }

  /**
   * 마이티가 소모된 뒤 야당 조커가 구를 확보하면 짧은 점수 기루를 처리합니다.
   */
  static Optional<PlayDecision> feedSecuredJoker(View view, PlayFacts facts, List<Action> legal) {
    var winner = facts.currentWinner().orElseThrow();
    if (!(winner.action().card() instanceof Joker) || !facts.allyWinning()
        || !view.publicCardHistory().cards().contains(Rules.mighty(view.contract().trump()))) {
      return Optional.empty();
    }
    var trumps = facts.cards().trumps().own();
    if (trumps.size() > 2) {
      return Optional.empty();
    }
    return PlaySupport.ordinary(legal, view).stream()
        .filter(action -> trumps.contains(action.card()) && action.card().points() > 0)
        .filter(action -> !facts.isSuitTop(action.card()))
        .min(Comparator.comparingInt(action -> supportCost((StandardCard) action.card())))
        .map(action -> new PlayDecision(action, "마이티가 빠져 야당 조커가 확보한 구에 짧은 점수 기루를 싣습니다."));
  }

  static Optional<PlayDecision> choose(View view, PlayFacts facts, List<Action> legal) {
    boolean teamCanTake = oppositionBehindCanTake(view, facts);
    var ruff = new TrickContinuation(view, facts).alliedRuffBehind();
    // 유일한 점수는 불확실한 간에 걸지 않습니다. 무문양과 기루 위치가 함께 강할 때는 예외입니다.
    boolean ruffSupport = ruff.filter(r -> view.hand().stream().filter(c -> c.points() > 0).count() >= 2
        || r.knownVoid() && r.ownershipWeight() >= 0.75).isPresent();
    if (!teamCanTake && !supportOpportunity(view, facts) && !ruffSupport) {
      return Optional.empty();
    }
    DeclarerPressure pressure = new DeclarerPressure(view, facts);
    int strength = Rules.strength(facts.currentWinner().orElseThrow().action().card(),
        facts.conditions());
    return PlaySupport.ordinary(legal, view).stream().filter(a -> a.card().points() > 0)
        .filter(a -> !view.contract().trump().matches(((StandardCard) a.card()).suit()))
        .filter(a -> Rules.strength(a.card(), facts.conditions()) <= strength
            || (facts.allyWinning() && !view.contract().trump()
            .matches(facts.conditions().leadSuit())
            && (shortPoints(view)
            || view.hand().stream().filter(c -> c.points() > 0).count() >= 4)))
        .filter(a -> !pressure.defense((StandardCard) a.card()))
        .min(Comparator.<Action>comparingInt(
                a -> facts.cards().isVoid(view.context().declarer(), ((StandardCard) a.card()).suit())
                    ? 0 : 1)
            .thenComparingInt(a -> supportCost((StandardCard) a.card())))
        .map(a -> new PlayDecision(a, "주공 부기루를 직접 막을 수비를 남기고 다른 문양의 점수를 공격 기회에 지원합니다."));
  }

  private static boolean oppositionBehindCanTake(View view, PlayFacts facts) {
    var friend = facts.teams().friendSeat();
    if (friend.isEmpty() || facts.remainingSeats().isEmpty()
        || !facts.remainingSeats().stream().allMatch(facts.teams()::isAlly)) {
      return false;
    }
    var winner = facts.currentWinner().orElseThrow();
    if (facts.allyWinning()) {
      return true;
    }
    if (winner.action().card().points() != 0 || !(winner.action()
        .card() instanceof StandardCard card)) {
      return false;
    }
    int strength = Rules.strength(card, facts.conditions());
    return facts.cards().outsideCards().stream().filter(StandardCard.class::isInstance)
        .map(StandardCard.class::cast)
        .filter(higher -> higher.suit() == facts.conditions().leadSuit()
            && !higher.equals(Rules.mighty(view.contract().trump())))
        .anyMatch(higher -> Rules.strength(higher, facts.conditions()) > strength
            && facts.remainingSeats().stream()
            .anyMatch(seat -> !facts.cards().isVoid(seat, higher.suit())));
  }

  private static boolean shortPoints(View view) {
    return java.util.Arrays.stream(Suit.values()).filter(s -> !view.contract().trump().matches(s))
        .anyMatch(s -> {
          var cards = view.hand().stream()
              .filter(c -> c instanceof StandardCard card && card.suit() == s).toList();
          return cards.size() >= 2 && cards.size() <= 3 && cards.stream()
              .anyMatch(c -> c.points() > 0);
        });
  }

  private static int supportCost(StandardCard card) {
    // 다른 지원 점수가 있으면 A를 남기되, 부기루 수비보다 A를 무조건 우선하지 않습니다.
    return (card.rank() == Rank.ACE ? 100 : 0) + card.rank().value();
  }

  private static boolean supportOpportunity(View view, PlayFacts facts) {
    var winner = facts.currentWinner().orElseThrow();
    // 주공이 이미 지는 카드를 낸 기회는 길이나 보유 점수 개수로 막지 않습니다.
    // 뒤 프렌드가 역전할 수 있다는 것만으로 지원을 포기하지 않되, 위의 defense 필터는 유지합니다.
    if (facts.allyWinning() && view.trick().stream()
        .anyMatch(p -> p.seat().equals(view.context().declarer()))) {
      return true;
    }
    if (facts.allyWinning() && winner.action().card() instanceof StandardCard ruff
        && view.contract().trump().matches(ruff.suit())
        && ruff.suit() != facts.conditions().leadSuit()) {
      return true;
    }
    if (facts.allyWinning() && (facts.isSecured(winner) || facts.isSuitTop(winner.action().card())
        || winner.action().card().equals(Rules.mighty(view.contract().trump())))) {
      return true;
    }
    boolean declarerOut = facts.discardedOffSuit(view.context().declarer());
    if (declarerOut && !winner.seat().equals(view.context().declarer())
        && !facts.teams().friendSeat().filter(winner.seat()::equals).isPresent()) {
      return true;
    }
    long pointLoad = view.hand().stream().filter(c -> c.points() > 0).count();
    if (facts.allyWinning() && (pointLoad >= 4 || shortPoints(view)) && !view.contract().trump()
        .matches(facts.conditions().leadSuit())
        && winner.action().card() instanceof StandardCard) {
      return true;
    }
    var lead = view.trick().get(0);
    if (!winner.seat().equals(view.context().declarer()) || !lead.seat()
        .equals(view.context().declarer())
        || !(lead.action().card() instanceof StandardCard card)) {
      return false;
    }
    if (view.contract().trump().matches(card.suit())) {
      return supportsTrumpWinnerBehind(view, facts, card);
    }
    boolean ordinaryAttack = facts.cards().outsideCards().stream()
        .filter(c -> c instanceof StandardCard higher
            && higher.suit() == card.suit() && higher.rank().value() > card.rank().value()
            && !c.equals(Rules.mighty(view.contract().trump())) && !facts.cards()
            .isDesignatedFriendCard(c))
        // 초구 K 등으로 주공의 잔여 A가 시사되면 이를 뒤 야당의 공격 카드로 세지 않습니다.
        .filter(c -> !facts.locations().suggestedHolders(c).contains(view.context().declarer()))
        .anyMatch(c -> facts.remainingSeats().stream()
            .anyMatch(s -> !s.equals(view.context().declarer())
                && !facts.teams().friendSeat().filter(s::equals).isPresent()
                && facts.locations().supportedHolder(s, c)));
    if (!ordinaryAttack) {
      return false;
    }
    // 특정 A나 K가 아니라 미소모 일반 상위와 뒤의 공격 가능 자리를 함께 확인합니다.
    return facts.remainingSeats().stream().anyMatch(s -> !s.equals(view.context().declarer())
        && !facts.teams().friendSeat().filter(s::equals).isPresent() && !facts.cards()
        .isVoid(s, card.suit()));
  }

  /**
   * 주공이 상위 기루를 돌린 뒤 낮춰 내면 뒤 야당의 미소모 탑 기루에 점수를 지원합니다.
   */
  private static boolean supportsTrumpWinnerBehind(View view, PlayFacts facts, StandardCard lead) {
    if (lead.points() > 0 || facts.cards().outsideCards()
        .contains(Rules.mighty(view.contract().trump()))
        || (Rules.strength(Joker.INSTANCE, facts.conditions()) > Rules.strength(lead,
        facts.conditions()) && facts.cards().outsideCards().contains(Joker.INSTANCE))) {
      return false;
    }
    var top = facts.cards().outsideCards().stream()
        .filter(StandardCard.class::isInstance).map(StandardCard.class::cast)
        .filter(card -> card.suit() == lead.suit() && card.rank().value() > lead.rank().value())
        .max(Comparator.comparingInt(card -> card.rank().value()));
    if (top.isEmpty() || facts.cards().isDesignatedFriendCard(top.get())) {
      return false;
    }
    boolean declarerPreviouslyLedAboveTop = view.context().history().stream()
        .filter(trick -> trick.leader().equals(view.context().declarer()))
        .map(trick -> trick.cards().get(0).action().card())
        .anyMatch(card -> card instanceof StandardCard prior && prior.suit() == lead.suit()
            && prior.rank().value() > top.get().rank().value());
    if (!declarerPreviouslyLedAboveTop) {
      return false;
    }
    var candidates = facts.remainingSeats().stream()
        .filter(seat -> !facts.cards().isVoid(seat, lead.suit())).toList();
    return !candidates.isEmpty() && candidates.stream().allMatch(facts.teams()::isAlly);
  }

}
