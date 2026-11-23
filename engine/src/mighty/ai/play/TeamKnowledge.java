package mighty.ai.play;

import java.util.Optional;
import java.util.stream.Stream;
import mighty.game.Role;
import mighty.game.Seat;
import mighty.game.friend.NoFriend;
import mighty.game.friend.CardFriend;
import mighty.game.friend.RevealedFriend;
import mighty.game.observation.View;

/**
 * 자기 역할과 공개 프렌드만으로 확실한 동료를 판정합니다.
 */
public final class TeamKnowledge {

  private final View view;
  private final Optional<Seat> submittedFriend;
  private final PublicBehaviorInference behavior;

  public TeamKnowledge(View view) {
    this(view, new PublicBehaviorInference(view));
  }

  TeamKnowledge(View view, PublicBehaviorInference behavior) {
    this.behavior = behavior;
    this.view = java.util.Objects.requireNonNull(view);
    // 현재 구에 지정 카드가 나왔다면 별도 공개 알림을 기다릴 필요가 없습니다.
    submittedFriend = view.friendSelection() instanceof CardFriend friend
        ? Stream.concat(view.context().history().stream().flatMap(trick -> trick.cards().stream()),
            view.trick().stream())
        .filter(play -> !play.seat().equals(view.context().declarer()))
        .filter(play -> play.action().card().equals(friend.card()))
        .map(play -> play.seat()).findFirst()
        : Optional.empty();
  }

  /**
   * 공식 공개를 우선하며, 공개 전에는 초반 기루 반환 신호를 전략에 반영합니다.
   */
  public Optional<Seat> friendSeat() {
    if (view.context().friendStatus() instanceof RevealedFriend friend) {
      return Optional.of(friend.seat());
    }
    if (view.context().friendStatus() instanceof NoFriend) {
      return Optional.empty();
    }
    if (view.role() == Role.FRIEND) {
      return Optional.of(view.self());
    }
    if (submittedFriend.isPresent()) {
      return submittedFriend;
    }
    return FriendSignals.returningFriend(view)
        .filter(seat -> !OppositionSignals.openingAttackers(view).contains(seat))
        .filter(seat ->
            view.role() != Role.OPPOSITION || !seat.equals(view.self()));
  }

  /**
   * 공개 증거의 추정은 공식 소속·정산에 쓰는 isAlly와 분리합니다.
   */
  public boolean isLikelyAlly(Seat seat) {
    if (isAlly(seat)) {
      return true;
    }
    if (view.context().friendStatus() instanceof NoFriend || friendSeat().isPresent()) {
      return false;
    }
    if (view.role() == Role.DECLARER) {
      return behavior.likelyFriends().contains(seat);
    }
    return view.role() == Role.OPPOSITION && !behavior.likelyFriends().isEmpty()
        && !seat.equals(view.context().declarer()) && !behavior.likelyFriends().contains(seat);
  }

  public boolean isAlly(Seat seat) {
    if (view.self().equals(seat)) {
      return true;
    }
    if (view.role() == Role.FRIEND) {
      return view.context().declarer().equals(seat);
    }
    if (view.role() == Role.DECLARER) {
      return friendSeat().filter(seat::equals).isPresent();
    }
    if (view.role() == Role.OPPOSITION) {
      if (view.context().friendStatus() instanceof NoFriend) {
        return !view.context().declarer().equals(seat);
      }
      var friend = friendSeat();
      if (friend.isPresent()) {
        return !view.context().declarer().equals(seat) && !friend.get().equals(seat);
      }
      return OppositionSignals.openingAttackers(view).contains(seat);
    }
    return false;
  }

}
