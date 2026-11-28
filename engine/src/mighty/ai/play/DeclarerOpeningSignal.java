package mighty.ai.play;

import java.util.Optional;
import mighty.game.friend.CardFriend;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.Joker;
import mighty.model.Rank;
import mighty.model.StandardCard;

/**
 * 초구의 점수 리드를 보고 주공이 직접 운영하려는 문양인지 읽습니다.
 */
public final class DeclarerOpeningSignal {

  private DeclarerOpeningSignal() {
  }

  /** 호출자가 초구임을 확인합니다. 프렌드에게 그 문양의 승리를 부탁하지 않는 연결패 신호입니다. */
  static java.util.List<StandardCard> retainedHonors(View view, mighty.game.trick.PublicPlay lead) {
    if (!lead.seat().equals(view.context().declarer())
        || !(lead.action().card() instanceof StandardCard card)
        || card.equals(Rules.mighty(view.contract().trump()))
        || card.rank().value() < Rank.JACK.value() || !independentFriend(view, card)) {
      return java.util.List.of();
    }
    return java.util.Arrays.stream(Rank.values()).filter(rank -> rank.value() > card.rank().value())
        .map(rank -> new StandardCard(card.suit(), rank))
        .filter(higher -> !higher.equals(Rules.mighty(view.contract().trump()))).toList();
  }

  private static boolean independentFriend(View view, StandardCard lead) {
    if (!(view.friendSelection() instanceof CardFriend friend)) return false;
    return friend.card() instanceof Joker || friend.card() instanceof StandardCard target
        && target.rank() == Rank.ACE && target.suit() != lead.suit()
        && !target.equals(Rules.mighty(view.contract().trump()));
  }

  public static Optional<StandardCard> sidePointLead(View view) {
    if (!view.trickNumber().isFirst() || view.trick().isEmpty()
        || !(view.friendSelection() instanceof CardFriend friend)) {
      return Optional.empty();
    }

    return sidePointLead(view, view.trick().get(0));
  }

  /**
   * 호출자가 초구임을 확인한 공개 리드를 읽습니다.
   */
  static Optional<StandardCard> sidePointLead(View view, mighty.game.trick.PublicPlay lead) {
    if (!(view.friendSelection() instanceof CardFriend friend)) {
      return Optional.empty();
    }
    if (!lead.seat().equals(view.context().declarer())
        || !(lead.action().card() instanceof StandardCard card)
        || card.points() == 0 || view.contract().trump().matches(card.suit())
        || card.equals(Rules.mighty(view.contract().trump()))) {
      return Optional.empty();
    }

    return independentFriend(view, card) ? Optional.of(card) : Optional.empty();
  }
}
