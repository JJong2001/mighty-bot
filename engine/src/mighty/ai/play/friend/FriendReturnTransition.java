package mighty.ai.play.friend;

import java.util.*;
import mighty.ai.play.*;
import mighty.game.action.*;
import mighty.game.friend.CardFriend;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.*;

/**
 * 주력 반환의 목적이 달성된 뒤 공개 신호로 마이티를 쓸 문양을 찾습니다.
 */
final class FriendReturnTransition {

  private FriendReturnTransition() {
  }

  static Optional<PlayDecision> choose(View view, PlayFacts facts, List<Action> ordinary) {
    if (view.contract().trump() != Trump.NO_TRUMP
        || !(view.friendSelection() instanceof CardFriend friend)
        || !(friend.card() instanceof StandardCard called) || called.rank() != Rank.ACE
        || called.equals(Rules.mighty(Trump.NO_TRUMP))
        || !facts.cards().outsideCards().contains(Rules.mighty(Trump.NO_TRUMP))) {
      return Optional.empty();
    }
    var history = view.context().history();
    if (history.isEmpty()) {
      return Optional.empty();
    }
    var last = history.get(history.size() - 1);
    if (!last.leader().equals(view.self()) || !last.winner().equals(view.self())
        || !(last.cards().get(0).action() instanceof StandardPlay returned)
        || returned.card().suit() != called.suit()
        || returned.card().rank().value() >= Rank.KING.value()) {
      return Optional.empty();
    }
    long rounds = history.stream().filter(t -> facts.teams().isAlly(t.winner())
        && t.cards().get(0).action() instanceof StandardPlay lead
        && lead.card().suit() == called.suit()
        && t.cards().stream().anyMatch(p -> p.seat().equals(t.winner())
        && p.action().card() instanceof StandardCard c && c.suit() == called.suit()
        && !c.equals(Rules.mighty(Trump.NO_TRUMP)))).count();
    if (rounds < 3 || !facts.cards().publicHistory().cards().contains(called)) {
      return Optional.empty();
    }
    var possibleDefenders = view.context().playOrder().seats().stream()
        .filter(seat -> !facts.teams().isAlly(seat) && !facts.cards().isVoid(seat, called.suit()))
        .toList();
    var outside = facts.cards().outsideCards(called.suit());
    // Q가 긴 한 집에 남은 관측이라면 세 번의 승리만으로 정리가 끝났다고 보지 않습니다.
    if (possibleDefenders.size() == 1 && outside.size() >= 2
        && outside.stream().anyMatch(c -> c.rank().value() > returned.card().rank().value())) {
      return Optional.empty();
    }
    var declarer = view.context().declarer();
    Optional<Suit> discarded = Optional.empty();
    for (var trick : history) {
      Suit led = Rules.conditionsFor(view.contract().trump(), trick.number(),
          trick.cards().get(0).action()).leadSuit();
      for (var play : trick.cards()) {
        if (play.seat().equals(declarer) && play.action().card() instanceof StandardCard card
            && card.suit() != led && !card.equals(Rules.mighty(Trump.NO_TRUMP))) {
          discarded = Optional.of(card.suit());
        }
      }
    }
    Optional<Suit> target = discarded;
    if (target.isEmpty()) {
      var first = history.get(0).cards().get(0);
      if (first.seat().equals(declarer) && first.action() instanceof StandardPlay opening
          && !opening.call() && opening.card().points() == 0
          && opening.card().suit() != called.suit()
          && opening.card().suit() != Rules.mighty(Trump.NO_TRUMP).suit()) {
        target = Optional.of(opening.card().suit());
      }
    }
    if (target.isEmpty() || target.get() == called.suit()) {
      return Optional.empty();
    }
    Suit suit = target.get();
    // 삽 버림 관측과 주공이 마지막에 판단할 자리의 이점이 함께 있어야 합니다.
    if (suit == Rules.mighty(Trump.NO_TRUMP).suit()
        && (discarded.isEmpty() || !view.context().playOrder().nextAfter(declarer)
        .equals(view.self()))) {
      return Optional.empty();
    }
    var choices = ordinary.stream().filter(a -> ((StandardCard) a.card()).suit() == suit).toList();
    return choices.isEmpty() ? Optional.empty() : Optional.of(new PlayDecision(
        PlaySupport.highest(choices), "주력 반환으로 충분히 상위를 소모했으므로 주공의 초구·버림 문양으로 연결합니다."));
  }
}
