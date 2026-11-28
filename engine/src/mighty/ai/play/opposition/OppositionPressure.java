package mighty.ai.play.opposition;

import java.util.*;
import mighty.ai.play.*;
import mighty.game.action.Action;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.*;

/**
 * 운영 문양의 실제 수비 보존과 초구의 점수 압박을 구분합니다.
 */
final class OppositionPressure {

  private OppositionPressure() {
  }

  static Optional<PlayDecision> preserveOperatingDefense(View view, PlayFacts facts,
      List<Action> legal) {
    if (view.trick().isEmpty()
        || !view.trick().get(0).seat().equals(view.context().declarer())
        || !(view.trick().get(0).action() instanceof mighty.game.action.StandardPlay lead)
        || lead.call() || lead.card().equals(Rules.mighty(view.contract().trump()))) {
      return Optional.empty();
    }
    Suit suit = facts.conditions().leadSuit();
    var inference = facts.declarerSuits().assess(suit);
    if (!inference.sideCandidate()) {
      return Optional.empty();
    }
    // 단 한 번의 중반 리드만으로 초구 예외를 확대하지 않습니다.
    if (!inference.reinforced()
        && !inference.evidence().contains(DeclarerSuitInference.Evidence.OPENING_POINT_SIGNAL)) {
      return Optional.empty();
    }
    DeclarerPressure pressure = new DeclarerPressure(view, facts);
    boolean hasDefense = PlaySupport.ordinary(legal, view).stream()
        .map(action -> (StandardCard) action.card())
        .anyMatch(card -> card.suit() == suit && pressure.defense(card));
    if (!hasDefense) {
      return Optional.empty();
    }
    // 주공의 현재 점수 카드를 잡을 수 있고 같은 문양의 후속 점수 후보가 없으면
    // 물패를 나중에 잡겠다고 이번 점수를 통과시키지 않습니다.
    var current = facts.currentWinner().orElseThrow();
    if (current.seat().equals(view.context().declarer())
        && current.action().card().points() > 0
        && !facts.winningActions().isEmpty()
        && facts.locations().possiblePointCards(view.context().declarer(), suit).isEmpty()) {
      return Optional.empty();
    }
    // 수비를 쓰지 않고도 잡을 수 있으면 굳이 흘리지 않습니다.
    boolean expendableWinner = PlaySupport.ordinary(facts.winningActions(), view).stream()
        .anyMatch(action -> !pressure.defense((StandardCard) action.card()));
    boolean specialWinner = facts.winningActions().stream().anyMatch(action ->
        action.card() instanceof Joker || action.card()
            .equals(Rules.mighty(view.contract().trump())));
    if (expendableWinner || specialWinner) {
      return Optional.empty();
    }

    var winner = facts.currentWinner().orElseThrow();
    int points = facts.trickPoints();
    boolean securedAlly = facts.allyWinning() && facts.isSecured(winner);
    boolean worthwhileCapture =
        points >= 2 && !facts.allyWinning() && !facts.winningActions().isEmpty();
    if (securedAlly || worthwhileCapture) {
      return Optional.empty();
    }

    return PlaySupport.ordinary(legal, view).stream()
        .filter(action -> ((StandardCard) action.card()).suit() == suit)
        .filter(action -> action.card().points() == 0)
        .filter(action -> !pressure.defense((StandardCard) action.card()))
        .filter(action -> !facts.winningActions().contains(action))
        .min(Comparator.comparingInt(action -> ((StandardCard) action.card()).rank().value()))
        .map(action -> new PlayDecision(action,
            "공개된 주공 운영 문양을 실제로 막을 높이와 장수가 있어 수비를 보존합니다."));
  }

  static Optional<PlayDecision> choose(View view, PlayFacts facts, List<Action> legal) {
    if (view.trickNumber().value() != 1 || view.contract().trump()
        .matches(facts.conditions().leadSuit())
        || !view.trick().get(0).seat().equals(view.context().declarer())
        || !facts.cards().outsideCards().contains(Rules.mighty(view.contract().trump()))) {
      return Optional.empty();
    }
    var winner = facts.currentWinner().orElseThrow();
    int points = facts.trickPoints();
    List<Action> ordinary = PlaySupport.ordinary(legal, view).stream()
        .filter(a -> ((StandardCard) a.card()).suit() == facts.conditions().leadSuit()).toList();
    boolean mightyBehind = facts.remainingSeats().stream()
        .anyMatch(s -> !s.equals(view.context().declarer())
            && !facts.teams().isAlly(s));
    boolean existingPressure =
        !winner.seat().equals(view.context().declarer()) && winner.action().card().points() > 0
            && !facts.teams().friendSeat().filter(winner.seat()::equals).isPresent();
    // 1점이 실렸다는 이유만으로 어차피 후속 상위에 지는 짧은 점수까지 아끼지 않습니다.
    var pressure = new DeclarerPressure(view, facts);
    boolean expendablePoint = ordinary.size() <= 2 && ordinary.stream()
        .anyMatch(a -> a.card().points() > 0 && !facts.isSuitTop(a.card())
            && !pressure.defense((StandardCard) a.card()));
    if (points < 2 && existingPressure && (facts.allyWinning() || mightyBehind)
        && !expendablePoint) {
      var low = ordinary.stream()
          .filter(a -> a.card().points() == 0 && !facts.winningActions().contains(a)).toList();
      if (!low.isEmpty()) {
        return Optional.of(new PlayDecision(PlaySupport.cheapest(low, view),
            "앞의 점수 압박이 있고 뒤 마이티가 남을 수 있어 높은 점수를 중복 소모하지 않습니다."));
      }
    }
    if (points == 0 && !facts.allyWinning() && ordinary.size() <= 3) {
      return ordinary.stream().filter(facts.winningActions()::contains)
          .filter(a -> a.card().points() > 0)
          .min(Comparator.comparingInt(a -> ((StandardCard) a.card()).rank().value()))
          .map(a -> new PlayDecision(a, "짧은 문양의 첫 점수 카드로 프렌드 지원을 압박하고 뒤 야당의 중복 소모를 줄입니다."));
    }
    return Optional.empty();
  }

}
