package mighty.ai.play.friend;

import java.util.List;
import java.util.Optional;
import mighty.ai.play.*;
import mighty.game.action.Action;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.*;

/**
 * 간 위험이 없으면 탑카를 선취하고, 주공의 사이드 전환에는 같은 문양으로 간을 유도합니다.
 */
final class FriendSideCash {

  private FriendSideCash() {
  }

  static Optional<PlayDecision> choose(View view, PlayFacts facts, List<Action> ordinary) {
    boolean noEnemyRuff = view.contract().trump() == Trump.NO_TRUMP
        || !JokerSuitPlan.enemiesMayHoldTrump(view, facts);
    long trumpRounds = view.context().history().stream().filter(trick ->
        view.contract().trump().matches(Rules.conditionsFor(view.contract().trump(),
            trick.number(), trick.cards().get(0).action()).leadSuit())).count();
    var tops = ordinary.stream().filter(a -> facts.isSuitTop(a.card()))
        .filter(a -> !view.contract().trump().matches(((StandardCard) a.card()).suit()))
        .filter(a -> {
          Suit suit = ((StandardCard) a.card()).suit();
          if (noEnemyRuff) {
            return FriendReturnSafety.allows(suit, view, facts);
          }
          // 미소모 마이티의 소유자를 가정해 주공의 마이티를 공격하지 않습니다.
          if (suit == Rules.mighty(view.contract().trump()).suit()
              && facts.cards().outsideCards().contains(Rules.mighty(view.contract().trump()))) {
            return false;
          }
          if (declarerTurnedToSide(suit, view)) {
            return true;
          }
          if (trumpRounds < 2 || !FriendReturnSafety.allows(suit, view, facts)) {
            return false;
          }
          return facts.cards().trumps().outside().stream().noneMatch(trump ->
              view.context().playOrder().seats().stream().anyMatch(seat ->
                  !facts.teams().isAlly(seat) && facts.cards().isVoid(seat, suit)
                      && !facts.cards().isVoid(seat, trump.suit())));
        }).toList();
    if (tops.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new PlayDecision(PlaySupport.highest(tops), noEnemyRuff
        ? "간 위험이 없는 노기루·기루 정리 이후에는 일반 탑카를 먼저 사용합니다."
        : "기루가 여러 번 돈 뒤 사이드 탑카로 점수와 선을 확보합니다."));
  }

  private static boolean declarerTurnedToSide(Suit suit, View view) {
    boolean trumpSeen = false;
    for (var trick : view.context().history()) {
      var lead = trick.cards().get(0);
      Suit led = Rules.conditionsFor(view.contract().trump(), trick.number(), lead.action())
          .leadSuit();
      if (view.contract().trump().matches(led)) {
        trumpSeen = true;
      } else if (trumpSeen && lead.seat().equals(view.context().declarer()) && led == suit
          && lead.action() instanceof mighty.game.action.StandardPlay play && !play.call()) {
        return true;
      }
    }
    return false;
  }

}
