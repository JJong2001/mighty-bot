package mighty.game.observation;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import mighty.game.Seat;
import mighty.game.TrickNumber;
import mighty.game.trick.PublicPlay;
import mighty.game.trick.PublicTrick;
import mighty.model.Card;

/**
 * 완료된 구와 현재 구의 공개 제출을 조회합니다. 손패와 비공개 버림패는 포함하지 않습니다.
 */
public final class PublicCardHistory {

  private final List<PublicTrick> completedTricks;
  private final List<PublicPlay> completedPlays;
  private final List<PublicPlay> currentPlays;
  private final List<PublicPlay> submittedPlays;
  private final Set<Card> cards;

  public PublicCardHistory(List<PublicTrick> completedTricks, List<PublicPlay> currentPlays) {
    this.completedTricks = List.copyOf(completedTricks);
    this.currentPlays = List.copyOf(currentPlays);
    completedPlays = this.completedTricks.stream().flatMap(t -> t.cards().stream()).toList();
    submittedPlays = Stream.concat(completedPlays.stream(), this.currentPlays.stream()).toList();
    cards = submittedPlays.stream().map(p -> p.action().card())
        .collect(Collectors.toUnmodifiableSet());
  }

  /**
   * 완료된 구의 제출만 순서대로 반환합니다.
   */
  public List<PublicPlay> completedPlays() {
    return completedPlays;
  }

  /**
   * 아직 끝나지 않은 현재 구의 제출만 반환합니다.
   */
  public List<PublicPlay> currentPlays() {
    return currentPlays;
  }

  /**
   * 완료된 구 다음에 현재 구를 붙인 공개 제출 순서입니다.
   */
  public List<PublicPlay> submittedPlays() {
    return submittedPlays;
  }

  /**
   * 현재까지 공개된 모든 카드입니다. 자기 손패나 주공의 버림패는 들어가지 않습니다.
   */
  public Set<Card> cards() {
    return cards;
  }

  public boolean hasBeenPlayed(Card card) {
    return cards.contains(card);
  }

  public boolean wasPlayedBy(Seat seat, Card card) {
    return submittedPlays.stream()
        .anyMatch(p -> p.seat().equals(seat) && p.action().card().equals(card));
  }

  /**
   * 과거 판단에 이후 구와 현재 구의 카드를 소급 적용하지 않도록 완료 구의 범위를 제한합니다.
   */
  public Set<Card> completedCardsThrough(TrickNumber number) {
    return completedTricks.stream().filter(t -> t.number().value() <= number.value())
        .flatMap(t -> t.cards().stream()).map(p -> p.action().card())
        .collect(Collectors.toUnmodifiableSet());
  }

}
