package mighty.ai.play;

import java.util.*;
import mighty.game.Seat;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.*;

/** 현재 선두 이후의 간 가능성과 지원 후 남는 통제를 구분합니다. 상대 실제 패는 읽지 않습니다. */
public final class TrickContinuation {
  public record RuffOpportunity(Seat seat, StandardCard card, boolean knownVoid,
                                double ownershipWeight) {}

  private final View view;
  private final PlayFacts facts;

  public TrickContinuation(View view, PlayFacts facts) {
    this.view = view;
    this.facts = facts;
  }

  public Optional<RuffOpportunity> alliedRuffBehind() {
    if (view.trick().isEmpty() || view.contract().trump() == Trump.NO_TRUMP
        || view.contract().trump().matches(facts.conditions().leadSuit())) return Optional.empty();
    var winner = facts.currentWinner().orElseThrow();
    int strength = Rules.strength(winner.action().card(), facts.conditions());
    List<RuffOpportunity> opportunities = new ArrayList<>();
    for (var trump : facts.cards().trumps().outside()) {
      if (trump.equals(Rules.mighty(view.contract().trump()))
          || Rules.strength(trump, facts.conditions()) <= strength) continue;
      var weights = facts.cards().trumps().ownerProbabilities(trump, facts);
      for (var seat : facts.remainingSeats()) {
        if (!facts.teams().isAlly(seat) || !facts.locations().possibleHolders(trump).contains(seat)) continue;
        boolean voidKnown = facts.cards().isVoid(seat, facts.conditions().leadSuit());
        double weight = weights.getOrDefault(seat, 0.0);
        // 무문양이면 가능한 간을 검토하고, 무문양도 모르면 기루 위치 근거가 우세할 때만 검토합니다.
        if ((!voidKnown && weight < 0.5) || weight <= 0 || enemyCanOvertakeAfter(seat, trump)) continue;
        opportunities.add(new RuffOpportunity(seat, trump, voidKnown, weight));
      }
    }
    return opportunities.stream().max(Comparator.comparing(RuffOpportunity::knownVoid)
        .thenComparingDouble(RuffOpportunity::ownershipWeight));
  }

  private boolean enemyCanOvertakeAfter(Seat actor, Card card) {
    var remaining = facts.remainingSeats();
    int strength = Rules.strength(card, facts.conditions());
    return remaining.subList(remaining.indexOf(actor) + 1, remaining.size()).stream()
        .filter(s -> !facts.teams().isAlly(s)).anyMatch(s -> facts.cards().outsideCards().stream()
            .anyMatch(c -> Rules.strength(c, facts.conditions()) > strength
                && facts.locations().possibleHolders(c).contains(s)));
  }

  /** 이미 두 점 이상 걸린 동료 승리에 마지막 일반 탑까지 실어 주공의 다음 점수 탑을 만들지 않습니다. */
  public boolean spendsLastPointGuard(StandardCard card) {
    if (view.trick().isEmpty() || !facts.allyWinning() || facts.trickPoints() < 2
        || card.equals(Rules.mighty(view.contract().trump()))
        || card.suit() != facts.conditions().leadSuit() || !facts.isSuitTop(card)) return false;
    boolean hasOtherControl = view.hand().stream().filter(StandardCard.class::isInstance)
        .map(StandardCard.class::cast).anyMatch(c -> !c.equals(card) && c.suit() == card.suit()
            && facts.isSuitTop(c));
    return !hasOtherControl && !facts.locations()
        .possiblePointCards(view.context().declarer(), card.suit()).isEmpty();
  }
}
