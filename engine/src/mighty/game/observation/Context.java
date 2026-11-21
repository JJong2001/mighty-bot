package mighty.game.observation;

import java.util.List;
import java.util.Objects;
import mighty.game.PlayOrder;
import mighty.game.Seat;
import mighty.game.bidding.Bid;
import mighty.game.bidding.PublicBid;
import mighty.game.friend.FriendStatus;
import mighty.game.friend.RevealedFriend;
import mighty.game.trick.PublicTrick;

/**
 * 플레이 단계에서 공개적으로 알려진 정보
 */
public record Context(
    PlayOrder playOrder,
    Seat declarer,
    FriendStatus friendStatus,
    List<PublicTrick> history,
    List<Bid> contractHistory,
    List<PublicBid> auction
) {

  public Context(PlayOrder playOrder, Seat declarer, FriendStatus friendStatus,
      List<PublicTrick> history, List<Bid> contractHistory) {
    this(playOrder, declarer, friendStatus, history, contractHistory, List.of());
  }

  public Context {
    auction = List.copyOf(auction);
    Objects.requireNonNull(playOrder, "플레이 순서가 필요합니다.");
    Objects.requireNonNull(declarer, "주공 자리가 필요합니다.");
    Objects.requireNonNull(friendStatus, "프렌드 공개 상태가 필요합니다.");
    history = List.copyOf(history);
    contractHistory = List.copyOf(contractHistory);
    if (!playOrder.seats().contains(declarer)) {
      throw new IllegalArgumentException(
          "주공은 이번 판의 플레이 참가자여야 합니다."
      );
    }
    if (friendStatus instanceof RevealedFriend revealed) {
      if (!playOrder.seats().contains(revealed.seat())) {
        throw new IllegalArgumentException(
            "프렌드는 이번 판의 플레이 참가자여야 합니다."
        );
      }
      if (revealed.seat().equals(declarer)) {
        throw new IllegalArgumentException(
            "주공과 프렌드 자리는 같을 수 없습니다."
        );
      }
    }
  }

  /**
   * 공약 이력이 없는 기존 관측도 생성할 수 있도록 지원합니다.
   */
  public Context(PlayOrder playOrder, Seat declarer, FriendStatus friendStatus,
      List<PublicTrick> history) {
    this(playOrder, declarer, friendStatus, history, List.of());
  }

  public Seat leader() {
    if (history.isEmpty()) {
      return declarer;
    }
    return history.get(history.size() - 1).winner();
  }

  public int captured(Seat seat) {
    Objects.requireNonNull(seat, "조회할 자리가 필요합니다.");
    return history.stream()
        .filter(completed -> completed.winner().equals(seat))
        .mapToInt(PublicTrick::points)
        .sum();
  }

}
