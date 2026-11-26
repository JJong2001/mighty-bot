package mighty.ai.play.friend;

import java.util.*;
import mighty.ai.play.*;
import mighty.game.action.*;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.*;

/**
 * 기루 순회 기록과 현재 패를 보고 호출 응답을 유예할 수 있는지 판단합니다.
 */
final class FriendTrumpResponse {

  private FriendTrumpResponse() {
  }

  static Optional<PlayDecision> choose(View view, PlayFacts facts, List<Action> legal) {
    var trump = view.contract().trump();
    if (trump == Trump.NO_TRUMP || view.trick().isEmpty()
        || !view.trick().get(0).seat().equals(view.context().declarer())
        || !(view.trick().get(0).action() instanceof StandardPlay lead)
        || lead.call() || lead.card().points() != 0 || !trump.matches(lead.card().suit())
        || !facts.cards().trumps().own().isEmpty()) {
      return Optional.empty();
    }
    Suit suit = lead.card().suit();
    var runs = view.context().history().stream().filter(t ->
        t.leader().equals(view.context().declarer())
            && t.cards().get(0).action() instanceof StandardPlay p && !p.call()
            && p.card().suit() == suit).toList();
    if (runs.isEmpty()) {
      return Optional.empty();
    }
    var mighty = Rules.mighty(trump);
    var special = facts.winningActions().stream().filter(legal::contains)
        .filter(a -> a.card().equals(mighty) || a.card() instanceof Joker).toList();
    if (special.isEmpty()) {
      return Optional.empty();
    }
    var holders = view.context().playOrder().seats().stream()
        .filter(s -> !facts.teams().isAlly(s) && !facts.cards().isVoid(s, suit)).toList();
    if (holders.isEmpty() || holders.size() > 2) {
      return Optional.empty();
    }
    var mightyAction = special.stream().filter(a -> a.card().equals(mighty)).findFirst();
    boolean singleton = mightyAction.isPresent() && view.hand().stream()
        .filter(c -> c instanceof StandardCard s && s.suit() == mighty.suit()).count() == 1;
    boolean unsafeJoker = special.stream().anyMatch(a -> a.card() instanceof Joker)
        && !view.hand().contains(Rules.gun(trump))
        && !facts.cards().publicHistory().cards().contains(Rules.gun(trump));
    boolean behind = holders.stream().anyMatch(facts.remainingSeats()::contains);
    // AK 때는 여러 야당이 따랐는데 이후 줄어든 경우, 남은 상위를 아직 못 뽑았을 수 있습니다.
    long crowdedHonors = runs.stream().filter(t -> t.cards().get(0).action().card().points() > 0)
        .filter(t -> t.cards().stream().filter(p -> !facts.teams().isAlly(p.seat())
            && p.action().card() instanceof StandardCard c && c.suit() == suit).count()
            > holders.size()).count();
    boolean extraction = behind && crowdedHonors >= 2
        && facts.cards().trumps().outside().stream().anyMatch(c -> c.points() > 0);
    // 현재 주공이 이기고 있으면 보존 예외가 아니라 기존 동료 보호·호출 판단에 맡깁니다.
    // 뒤의 미소모 상위를 빼려는 공개 근거가 있는 경우만 별도로 응답합니다.
    if (facts.allyWinning() && !extraction) {
      return Optional.empty();
    }
    if (singleton || unsafeJoker || facts.trickPoints() >= 3
        || ScoreSupport.lossWouldDefeatContract(view, facts) || extraction) {
      return Optional.of(new PlayDecision(mightyAction.orElse(special.get(0)),
          "기루 호출: 받침·콜 위험·실린 점수 또는 뒤의 미소모 상위 때문에 응답합니다."));
    }
    var winner = facts.currentWinner().orElseThrow().action().card();
    if (!(winner instanceof StandardCard current) || current.suit() != suit
        || current.points() != 0 || !facts.cards().trumps().outside().stream()
        .anyMatch(c -> c.points() > 0 && c.rank().value() > current.rank().value())) {
      return Optional.empty();
    }
    var water = PlaySupport.ordinary(facts.passingActions(legal), view).stream()
        .filter(a -> a.card().points() == 0).toList();
    if (water.isEmpty()) {
      return Optional.empty();
    }
    // 마이티를 숨기면서 마지막 받침을 버려 다음 마공에 강제로 나오게 만들지 않습니다.
    var covered = water.stream().filter(a -> mightyAction.isEmpty()
        || ((StandardCard) a.card()).suit() != mighty.suit()
        || view.hand().stream().filter(c -> c instanceof StandardCard s
        && s.suit() == mighty.suit()).count() > 2).toList();
    if (covered.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new PlayDecision(PlaySupport.cheapest(covered, view),
        "집중된 기루의 물패 승리는 넘기고 특수카드를 남은 점수 기루에 보존합니다."));
  }
}
