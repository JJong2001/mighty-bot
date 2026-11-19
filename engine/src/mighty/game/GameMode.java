package mighty.game;

import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public enum GameMode {
  FIVE_PLAYER(5),
  SIX_PLAYER(6);
  public static final int PLAYING_PLAYER_COUNT = 5;
  private final int tablePlayerCount;

  public int tablePlayerCount() {
    return tablePlayerCount;
  }

}
