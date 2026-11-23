package mighty.ai.play.friend;

import java.util.List;
import java.util.Optional;
import mighty.ai.play.*;
import mighty.game.action.Action;
import mighty.game.friend.CardFriend;
import mighty.game.friend.SpecialFriend;
import mighty.game.trick.PublicTrick;
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
    var history = view.context().history();
    boolean onReveal = !history.isEmpty()
        && revealedByWinning(view, history.get(history.size() - 1));
    var possibleRuffers = view.context().playOrder().seats().stream()
        .filter(seat -> !facts.teams().isAlly(seat))
        .filter(seat -> facts.cards().trumps().outside().stream()
            .anyMatch(trump -> !facts.cards().isVoid(seat, trump.suit())))
        .toList();
    var tops = ordinary.stream().filter(a -> facts.isSuitTop(a.card()))
        .filter(a -> !view.contract().trump().matches(((StandardCard) a.card()).suit()))
        .filter(a -> {
          Suit suit = ((StandardCard) a.card()).suit();
          if (!preservesSpecialCards(suit, view, facts)) {
            return false;
          }
          if (noEnemyRuff) {
            return FriendReturnSafety.allows(suit, view, facts);
          }
          if (declarerTurnedToSide(suit, view)) {
            return true;
          }
          if (!FriendReturnSafety.allows(suit, view, facts)
              || possibleRuffers.stream().anyMatch(seat -> facts.cards().isVoid(seat, suit))) {
            return false;
          }
          // 공개 직후에는 간칠 수 있는 자리들이 따를 장수가 남아 있을 때 한 구 먼저 돕습니다.
          return trumpRounds >= 2 || onReveal && facts.cards().outsideCount(suit)
              >= possibleRuffers.size();
        }).toList();
    if (tops.isEmpty()) {
      return Optional.empty();
    }
    String reason = noEnemyRuff
        ? "간 위험이 없는 노기루·기루 정리 이후에는 일반 탑카를 먼저 사용합니다."
        : trumpRounds < 2 && onReveal
            ? "공개 직후 간 위험이 낮은 사이드 탑으로 주공에게 한 구 더 지원합니다."
            : "기루가 여러 번 돈 뒤 사이드 탑카로 점수와 선을 확보합니다.";
    return Optional.of(new PlayDecision(PlaySupport.highest(tops), reason));
  }

  static boolean revealedByWinning(View view, PublicTrick trick) {
    if (!trick.winner().equals(view.self())) {
      return false;
    }
    return view.friendSelection() == SpecialFriend.FIRST_TRICK && trick.number().isFirst()
        || view.friendSelection() instanceof CardFriend friend
        && trick.cards().stream().anyMatch(play -> play.seat().equals(view.self())
            && play.action().card().equals(friend.card()));
  }

  static boolean preservesSpecialCards(Suit suit, View view, PlayFacts facts) {
    var mighty = Rules.mighty(view.contract().trump());
    return (suit != mighty.suit() || !facts.cards().outsideCards().contains(mighty))
        && !risksSpendingGun(suit, view, facts);
  }

  private static boolean risksSpendingGun(Suit suit, View view, PlayFacts facts) {
    var trump = view.contract().trump();
    var gun = Rules.gun(trump);
    // 콜 카드가 밖에 있으면 주공이 보유할 수 있으므로, 선택적인 탑 선으로 소모를 유도하지 않습니다.
    return trump != Trump.NO_TRUMP && suit == gun.suit()
        && facts.cards().outsideCards().contains(gun)
        && !facts.cards().publicHistory().hasBeenPlayed(Joker.INSTANCE);
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
