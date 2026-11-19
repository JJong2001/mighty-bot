package mighty.ai.preparation;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import mighty.game.observation.DiscardView;
import mighty.game.rule.Rules;
import mighty.model.Card;
import mighty.model.Joker;
import mighty.model.StandardCard;
import mighty.model.Trump;

/**
 * 특수카드와 기루를 우선 보존하고 낮은 일반 카드 세 장을 버리는 기준 정책입니다.
 */
public final class BaselineDiscardPolicy implements DiscardPolicy {

  @Override
  public List<Card> choose(DiscardView view) {
    Objects.requireNonNull(view, "버리기 관측이 필요합니다.");
    return view.hand().stream()
        .sorted(Comparator.<Card>comparingInt(card -> retentionCost(card, view.contract().trump()))
            .thenComparing(Card::toCode))
        .limit(3)
        .toList();
  }

  /**
   * 기준 정책에서 보존할 카드의 우선순위입니다. 기대 득점은 아닙니다.
   */
  private static int retentionCost(Card card, Trump trump) {
    if (card.equals(Rules.mighty(trump))) {
      return 100;
    }
    if (card instanceof Joker) {
      return 90;
    }
    StandardCard standard = (StandardCard) card;
    return standard.rank().value() + (trump.matches(standard.suit()) ? 20 : 0);
  }

}
