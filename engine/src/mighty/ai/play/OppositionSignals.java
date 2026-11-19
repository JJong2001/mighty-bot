package mighty.ai.play;

import java.util.*;
import mighty.game.Seat;
import mighty.game.action.StandardPlay;
import mighty.game.friend.SpecialFriend;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.game.trick.PublicPlay;
import mighty.model.*;

/**
 * 주공의 확실한 초구를 간으로 공격한 자리를 야당 운영 신호로 읽습니다. 공식 역할은 바꾸지 않습니다.
 */
final class OppositionSignals {

  private OppositionSignals() {
  }

  static Set<Seat> openingAttackers(View view) {
    if (view.friendSelection() == SpecialFriend.FIRST_TRICK) {
      return Set.of();
    }
    List<PublicPlay> first = view.context().history().isEmpty() ? view.trick()
        : view.context().history().get(0).cards();
    if (first.isEmpty() || !first.get(0).seat().equals(view.context().declarer())
        || !(first.get(0).action() instanceof StandardPlay lead)) {
      return Set.of();
    }
    var mighty = Rules.mighty(view.contract().trump());
    boolean top = Arrays.stream(Rank.values())
        .filter(rank -> rank.value() > lead.card().rank().value())
        .map(rank -> new StandardCard(lead.card().suit(), rank)).allMatch(mighty::equals);
    if (!top || lead.card().equals(mighty)) {
      return Set.of();
    }
    Set<Seat> attackers = new HashSet<>();
    for (var p : first.subList(1, first.size())) {
      if (p.action().card() instanceof StandardCard c && !c.equals(mighty)
          && c.suit() != lead.card().suit() && view.contract().trump().matches(c.suit())) {
        attackers.add(p.seat());
      }
    }
    return Set.copyOf(attackers);
  }

}
