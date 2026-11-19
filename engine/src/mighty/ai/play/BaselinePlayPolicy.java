package mighty.ai.play;

import java.util.Comparator;
import java.util.Objects;
import mighty.game.action.Action;
import mighty.game.action.StandardPlay;
import mighty.game.observation.View;
import mighty.game.rule.LegalActionContext;
import mighty.game.rule.LegalActions;
import mighty.game.rule.Rules;
import mighty.model.Joker;
import mighty.model.StandardCard;
import mighty.model.Trump;

/**
 * 합법 행동 중 낮은 일반 카드를 우선 내는 최소 플레이 정책입니다.
 */
public final class BaselinePlayPolicy implements PlayPolicy {

  @Override
  public Action choose(View view) {
    Objects.requireNonNull(view, "플레이 관측이 필요합니다.");
    return LegalActions.legalActions(new LegalActionContext(
            view.hand(), view.contract().trump(), view.trickNumber(), view.trick()))
        .stream()
        .min(Comparator.<Action>comparingInt(action -> playCost(action, view.contract().trump()))
            .thenComparing(action -> action.card().toCode()))
        .orElseThrow(() -> new IllegalStateException("선택할 수 있는 합법 행동이 없습니다."));
  }

  /**
   * 특수카드와 기루를 아끼기 위한 기준 정책의 선택 순서입니다.
   */
  private static int playCost(Action action, Trump trump) {
    if (action.card().equals(Rules.mighty(trump))) {
      return 100;
    }
    if (action.card() instanceof Joker) {
      return 90;
    }
    StandardCard card = (StandardCard) action.card();
    int cost = card.rank().value() + (trump.matches(card.suit()) ? 20 : 0);
    // 같은 총 카드라면 자동 콜보다 일반 제출을 우선합니다.
    return cost + (action instanceof StandardPlay play && play.call() ? 1 : 0);
  }

}
