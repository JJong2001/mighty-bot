package mighty.ai.play;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import mighty.game.Seat;
import mighty.game.deal.DeckFactory;
import mighty.game.trick.PublicTrick;
import mighty.model.Card;
import mighty.game.action.Action;
import mighty.game.friend.CardFriend;
import mighty.game.friend.NoFriend;
import mighty.game.friend.SpecialFriend;
import mighty.game.observation.DeclarerInfo;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.StandardCard;
import mighty.model.Trump;

/**
 * 공개된 기루 반환을 프렌드의 행동 신호로 읽습니다. 공식 공개 상태는 변경하지 않습니다.
 */
final class FriendSignals {

  private FriendSignals() {
  }

  static Optional<Seat> returningFriend(View view) {
    if (view.contract().trump() == Trump.NO_TRUMP || view.context()
        .friendStatus() instanceof NoFriend
        || view.friendSelection() == SpecialFriend.FIRST_TRICK) {
      return Optional.empty();
    }
    if (view.friendSelection() instanceof CardFriend friend
        && view.privateInfo() instanceof DeclarerInfo info
        && (info.hand().contains(friend.card()) || info.discardedCards().contains(friend.card()))) {
      return Optional.empty();
    }
    Set<Seat> signals = new HashSet<>();
    for (var trick : view.context().history()) {
      if (!trick.leader().equals(view.context().declarer())
          && trumpLead(view, trick.cards().get(0).action()) && !completedStrip(view, trick)) {
        signals.add(trick.leader());
      }
    }
    if (!view.trick().isEmpty()) {
      var lead = view.trick().get(0);
      if (!lead.seat().equals(view.context().declarer()) && trumpLead(view, lead.action())
          && !currentStrip(view)) {
        signals.add(lead.seat());
      }
    }
    // 과거의 잘못된 복수 야당 리드를 서로 다른 두 프렌드로 확정하지 않습니다.
    return signals.size() == 1 ? signals.stream().findFirst() : Optional.empty();
  }

  private static boolean currentStrip(View view) {
    if (view.trickNumber().value() <= 3) {
      return false;
    }
    var lead = view.trick().get(0);
    var declarer = view.trick().stream()
        .filter(play -> play.seat().equals(view.context().declarer())).findFirst();
    if (declarer.isEmpty() || !(declarer.get().action().card() instanceof StandardCard card)
        || !view.contract().trump().matches(card.suit())) {
      return false;
    }
    var winner = Rules.currentWinner(view.contract().trump(), view.trickNumber(), view.trick());
    if (winner.isEmpty() || !winner.get().seat().equals(lead.seat())) {
      return false;
    }
    Set<Card> spent = new HashSet<>(view.publicCardHistory().cards());
    if (view.privateInfo() instanceof DeclarerInfo info) {
      spent.addAll(info.discardedCards());
    }
    return DeckFactory.create().stream().filter(c -> c instanceof StandardCard standard
        && view.contract().trump().matches(standard.suit())).allMatch(spent::contains);
  }

  private static boolean completedStrip(View view, PublicTrick event) {
    if (event.number().value() <= 3 || !event.winner().equals(event.leader())) {
      return false;
    }
    boolean declarerFollowed = event.cards().stream().anyMatch(play ->
        play.seat().equals(view.context().declarer()) && play.action()
            .card() instanceof StandardCard card
            && view.contract().trump().matches(card.suit()));
    if (!declarerFollowed) {
      return false;
    }
    Set<Card> spent = new HashSet<>(view.publicCardHistory().completedCardsThrough(event.number()));
    if (view.privateInfo() instanceof DeclarerInfo info) {
      spent.addAll(info.discardedCards());
    }
    return DeckFactory.create().stream().filter(card -> card instanceof StandardCard standard
        && view.contract().trump().matches(standard.suit())).allMatch(spent::contains);
  }

  private static boolean trumpLead(View view, Action action) {
    return view.contract().trump().matches(Rules.conditionsFor(view.contract().trump(),
        view.trickNumber(), action).leadSuit());
  }

}
