package mighty.game.bidding;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import mighty.game.Seat;
import mighty.game.deal.DealParticipants;
import mighty.model.Card;

public final class BiddingState {

  private final DealParticipants participants;
  private final Seat openingSeat;
  private final List<PublicBid> history;

  private BiddingState(
      DealParticipants participants,
      Seat openingSeat,
      List<PublicBid> history
  ) {
    this.participants = participants;
    this.openingSeat = openingSeat;
    this.history = List.copyOf(history);
  }

  public static BiddingState start(
      DealParticipants participants,
      Seat openingSeat
  ) {
    Objects.requireNonNull(participants, "비딩 참가자가 필요합니다.");
    Objects.requireNonNull(openingSeat, "비딩 시작 자리가 필요합니다.");
    if (!participants.seats().contains(openingSeat)) {
      throw new IllegalArgumentException(
          "비딩 시작 자리는 참가자에 포함되어야 합니다."
      );
    }
    return new BiddingState(
        participants,
        openingSeat,
        List.of()
    );
  }

  public Optional<PublicBid> highestBid() {
    for (int index = history.size() - 1; index >= 0; index--) {
      PublicBid publicBid = history.get(index);
      if (publicBid.action() instanceof BidOffer) {
        return Optional.of(publicBid);
      }
    }
    return Optional.empty();
  }

  public Set<Seat> passedSeats() {
    Set<Seat> passed = new HashSet<>();
    for (PublicBid publicBid : history) {
      if (publicBid.action() instanceof BidPass) {
        passed.add(publicBid.seat());
      }
    }
    return Set.copyOf(passed);
  }

  /**
   * 해당 참가자가 이번 비딩에서 공약을 제시한 적이 있는지 확인합니다.
   */
  public boolean hasOffered(Seat seat) {
    Objects.requireNonNull(seat, "확인할 자리가 필요합니다.");
    if (!participants.seats().contains(seat)) {
      throw new IllegalArgumentException(
          "이번 비딩의 참가자가 아닙니다."
      );
    }
    return history.stream()
        .anyMatch(publicBid ->
            publicBid.seat().equals(seat)
                && publicBid.action() instanceof BidOffer
        );
  }

  public boolean isFinished() {
    // 딜미스 선언이 접수되면 재배분을 위해 비딩을 종료합니다.
    if (!history.isEmpty()
        && history.get(history.size() - 1).action() instanceof DealMiss) {
      return true;
    }
    Optional<PublicBid> highest = highestBid();
    if (highest.isEmpty()) {
      return false;
    }
    BidOffer offer = (BidOffer) highest.orElseThrow().action();
    if (offer.bid().points() == Bid.MAX_POINTS) {
      return true;
    }
    return passedSeats().size() == participants.seats().size() - 1;
  }

  /**
   * 종료된 비딩의 낙찰 또는 딜미스 결과를 반환합니다.
   */
  public Optional<BiddingOutcome> result() {
    if (!isFinished()) {
      return Optional.empty();
    }
    PublicBid lastAction = history.get(history.size() - 1);
    // 이전 입찰이 있어도 딜미스 종료 시에는 낙찰 결과를 만들지 않습니다.
    if (lastAction.action() instanceof DealMiss) {
      return Optional.of(
          new DealMissResult(lastAction.seat())
      );
    }
    PublicBid winningBid = highestBid().orElseThrow(
        () -> new IllegalStateException(
            "낙찰된 비딩에 공약이 없습니다."
        )
    );
    BidOffer offer = (BidOffer) winningBid.action();
    return Optional.of(
        new BiddingResult(winningBid.seat(), offer.bid())
    );
  }

  public Optional<Seat> currentSeat() {
    if (isFinished()) {
      return Optional.empty();
    }
    if (history.isEmpty()) {
      return Optional.of(openingSeat);
    }
    List<Seat> seats = participants.seats();
    Set<Seat> passed = passedSeats();
    Seat lastSeat = history.get(history.size() - 1).seat();
    int lastIndex = seats.indexOf(lastSeat);
    for (int offset = 1; offset <= seats.size(); offset++) {
      int index = (lastIndex + offset) % seats.size();
      Seat candidate = seats.get(index);
      if (!passed.contains(candidate)) {
        return Optional.of(candidate);
      }
    }
    throw new IllegalStateException(
        "진행 중인 비딩에 행동 가능한 참가자가 없습니다."
    );
  }

  /**
   * 공개 정보로 검증 가능한 입찰 또는 패스를 접수합니다.
   */
  public BiddingState submit(PublicBid publicBid) {
    Objects.requireNonNull(publicBid, "비딩 기록이 필요합니다.");
    requireTurn(publicBid.seat());
    if (publicBid.action() instanceof DealMiss) {
      throw new IllegalArgumentException(
          "딜미스 제출에는 자기 손패와 보유 점수가 필요합니다."
      );
    }
    validateAction(publicBid.action());
    return append(publicBid);
  }

  /**
   * 자기 손패와 보유 점수가 필요한 딜미스까지 접수합니다. 개인 정보는 검증에만 사용하며 상태에 저장하지 않습니다.
   */
  public BiddingState submit(
      PublicBid publicBid,
      List<Card> hand,
      int playerScore
  ) {
    Objects.requireNonNull(publicBid, "비딩 기록이 필요합니다.");
    if (!(publicBid.action() instanceof DealMiss)) {
      return submit(publicBid);
    }
    requireTurn(publicBid.seat());
    if (!DealMissRules.canDeclare(this, hand, playerScore)) {
      throw new IllegalArgumentException(
          "딜미스를 선언할 수 있는 조건을 만족하지 않습니다."
      );
    }
    return append(publicBid);
  }

  /**
   * 진행 중인 비딩에서 해당 자리의 차례인지 확인합니다.
   */
  private void requireTurn(Seat seat) {
    Seat expectedSeat = currentSeat().orElseThrow(
        () -> new IllegalStateException(
            "이미 종료된 비딩에는 행동할 수 없습니다."
        )
    );
    if (!seat.equals(expectedSeat)) {
      throw new IllegalArgumentException(
          "현재 비딩할 차례가 아닙니다."
      );
    }
  }

  /**
   * 검증된 행동을 추가한 새 상태를 만듭니다.
   */
  private BiddingState append(PublicBid publicBid) {
    List<PublicBid> nextHistory = new ArrayList<>(history);
    nextHistory.add(publicBid);
    return new BiddingState(
        participants,
        openingSeat,
        nextHistory
    );
  }

  private void validateAction(BiddingAction action) {
    if (action instanceof BidPass) {
      if (history.isEmpty()) {
        throw new IllegalArgumentException(
            "첫 비딩자가 반드시 공약을 제시해야 합니다."
        );
      }
      return;
    }
    if (action instanceof BidOffer offer) {
      highestBid().ifPresent(previous -> {
        BidOffer previousOffer = (BidOffer) previous.action();
        if (!BiddingRules.isHigher(
            offer.bid(),
            previousOffer.bid()
        )) {
          throw new IllegalArgumentException(
              "현재 최고 공약보다 높은 공약을 제시해야 합니다."
          );
        }
      });
      return;
    }
    throw new IllegalArgumentException(
        "지원하지 않는 비딩 행동입니다."
    );
  }

  public DealParticipants participants() {
    return participants;
  }

  public Seat openingSeat() {
    return openingSeat;
  }

  public List<PublicBid> history() {
    return history;
  }

}
