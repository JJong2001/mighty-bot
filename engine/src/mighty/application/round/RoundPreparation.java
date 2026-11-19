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

  public <E extends Exception> void discard(
      RoundState round,
      Submission<Bid, E> revise,
      Submission<List<Card>, E> submit
  ) throws E {
    var actor = round.currentSeat().orElseThrow();
    var history = round.contractHistory();
    var plan = planner.choose(round.discardView(actor), history.get(0), history);
    if (!plan.contract().equals(round.contract())) {
      revise.apply(plan.contract());
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
