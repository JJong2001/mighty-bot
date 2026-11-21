package mighty.ai.play;

import java.util.List;
import java.util.Optional;
import mighty.game.Role;
import mighty.game.Seat;
import mighty.game.action.Action;
import mighty.game.bidding.BidOffer;
import mighty.game.friend.CardFriend;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.game.trick.PublicPlay;
import mighty.model.Rank;
import mighty.model.StandardCard;
import mighty.model.Suit;

final class HonorGamble {

  private HonorGamble() {}

  static PlayDecision choose(View view, PlayFacts facts, List<Action> legal, PlayDecision usual,
      RulePlayPolicy policy) {
    var candidate = candidate(view, facts, legal);
    if (candidate.isEmpty()) {
      // 보험이나 K의 소재 근거가 없을 때 기존 일반 후수도 우연히 같은 도박을 택할 수 있습니다.
      if (openingMightyFriend(view) && isUncertainHonor(usual.action(), view, facts)) {
        return mightyResponse(view, legal);
      }
      return usual;
    }
    PlayDecision control = usual;
    if (openingMightyFriend(view)) {
      if (facts.winningActions().contains(usual.action())
          && facts.isSecured(new PublicPlay(view.self(), usual.action()))
          && !usual.action().card().equals(Rules.mighty(view.contract().trump()))) {
        return usual;
      }
      control = mightyResponse(view, legal);
    } else if (candidate.get().equals(usual.action())) {
      var alternatives = legal.stream().filter(a -> !a.equals(usual.action())).toList();
      if (alternatives.isEmpty()) {
        return usual;
      }
      var secure = alternatives.stream().filter(facts.winningActions()::contains)
          .filter(a -> facts.isSecured(new PublicPlay(view.self(), a))).toList();
      control = new PlayDecision(PlaySupport.cheapest(secure.isEmpty() ? alternatives : secure, view),
          "프렌드의 기루 반환에 안정적인 진행을 유지합니다.");
    }
    return HonorGambleTrial.compare(view, facts, control, candidate.get(), policy);
  }

  private static boolean openingMightyFriend(View view) {
    return view.role() == Role.FRIEND && view.trickNumber().isFirst()
        && (view.trick().size() == 2 || view.trick().size() == 3)
        && view.trick().get(0).action().card() instanceof StandardCard lead && lead.points() == 0
        && view.friendSelection() instanceof CardFriend friend
        && friend.card().equals(Rules.mighty(view.contract().trump())) && view.hand().contains(friend.card());
  }

  private static PlayDecision mightyResponse(View view, List<Action> legal) {
    var mighty = Rules.mighty(view.contract().trump());
    return new PlayDecision(legal.stream().filter(a -> a.card().equals(mighty)).findFirst()
        .orElseThrow(), "초구를 마이티로 받아 확실하게 선을 잡습니다.");
  }

  private static boolean isUncertainHonor(Action action, View view, PlayFacts facts) {
    return action.card() instanceof StandardCard card && card.suit() == facts.conditions().leadSuit()
        && (card.rank() == Rank.KING || card.rank() == Rank.QUEEN)
        && facts.winningActions().contains(action) && !facts.isSuitTop(card)
        && !facts.isSecured(new PublicPlay(view.self(), action));
  }

  static Optional<Action> candidate(View view, PlayFacts facts, List<Action> legal) {
    if (view.trick().isEmpty() || !(view.trick().get(0).action().card() instanceof StandardCard lead)) {
      return Optional.empty();
    }
    var suit = lead.suit();
    var mighty = Rules.mighty(view.contract().trump());
    int behind = facts.remainingSeats().size();
    if (view.role() == Role.FRIEND) {
      if (!openingMightyFriend(view)
          || legal.stream().noneMatch(a -> a.card().equals(mighty))
          || behind == 2 && view.trick().stream().skip(1)
              .noneMatch(p -> bidSuit(view, p.seat(), suit))) {
        return Optional.empty();
      }
    } else if (view.role() != Role.DECLARER || behind != 1 || view.context().history().isEmpty()
        || !facts.teams().isAlly(view.context().leader()) || !view.contract().trump().matches(suit)
        || !facts.cards().outsideCards().contains(new StandardCard(suit, Rank.ACE))) {
      return Optional.empty();
    }
    for (var rank : List.of(Rank.KING, Rank.QUEEN)) {
      var card = new StandardCard(suit, rank);
      var action = facts.winningActions().stream().filter(legal::contains)
          .filter(a -> a.card().equals(card)).findFirst();
      if (action.isEmpty() || facts.isSuitTop(card)
          || facts.isSecured(new PublicPlay(view.self(), action.get()))) {
        continue;
      }
      if (view.role() == Role.FRIEND) {
        // 시도가 실패해도 다음 스페이드 공격에 마이티 대신 낼 별도 보험을 남깁니다.
        boolean insured = view.hand().stream().anyMatch(c -> !c.equals(card)
            && c instanceof StandardCard s && s.suit() == Suit.SPADE
            && (s.rank() == Rank.KING || s.rank() == Rank.QUEEN));
        if (!insured || rank == Rank.QUEEN && (!bidSuit(view, view.context().declarer(), suit)
            || !facts.cards().outsideCards().contains(new StandardCard(suit, Rank.KING)))) {
          continue;
        }
      }
      return action;
    }
    return Optional.empty();
  }

  private static boolean bidSuit(View view, Seat seat, Suit suit) {
    return view.context().auction().stream().anyMatch(b -> b.seat().equals(seat)
        && b.action() instanceof BidOffer offer && offer.bid().points() >= 16
        && offer.bid().trump().matches(suit));
  }

  static boolean improvesHardContract(int samples, int usualWins, int gambleWins, int netGain) {
    // 원래 수로 절반 이상 성공하면 유지합니다. 몇 건의 우연한 차이로 위험을 늘리지 않습니다.
    return samples >= 16 && 2 * usualWins < samples && gambleWins >= usualWins + 2 && netGain > 0;
  }
}
