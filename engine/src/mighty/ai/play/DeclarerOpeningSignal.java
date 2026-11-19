package mighty.ai.play;

import java.util.Optional;
import mighty.game.friend.CardFriend;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.Joker;
import mighty.model.Rank;
import mighty.model.StandardCard;
import mighty.model.Trump;

/**
 * 초구의 점수 리드를 보고 주공이 직접 운영하려는 문양인지 읽습니다.
 */
public final class DeclarerOpeningSignal {

  private DeclarerOpeningSignal() {
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

    if (friend.card() instanceof Joker) {
      return Optional.of(card);
    }
    if (view.contract().trump() == Trump.NO_TRUMP && friend.card() instanceof StandardCard target
        && target.rank() == Rank.ACE && target.suit() != card.suit()
        && !target.equals(Rules.mighty(Trump.NO_TRUMP))) {
      return Optional.of(card);
    }
    return Optional.empty();
  }
}
