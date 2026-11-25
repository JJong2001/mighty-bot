package mighty.ai.preparation;

import java.util.*;
import mighty.ai.bidding.BiddingHand;
import mighty.ai.planning.HighNoTrumpPlan;
import mighty.game.*;
import mighty.game.bidding.*;
import mighty.game.observation.*;
import mighty.model.*;

/**
 * 확정한 준비 계획을 실제 판과 가상 대국에서 같은 공약 공개 순서로 변환합니다.
 */
public final class PreparationDeclarations {

  private PreparationDeclarations() {
  }

  static Optional<HighNoTrumpPlan> highRoute(BiddingHand hand,
      Bid awarded) {
    if (awarded.points() != 16 && awarded.points() != 17) {
      return Optional.empty();
    }
    return HighNoTrumpPlan.find(hand)
        .filter(plan -> awarded.trump() == Trump.NO_TRUMP
            || (awarded.points() == 16 && awarded.trump() == plan.suited()
            && plan.hasTwoSources(hand)));
  }

  public static List<Bid> discardDeclarations(DiscardView view, Bid awarded, List<Bid> history,
      PreparationPlan plan) {
    if (plan.contract().equals(new Bid(20, Trump.NO_TRUMP))
        && (awarded.points() == 16 || awarded.points() == 17)) {
      var kept = new ArrayList<>(view.hand());
      kept.removeAll(plan.discarded());
      var hand = BiddingHand.from(kept);
      if (highRoute(hand, awarded).filter(route -> route.canRun(hand)).isPresent()) {
        var exposed = new FriendView(view.self(), view.contract(), kept, plan.discarded(), history,
            view.auction());
        return declarationSequence(exposed, awarded, history, plan.contract());
      }
    }
    return plan.contract().equals(view.contract()) ? List.of() : List.of(plan.contract());
  }

  static List<Bid> declarationSequence(FriendView view, Bid awarded, List<Bid> history,
      Bid selected) {
    if (selected.points() == Bid.MAX_POINTS
        && selected.trump() == Trump.NO_TRUMP) {
      BiddingHand hand = BiddingHand.from(view.hand());
      var route = highRoute(hand, awarded).filter(plan -> plan.canRun(hand));
      if (route.isPresent()) {
        var signals = new ArrayList<Bid>();
        if (hand.holding(Suit.SPADE).size() >= 5) {
          signals.add(new Bid(20, Trump.SPADE));
        }
        signals.add(new Bid(20, route.get().suited()));
        signals.add(selected);
        if (history.size() >= signals.size() && history.subList(history.size() - signals.size(),
            history.size()).equals(signals)) {
          return List.of();
        }
        return List.copyOf(signals);
      }
      var selection = new RuleFriendPolicy().choose(new FriendView(
          view.self(), selected, view.hand(), view.discardedCards(), history));
      var opening = mighty.ai.planning.NoTrumpOpeningPlanner.plan(hand, view.discardedCards(),
          selection, selected.points(), history);
      Suit primary = opening.returnSuit().orElseGet(
          () -> mighty.ai.planning.NoTrumpPlanner.signallingSuit(hand));
      Trump signal = Arrays.stream(Trump.values()).filter(t -> t.matches(primary)).findFirst()
          .orElseThrow();
      Bid signalBid = new Bid(Bid.MAX_POINTS, signal);
      if (!PublicContractHistory.latestSuit(history).filter(primary::equals).isPresent()
          && ContractChangeRules.canChange(awarded, view.contract(), signalBid, GamePhase.FRIEND)
          && ContractChangeRules.canChange(awarded, signalBid, selected, GamePhase.FRIEND)) {
        return List.of(signalBid, selected);
      }
    }
    return selected.equals(view.contract()) ? List.of() : List.of(selected);
  }

}
