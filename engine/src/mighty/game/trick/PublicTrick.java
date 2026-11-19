package mighty.game.trick;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import mighty.game.GameMode;
import mighty.game.Seat;
import mighty.game.TrickNumber;
import mighty.model.Card;

public record PublicTrick(
    TrickNumber number,
    List<PublicPlay> cards,
    Seat winner
) {

  public PublicTrick {
    Objects.requireNonNull(number, "구 번호가 필요합니다.");
    Objects.requireNonNull(winner, "승자 자리가 필요합니다.");
    cards = List.copyOf(cards);
    if (cards.size() != GameMode.PLAYING_PLAYER_COUNT) {
      throw new IllegalArgumentException(
          "완료된 구에는 다섯 사람의 제출이 필요합니다."
      );
    }
    Set<Seat> submittedSeats = new HashSet<>();
    Set<Card> submittedCards = new HashSet<>();
    for (PublicPlay play : cards) {
      if (!submittedSeats.add(play.seat())) {
        throw new IllegalArgumentException(
            "한 사람이 같은 구에 두 번 제출했습니다."
        );
      }
      if (!submittedCards.add(play.action().card())) {
        throw new IllegalArgumentException(
            "한 구에 같은 카드가 두 번 제출됐습니다."
        );
      }
    }
    if (!submittedSeats.contains(winner)) {
      throw new IllegalArgumentException(
          "승자는 이 구에 제출한 사람이어야 합니다."
      );
    }
  }

  // 카드를 처음 낸 사람이 선
  public Seat leader() {
    return cards.get(0).seat();
  }

  // 승자가 가져간 점수
  public int points() {
    return cards.stream()
        .mapToInt(play -> play.action().card().points())
        .sum();
  }

}
