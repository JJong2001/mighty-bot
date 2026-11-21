package mighty.application.round;

import java.util.List;
import mighty.ai.preparation.FriendPolicy;
import mighty.ai.preparation.PreparationPlanner;
import mighty.game.bidding.Bid;
import mighty.game.friend.FriendSelection;
import mighty.game.round.RoundState;
import mighty.model.Card;

/**
 * 공약 변경을 적용한 뒤 해당 준비 단계의 행동을 제출합니다.
 */
public final class RoundPreparation {

  private final PreparationPlanner planner;
  private final FriendPolicy friendPolicy;

  public RoundPreparation(PreparationPlanner planner, FriendPolicy friendPolicy) {
    this.planner = planner;
    this.friendPolicy = friendPolicy;
  }

  public <E extends Exception> void beforeKitty(
      RoundState round,
      Submission<Bid, E> revise
  ) throws E {
    var actor = round.currentSeat().orElseThrow();
    var history = round.contractHistory();
    var hand = round.beforeKittyHand(actor);
    Bid selected = PreparationPlanner.beforePublicBid(mighty.ai.bidding.BiddingHand.from(hand),
        history.get(0), round.contract(), actor, round.publicBids()).orElseGet(() ->
        planner.beforeKittyContract(hand, history.get(0), round.contract()));
    if (!selected.equals(round.contract())) {
      revise.apply(selected);
    }
  }

  public <E extends Exception> void discard(
      RoundState round,
      Submission<Bid, E> revise,
      Submission<List<Card>, E> submit
  ) throws E {
    var actor = round.currentSeat().orElseThrow();
    var history = round.contractHistory();
    var view = round.discardView(actor);
    var plan = planner.choose(view, history.get(0), history);
    for (var bid : PreparationPlanner.discardDeclarations(view, history.get(0), history, plan)) {
      revise.apply(bid);
    }
    submit.apply(plan.discarded());
  }

  public <E extends Exception> void chooseFriend(
      RoundState round,
      Submission<Bid, E> revise,
      Submission<FriendSelection, E> submit
  ) throws E {
    var actor = round.currentSeat().orElseThrow();
    var history = round.contractHistory();
    for (var bid : planner.declarations(round.friendView(actor), history.get(0), history)) {
      revise.apply(bid);
    }
    // 최종 공약을 적용한 뒤의 관측으로 프렌드를 선택합니다.
    submit.apply(friendPolicy.choose(round.friendView(actor)));
  }

  @FunctionalInterface
  public interface Submission<T, E extends Exception> {

    void apply(T value) throws E;
  }
}
