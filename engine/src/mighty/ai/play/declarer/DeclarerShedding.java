package mighty.ai.play.declarer;

import java.util.Comparator;
import java.util.Optional;
import mighty.ai.play.*;
import mighty.game.action.Action;
import mighty.model.StandardCard;

/**
 * 지금 버릴 손실과 나중에 직접 맞으러 나갈 손실을 비교합니다. 특정 점수 숫자에 묶지 않습니다.
 */
final class DeclarerShedding {

  private DeclarerShedding() {
  }

  static Optional<PlayDecision> keepSideCover(DeclarerPosition position) {
    var facts = position.facts();
    var view = position.view();
    if (!facts.allyWinning() || !facts.isSecured(facts.currentWinner().orElseThrow())) {
      return Optional.empty();
    }
    return position.ordinary().stream().filter(action -> action.card().points() > 0)
        .filter(action -> !facts.winningActions().contains(action))
        .filter(action -> {
          var card = (StandardCard) action.card();
          if (card.suit() == facts.conditions().leadSuit() || view.contract().trump()
              .matches(card.suit())) {
            return false;
          }
          var side = SuitDevelopment.analyze(view, facts, card.suit());
          return side.held().size() == 2 && side.held().get(0).equals(card)
              && side.held().get(1).points() == 0 && !side.blockers().isEmpty();
        })
        .findFirst().map(action -> new PlayDecision(action,
            "보호 숫자를 버려 막힌 점수를 남기기 전에 확보된 프렌드 승리에 점수를 처리합니다."));
  }

  static Optional<PlayDecision> preventLargeLoss(DeclarerPosition position) {
    var facts = position.facts();
    var view = position.view();
    if (facts.allyWinning()) {
      return Optional.empty();
    }
    int points = facts.trickPoints();
    int behind = (int) facts.remainingSeats().stream().filter(seat -> !facts.teams().isAlly(seat))
        .count();
    boolean friendLedPoint = facts.teams().isAlly(view.trick().get(0).seat())
        && view.trick().get(0).action().card().points() > 0;
    if (points < 2 && !(friendLedPoint && points + behind >= 3)) {
      return Optional.empty();
    }
    if (behind == 0 && !friendLedPoint && points < 3) {
      return Optional.empty();
    }
    var safe = facts.winningActions().stream().filter(position.legal()::contains)
        .filter(action -> !(action.card() instanceof StandardCard card) || !view.contract().trump()
            .matches(card.suit()) || view.contract().trump().matches(facts.conditions().leadSuit()))
        .filter(action -> facts.isSecured(new mighty.game.trick.PublicPlay(view.self(), action))
            || action.card() instanceof StandardCard card
            && card.suit() == facts.conditions().leadSuit() && facts.isSuitTop(card))
        .toList();
    return safe.isEmpty() ? Optional.empty()
        : Optional.of(new PlayDecision(SpecialCardPlan.recoveryFirst(safe, view, facts),
            "점수가 더 모이기 전에 확보할 수 있는 구를 잡습니다."));
  }

  static Optional<PlayDecision> choose(DeclarerPosition position) {
    var view = position.view();
    var facts = position.facts();
    if (facts.allyWinning() || view.contract().trump().matches(facts.conditions().leadSuit())) {
      return Optional.empty();
    }
    var water = new DeclarerPlan(position).water();
    int points = facts.trickPoints();
    var leadSuit = facts.conditions().leadSuit();
    long outsideSuit = facts.cards().outsideCount(leadSuit);
    long outsidePoints = facts.cards().outsideCards().stream()
        .filter(c -> c instanceof StandardCard card
            && card.suit() == leadSuit && c.points() > 0).count();
    long possibleFollowers = view.context().playOrder().seats().stream()
        .filter(s -> !s.equals(view.self()))
        .filter(s -> !facts.cards().isVoid(s, leadSuit)).count();
    double followShare = Math.min(1.0, (double) outsideSuit / Math.max(1, possibleFollowers));
    double suitPointShare = (double) outsidePoints / Math.max(1, outsideSuit);
    // 뒤의 손패는 모르므로 공개 잔여 문양·점수 비율로 추정합니다. 알려진 무문양은 최대 한 점 위험입니다.
    double behind = facts.remainingSeats().stream().filter(s -> !facts.teams().isAlly(s))
        .mapToDouble(s -> facts.cards().isVoid(s, leadSuit) ? 1.0
            : followShare * suitPointShare + (1.0 - followShare)).sum();
    DeclarerPlan plan = new DeclarerPlan(position);
    int specialLosses = (facts.cards().outsideCards().contains(mighty.model.Joker.INSTANCE) ? 1 : 0)
        + (facts.cards().outsideCards()
        .contains(mighty.game.rule.Rules.mighty(view.contract().trump())) ? 1 : 0);
    return position.ordinary().stream().filter(a -> water.contains(a.card()))
        .filter(a -> ((StandardCard) a.card()).suit() != facts.conditions().leadSuit())
        .filter(a -> {
          if (points == 0 && behind == 0) {
            return true;
          }
          double loss = points + a.card().points() + behind;
          if (a.card().points() == 0 && !plan.developments().isEmpty()) {
            return false;
          }
          if (a.card().points() == 0) {
            double budget = plan.remainingMastersAfter(a.card()) ? plan.lossAllowance()
                : plan.lossAllowance() / Math.max(1, water.size() + specialLosses);
            return loss <= budget;
          }
          return points < 2 && loss < a.card().points() + 3
              && (!plan.remainingMastersAfter(a.card()) || loss <= plan.lossAllowance());
        })
        .min(Comparator.<Action>comparingDouble(a -> points + a.card().points() + behind)
            .thenComparingInt(a -> -((StandardCard) a.card()).rank().value()))
        .map(a -> new PlayDecision(a, "현재 자기 카드까지 손실 " + (points + a.card().points())
            + "점과 뒤의 점수 위험을 추정해 나중의 집중 실점과 기루 소모를 줄입니다."));
  }

}
