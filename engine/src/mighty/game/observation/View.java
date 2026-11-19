package mighty.game.observation;

import java.util.List;
import java.util.Objects;
import mighty.game.bidding.Bid;
import mighty.game.GameMode;
import mighty.game.Role;
import mighty.game.Seat;
import mighty.game.TrickNumber;
import mighty.game.friend.FriendSelection;
import mighty.game.trick.PublicPlay;
import mighty.model.Card;

public record View(
    Seat self,
    PrivateInfo privateInfo,
    Role role,
    Bid contract,
    FriendSelection friendSelection,
    Context context,
    List<PublicPlay> trick
) {

  public View {
    Objects.requireNonNull(self, "행동자 자리가 필요합니다.");
    Objects.requireNonNull(privateInfo, "개인 정보가 필요합니다.");
    Objects.requireNonNull(role, "역할이 필요합니다.");
    Objects.requireNonNull(contract, "공약이 필요합니다.");
    Objects.requireNonNull(friendSelection, "프렌드 지정 정보가 필요합니다.");
    Objects.requireNonNull(context, "공개 정보가 필요합니다.");
    trick = List.copyOf(trick);
    boolean declarerRole = role == Role.DECLARER;
    boolean declarerSeat = self.equals(context.declarer());
    boolean declarerInfo = privateInfo instanceof DeclarerInfo;
    if (declarerRole != declarerSeat
        || declarerRole != declarerInfo) {
      throw new IllegalArgumentException(
          "주공 자리·역할·개인 정보 타입이 일치하지 않습니다."
      );
    }
    int completedTricks = context.history().size();
    if (completedTricks >= TrickNumber.LAST) {
      throw new IllegalArgumentException(
          "종료된 대국은 플레이 관측으로 생성할 수 없습니다."
      );
    }
    if (trick.size() >= GameMode.PLAYING_PLAYER_COUNT) {
      throw new IllegalArgumentException(
          "완료된 구는 다음 행동 차례의 관측에 들어갈 수 없습니다."
      );
    }
    int expectedHandSize = TrickNumber.LAST - completedTricks;
    if (privateInfo.hand().size() != expectedHandSize) {
      throw new IllegalArgumentException(
          "완료된 구 수와 행동자의 손패 장수가 일치하지 않습니다."
      );
    }
    validateActingTurn(self, context, trick);
  }

  public List<Card> hand() {
    return privateInfo.hand();
  }

  public TrickNumber trickNumber() {
    return new TrickNumber(context.history().size() + 1);
  }

  /**
   * 완료된 구에 공개된 카드. 현재 구의 카드는 포함하지 않는다.
   */
  public List<Card> played() {
    return context.history().stream()
        .flatMap(completed -> completed.cards().stream())
        .map(play -> play.action().card())
        .toList();
  }

  /**
   * 완료된 구와 현재 구를 함께 읽는 공통 공개 카드 조회입니다.
   */
  public PublicCardHistory publicCardHistory() {
    return new PublicCardHistory(context.history(), trick);
  }

  private static void validateActingTurn(
      Seat self,
      Context context,
      List<PublicPlay> trick
  ) {
    Seat leader = context.leader();
    List<Seat> submittedSeats = trick.stream()
        .map(PublicPlay::seat)
        .toList();
    context.playOrder().requireSequence(leader, submittedSeats);
    Seat expectedSelf;
    if (trick.isEmpty()) {
      expectedSelf = leader;
    } else {
      Seat lastSubmitter = trick.get(trick.size() - 1).seat();
      expectedSelf = context.playOrder().nextAfter(lastSubmitter);
    }
    if (!self.equals(expectedSelf)) {
      throw new IllegalArgumentException(
          "현재 차례와 관측의 행동자가 일치하지 않습니다."
              + " 예상=" + expectedSelf.index()
              + ", 입력=" + self.index()
      );
    }
  }

}
