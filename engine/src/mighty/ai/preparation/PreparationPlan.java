package mighty.ai.preparation;

import java.util.List;
import mighty.game.bidding.Bid;
import mighty.game.friend.FriendSelection;
import mighty.model.Card;

/**
 * 주공 자기 13장으로 비교한 공약·버리기·프렌드의 일관된 선택입니다.
 */
public record PreparationPlan(Bid contract, List<Card> discarded, FriendSelection friend,
                              String reason) {

  public PreparationPlan {
    discarded = List.copyOf(discarded);
  }

}
