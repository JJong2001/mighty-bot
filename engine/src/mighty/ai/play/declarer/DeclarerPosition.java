package mighty.ai.play.declarer;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import mighty.ai.play.PlayFacts;
import mighty.ai.play.PlaySupport;
import mighty.game.action.Action;
import mighty.game.action.StandardPlay;
import mighty.game.friend.CardFriend;
import mighty.game.friend.UnrevealedFriend;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.Card;
import mighty.model.StandardCard;
import mighty.model.Suit;

/**
 * 주공의 자기 패·자기 버리기·공개 진행으로만 만드는 이번 차례의 운영 자료입니다.
 */
record DeclarerPosition(View view, PlayFacts facts, List<Action> legal) {

  DeclarerPosition {
    legal = List.copyOf(legal);
  }

  /**
   * 직전 선을 간으로 가져왔는지 확인해 같은 기루를 바로 상위에 내주지 않도록 합니다.
   */
  boolean justRuffed() {
    if (view.context().history().isEmpty()) {
      return false;
    }
    var last = view.context().history().get(view.context().history().size() - 1);
    if (!last.winner().equals(view.self()) || view.contract().trump().matches(
        Rules.conditionsFor(view.contract().trump(), last.number(), last.cards().get(0).action())
            .leadSuit())) {
      return false;
    }
    return last.cards().stream().anyMatch(p -> p.seat().equals(view.self())
        && p.action().card() instanceof StandardCard c && view.contract().trump()
        .matches(c.suit()));
  }

  List<Action> ordinary() {
    return PlaySupport.ordinary(legal, view).stream()
        .filter(action -> !((StandardPlay) action).call()).toList();
  }

  List<Action> inSuit(Suit suit) {
    return ordinary().stream().filter(action -> ((StandardCard) action.card()).suit() == suit)
        .toList();
  }

  List<Action> trumps() {
    return ordinary().stream().filter(action -> view.contract().trump()
        .matches(((StandardCard) action.card()).suit())).toList();
  }

  List<Action> sides() {
    return ordinary().stream().filter(action -> !view.contract().trump()
        .matches(((StandardCard) action.card()).suit())).toList();
  }

  /**
   * 주력 추정에는 자기의 이미 제출한 카드도 포함해 긴 문양이 소진됐다고 다른 문양으로 바꾸지 않습니다.
   */
  Suit primarySuit() {
    return Arrays.stream(Suit.values()).max(Comparator
        .<Suit>comparingLong(suit -> initialLength(suit))
        .thenComparingInt(suit -> -suit.ordinal())).orElseThrow();
  }

  long initialLength(Suit suit) {
    long held = length(suit);
    long used = facts.cards().publicHistory().completedPlays().stream()
        .filter(play -> play.seat().equals(view.self()))
        .filter(play -> play.action().card() instanceof StandardCard card && card.suit() == suit)
        .count();
    return held + used;
  }

  long length(Suit suit) {
    return view.hand().stream().filter(card -> card instanceof StandardCard standard
        && standard.suit() == suit).count();
  }

  /**
   * 아직 공개되지 않았고 자기 손·버리기·공개 카드에도 없는 지정 카드만 호출 대상으로 봅니다.
   */
  boolean needsFriendCall() {
    return view.context().friendStatus() instanceof UnrevealedFriend
        && view.friendSelection() instanceof CardFriend friend
        && facts.cards().outsideCards().contains(friend.card());
  }

  boolean calls(Card card) {
    return needsFriendCall() && ((CardFriend) view.friendSelection()).card().equals(card);
  }

  /**
   * 외부 기루를 야당 기루로 단정하지 않고, 야당일 수 있는 자리의 공개 무기루를 검사합니다.
   */
  boolean enemiesMayHaveTrump() {
    return facts.cards().trumps().enemiesMayHold(facts);
  }

  /**
   * 두 차례 이상 기루를 따라 소모했는데도 자기 최고 기루보다 높은 외부 기루가 남는 상태입니다.
   */
  boolean trumpControlWeakened() {
    if (trumps().isEmpty() || !enemiesMayHaveTrump()) {
      return false;
    }
    long rounds = view.context().history().stream().filter(trick -> view.contract().trump().matches(
        Rules.conditionsFor(view.contract().trump(), trick.number(), trick.cards().get(0).action())
            .leadSuit())).count();
    int ownTop = trumps().stream().mapToInt(action -> ((StandardCard) action.card()).rank().value())
        .max().orElseThrow();
    return rounds >= 2 && facts.cards().outsideCards().stream()
        .anyMatch(card -> card instanceof StandardCard standard
            && view.contract().trump().matches(standard.suit())
            && standard.rank().value() > ownTop);
  }

  /**
   * 물패를 내도 된다는 확정이 아니라, 지원 요청·문양 전환에 쓸 낮은 사이드 후보입니다.
   */
  Action lowSide(List<Action> candidates) {
    return candidates.stream().min(Comparator.<Action>comparingInt(action -> action.card().points())
        .thenComparingLong(action -> length(((StandardCard) action.card()).suit()))
        .thenComparingInt(action -> ((StandardCard) action.card()).rank().value())
        .thenComparing(action -> action.card().toCode())).orElseThrow();
  }

}
