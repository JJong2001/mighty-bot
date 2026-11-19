package mighty.simulation;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import mighty.ai.play.PlayPolicy;
import mighty.game.GamePhase;
import mighty.game.action.Action;
import mighty.game.observation.View;
import mighty.game.round.RoundResult;
import mighty.game.round.RoundState;
import mighty.game.trick.PublicPlay;

/**
 * 준비를 마친 한 판에서 지정한 수를 재생하고 나머지는 플레이 정책으로 진행합니다.
 */
public final class RoundReplay {

  private RoundReplay() {
  }

  /**
   * 차례 번호는 초구 첫 카드를 1로 하며 이미 진행된 구와 현재 구를 포함합니다.
   */
  public static ReplayResult run(RoundState round, PlayPolicy policy,
      Map<Integer, Action> overrides) {
    Objects.requireNonNull(round);
    Objects.requireNonNull(policy);
    overrides = Map.copyOf(overrides);
    if (round.phase() != GamePhase.PLAY) {
      throw new IllegalArgumentException("프렌드 지정이 끝난 판이 필요합니다.");
    }
    View initial = round.currentView();
    int next = initial.context().history().size() * mighty.game.GameMode.PLAYING_PLAYER_COUNT
        + initial.trick().size() + 1;
    int last = mighty.game.TrickNumber.LAST * mighty.game.GameMode.PLAYING_PLAYER_COUNT;
    for (int turn : overrides.keySet()) {
      if (turn < next || turn > last) {
        throw new IllegalArgumentException("지정한 차례가 범위를 벗어났습니다.");
      }
    }
    List<PublicPlay> played = new ArrayList<>();
    while (round.phase() == GamePhase.PLAY) {
      View view = round.currentView();
      Action action = overrides.get(next++);
      if (action == null) {
        action = policy.choose(view);
      }
      var play = new PublicPlay(view.self(), action);
      round.play(play);
      played.add(play);
    }
    return new ReplayResult(played, round.result().orElseThrow());
  }

  public record ReplayResult(List<PublicPlay> plays, RoundResult result) {

    public ReplayResult {
      plays = List.copyOf(plays);
    }
  }

}
