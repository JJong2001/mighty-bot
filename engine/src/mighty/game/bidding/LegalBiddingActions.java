package mighty.game.bidding;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import mighty.model.Card;
import mighty.model.Trump;

public final class LegalBiddingActions {

  private LegalBiddingActions() {
  }

  public static List<BiddingAction> legalActions(
      BiddingState state,
      List<Card> hand,
      int playerScore
  ) {
    Objects.requireNonNull(state, "비딩 상태가 필요합니다.");
    List<Card> cards = List.copyOf(hand);
    List<BiddingAction> actions = new ArrayList<>(
        bidAndPassActions(state)
    );
    if (DealMissRules.canDeclare(state, cards, playerScore)) {
      actions.add(DealMiss.INSTANCE);
    }
    return List.copyOf(actions);
  }

  public static List<BiddingAction> bidAndPassActions(
      BiddingState state
  ) {
    Objects.requireNonNull(state, "비딩 상태가 필요합니다.");
    if (state.isFinished()) {
      return List.of();
    }
    List<BiddingAction> actions = new ArrayList<>();
    if (!state.history().isEmpty()) {
      actions.add(BidPass.INSTANCE);
    }
    var highest = state.highestBid();
    if (highest.isEmpty()) {
      addOffers(actions);
    } else {
      BidOffer currentOffer =
          (BidOffer) highest.orElseThrow().action();
      addHigherOffers(actions, currentOffer.bid());
    }
    return List.copyOf(actions);
  }

  private static void addOffers(List<BiddingAction> actions) {
    for (
        int points = Bid.MIN_POINTS;
        points <= Bid.MAX_POINTS;
        points++
    ) {
      for (Trump trump : Trump.values()) {
        actions.add(new BidOffer(new Bid(points, trump)));
      }
    }
  }

  private static void addHigherOffers(
      List<BiddingAction> actions,
      Bid current
  ) {
    for (
        int points = Bid.MIN_POINTS;
        points <= Bid.MAX_POINTS;
        points++
    ) {
      for (Trump trump : Trump.values()) {
        Bid offered = new Bid(points, trump);
        if (BiddingRules.isHigher(offered, current)) {
          actions.add(new BidOffer(offered));
        }
      }
    }
  }

}
