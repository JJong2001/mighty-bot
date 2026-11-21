package mighty.ai.play.declarer;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import mighty.ai.bidding.BiddingHand;
import mighty.ai.planning.HighNoTrumpPlan;
import mighty.ai.play.PlayDecision;
import mighty.ai.play.PlaySupport;
import mighty.game.action.Action;
import mighty.game.action.JokerLead;
import mighty.game.friend.CardFriend;
import mighty.game.rule.Rules;
import mighty.model.*;

/** 합의한 노기루 운영에서 공개된 소모를 보고 다음 선을 고릅니다. */
final class HighNoTrumpPlay {

  private HighNoTrumpPlay() {
  }

  private static Optional<HighNoTrumpPlan> route(DeclarerPosition position) {
    var bid = position.view().contract();
    if (bid.trump() != Trump.NO_TRUMP || (bid.points() != 16 && bid.points() != 17
        && bid.points() != 20)) {
      return Optional.empty();
    }
    var hand = BiddingHand.from(NoTrumpOperations.initialCards(position));
    return HighNoTrumpPlan.find(hand).filter(p -> bid.points() != 20 || p.canRun(hand));
  }

  static Optional<PlayDecision> lead(DeclarerPosition position) {
    var route = route(position);
    var view = position.view();
    if (route.isEmpty() || !(view.friendSelection() instanceof CardFriend friend)) {
      return Optional.empty();
    }
    var plan = route.get();
    var main = position.inSuit(plan.primary());
    boolean first = view.trickNumber().isFirst();
    if (position.needsFriendCall()) {
      if (friend.card() instanceof Joker && plan.tops() == 2) {
        if (first) {
          return playCard(position.legal(), new StandardCard(Suit.SPADE, Rank.KING),
              "별도 초구로 선을 잡고 주력의 낮은 패로 조커를 찾습니다.");
        }
        return low(main, "상위 두 장만으로 주력을 정리하기 어려워 조커 지원을 먼저 받습니다.");
      }
      if (friend.card() instanceof StandardCard called && !called.equals(Rules.mighty(Trump.NO_TRUMP))
          && (view.contract().points() == 20 || called.suit() != Suit.SPADE)) {
        return low(position.inSuit(called.suit()), "지정 문양의 물패로 프렌드를 찾아 반환받습니다.");
      }
    }
    // 스페이드 반환을 K로 회수한 직후에는 외부 상위가 남을 때만 마이티로 한 번 더 뽑습니다.
    if (!first && view.contract().points() == 20 && position.initialLength(Suit.SPADE) >= 5) {
      var last = view.context().history().get(view.context().history().size() - 1);
      if (Rules.conditionsFor(Trump.NO_TRUMP, last.number(), last.cards().get(0).action())
          .leadSuit() == Suit.SPADE && last.winner().equals(view.self())) {
        var spades = position.inSuit(Suit.SPADE);
        if (!spades.isEmpty()) {
          var high = PlaySupport.highest(spades);
          if (position.facts().isSuitTop(high.card())) {
            return Optional.of(new PlayDecision(high, "승격된 스페이드를 먼저 처리합니다."));
          }
          var mighty = playCard(position.legal(), Rules.mighty(Trump.NO_TRUMP),
              "반환 문양의 외부 상위를 마이티로 소모시킵니다.");
          if (mighty.isPresent()) {
            return mighty;
          }
        }
      }
    }
    if (!main.isEmpty()) {
      var high = PlaySupport.highest(main);
      if (position.facts().isSuitTop(high.card())) {
        return Optional.of(new PlayDecision(high, "주력의 남은 탑을 위에서부터 소진합니다."));
      }
      if (!first && plan.joker()) {
        return position.legal().stream().filter(a -> a instanceof JokerLead lead
            && lead.declaredSuit() == plan.primary()).findFirst()
            .map(a -> new PlayDecision(a, "주력의 상위가 남아 조커로 한 번 더 뽑습니다."));
      }
    }
    if (position.needsFriendCall() && friend.card() instanceof StandardCard called
        && called.suit() == Suit.SPADE && called.rank() != Rank.ACE) {
      return low(position.inSuit(Suit.SPADE), "주력을 처리한 뒤 낮은 스페이드로 프렌드를 찾습니다.");
    }
    return Optional.empty();
  }

  static Optional<PlayDecision> follow(DeclarerPosition position) {
    var view = position.view();
    var facts = position.facts();
    var route = route(position);
    if (!facts.teams().isAlly(view.trick().get(0).seat()) || route.isEmpty()) {
      return Optional.empty();
    }
    if (facts.conditions().leadSuit() == Suit.SPADE) {
      return playCard(facts.winningActions(), new StandardCard(Suit.SPADE, Rank.KING),
          "프렌드의 스페이드 반환을 K로 회수해 마이티를 남깁니다.");
    }
    if (facts.conditions().leadSuit() == route.get().primary()
        && (view.trick().get(0).action().card().points() == 0 || view.contract().points() == 20)) {
      var winners = facts.winningActions().stream().filter(position.legal()::contains)
          .filter(a -> a.card() instanceof StandardCard c && c.suit() == facts.conditions().leadSuit())
          .filter(a -> facts.isSuitTop(a.card())).toList();
      if (!winners.isEmpty()) {
        return Optional.of(new PlayDecision(PlaySupport.highest(winners), "반환받은 주력 탑으로 선을 회수합니다."));
      }
    }
    if (view.friendSelection() instanceof CardFriend friend
        && friend.card() instanceof StandardCard called && called.rank() == Rank.ACE
        && called.suit() != Suit.SPADE && facts.allyWinning()
        && facts.isSuitTop(facts.currentWinner().orElseThrow().action().card())
        && view.hand().contains(Rules.mighty(Trump.NO_TRUMP)) && view.hand().contains(Joker.INSTANCE)
        && position.inSuit(facts.conditions().leadSuit()).isEmpty()) {
      return low(position.inSuit(Suit.SPADE), "프렌드의 승리 카드에 물스페이드를 버려 반환을 요청합니다.");
    }
    return Optional.empty();
  }

  private static Optional<PlayDecision> low(List<Action> actions, String reason) {
    return actions.stream().filter(a -> a.card().points() == 0)
        .min(Comparator.comparingInt(a -> ((StandardCard) a.card()).rank().value()))
        .map(a -> new PlayDecision(a, reason));
  }

  private static Optional<PlayDecision> playCard(List<Action> legal, Card card, String reason) {
    return legal.stream().filter(a -> a.card().equals(card)).findFirst()
        .map(a -> new PlayDecision(a, reason));
  }
}
