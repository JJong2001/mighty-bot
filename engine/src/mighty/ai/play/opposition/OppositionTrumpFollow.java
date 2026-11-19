package mighty.ai.play.opposition;

import java.util.*;
import mighty.ai.play.*;
import mighty.game.action.*;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.*;

/**
 * 야당 기루 후수: 다음 공격 보존과 지금 확보할 점수를 원래 순서로 비교합니다.
 */
final class OppositionTrumpFollow {

  static PlayDecision choose(View view, PlayFacts facts, List<Action> legal) {
    List<Action> trumps = PlaySupport.ordinary(legal, view).stream()
        .filter(a -> view.contract().trump().matches(((StandardCard) a.card()).suit())).toList();
    if (trumps.isEmpty()) {
      return FollowStrategy.choose(view, facts, legal);
    }
    List<Action> low = trumps.stream().filter(a -> a.card().points() == 0).toList();
    var winner = facts.currentWinner().orElseThrow();
    boolean specialOutside =
        facts.cards().outsideCards().contains(Rules.mighty(view.contract().trump()))
            || facts.cards().outsideCards().contains(Joker.INSTANCE);
    boolean pressureAlready = winner.action().card().points() > 0
        || winner.action().card().equals(Rules.mighty(view.contract().trump()))
        || winner.action().card() instanceof Joker;
    List<Action> winners = facts.winningActions().stream().filter(trumps::contains).toList();
    // 첫 순회의 적은 점수는 특수카드가 빠진 안전한 최고 기루로 나중에 크게 잡을 수 있습니다.
    boolean firstRound = view.context().history().stream()
        .noneMatch(t -> view.contract().trump().matches(
            Rules.conditionsFor(view.contract().trump(), t.number(), t.cards().get(0).action())
                .leadSuit()));
    int points = facts.trickPoints();
    var highest = trumps.stream().map(a -> (StandardCard) a.card())
        .max(Comparator.comparingInt(c -> c.rank().value())).orElseThrow();
    if (!facts.allyWinning() && firstRound && points <= 1 && !specialOutside && !low.isEmpty()
        && !winner.seat().equals(view.context().declarer())
        && facts.isSuitTop(highest) && new TrumpSurvival(view, facts).survives(highest)) {
      return new PlayDecision(PlaySupport.cheapest(low, view),
          "첫 기루 순회의 작은 점수에는 안전하게 남을 최고 기루를 숨겨 이후 점수 트릭을 노립니다.");
    }
    // 여당 뒤에서 J로도 앞설 수 있으면 A를 다음 공격에 남깁니다.
    if (!facts.allyWinning() && points < 2 && !specialOutside) {
      var cheaperPoint = winners.stream().filter(a -> a.card().points() > 0)
          .filter(a -> !a.card().equals(highest)).toList();
      if (!cheaperPoint.isEmpty() && facts.isSuitTop(highest)) {
        return new PlayDecision(PlaySupport.cheapest(cheaperPoint, view),
            "낮은 점수 기루로 압박하고 최고 기루는 다음 공격에 남깁니다.");
      }
    }
    if (points >= 2 && !facts.allyWinning() && !winners.isEmpty()) {
      return new PlayDecision(PlaySupport.cheapest(winners, view),
          "자기 점수까지 세 점 이상 확보할 기회이므로 장수 보존보다 실제 승리를 우선합니다.");
    }
    // 현재 여당을 확실히 잡을 수 있는 기회는 장수 보존 규칙보다 먼저 처리합니다.
    var secured = winners.stream().filter(a -> facts.isSecured(
        new mighty.game.trick.PublicPlay(view.self(), a))).toList();
    if (!facts.allyWinning() && !secured.isEmpty()) {
      return new PlayDecision(PlaySupport.cheapest(secured, view), "확보 가능한 승리를 보존 규칙으로 포기하지 않습니다.");
    }
    if (!low.isEmpty() && facts.allyWinning() && pressureAlready) {
      return new PlayDecision(PlaySupport.cheapest(low, view),
          "동료가 이미 점수 기루로 압박하므로 높은 수비를 겹치지 않습니다.");
    }
    // 장수가 짧은 야당 한 명이 압박합니다. A는 후속 실제 승리 자원이므로 제외합니다.
    if (!low.isEmpty() && !pressureAlready && specialOutside && trumps.size() <= 2) {
      var pressure = trumps.stream().filter(a -> a.card().points() > 0)
          .filter(a -> ((StandardCard) a.card()).rank() != Rank.ACE)
          .min(Comparator.comparingInt(a -> ((StandardCard) a.card()).rank().value()));
      if (pressure.isPresent()) {
        return new PlayDecision(pressure.get(),
            "짧은 점수 기루 한 장으로 특수카드 사용을 압박하고 다른 야당의 수비를 남깁니다.");
      }
    }
    // A/K 고정 분기 대신 Q/J도 같은 장수와 남은 위협 수로 수비 가치를 판단합니다.
    var survival = new TrumpSurvival(view, facts);
    if (!low.isEmpty() && survival.threatsAbove(highest) > 0 && survival.survives(highest)) {
      return new PlayDecision(PlaySupport.cheapest(low, view),
          "남은 상위 소모를 버틸 기루 장수이므로 높은 공격 수단을 보존합니다.");
    }
    if (!facts.allyWinning()) {
      if (!winners.isEmpty()) {
        return new PlayDecision(PlaySupport.cheapest(winners, view),
            "남은 카드에 대응하는 최소 기루로 잡고 더 높은 수비 카드를 남깁니다.");
      }
    }
    return new PlayDecision(PlaySupport.cheapest(trumps, view), "따라야 하는 기루 중 손실이 작은 카드부터 냅니다.");
  }

}
