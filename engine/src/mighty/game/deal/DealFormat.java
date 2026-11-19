package mighty.game.deal;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum DealFormat {
  FIVE_PLAYER_OPENING(5, 10, 3),
  SIX_PLAYER_OPENING(6, 8, 5);
  private final int participantCount;
  private final int handSize;
  private final int kittySize;

  public int participantCount() {
    return participantCount;
  }

  public int handSize() {
    return handSize;
  }

  public int kittySize() {
    return kittySize;
  }

}
