package mighty.ai.play;

import java.util.Comparator;
import java.util.List;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.Rank;
import mighty.model.StandardCard;
import mighty.model.Suit;

/**
 * 자기 패와 공개 카드로 부기루의 상위 소모·길이 육성 가능성을 분석합니다.
 */
public record SuitDevelopment(Suit suit, List<StandardCard> held, List<StandardCard> blockers) {

  public SuitDevelopment {
    held = List.copyOf(held);
    blockers = List.copyOf(blockers);
  }

  public static SuitDevelopment analyze(View view, PlayFacts facts, Suit suit) {
    var mighty = Rules.mighty(view.contract().trump());
    List<StandardCard> held = view.hand().stream()
        .filter(card -> card instanceof StandardCard standard
            && standard.suit() == suit && !card.equals(mighty)).map(StandardCard.class::cast)
        .sorted(Comparator.comparingInt((StandardCard card) -> card.rank().value()).reversed())
        .toList();
    int highest = held.isEmpty() ? 0 : held.get(0).rank().value();
    List<StandardCard> blockers = facts.cards().outsideCards().stream()
        .filter(card -> card instanceof StandardCard standard && standard.suit() == suit
            && !card.equals(mighty) && standard.rank().value() > highest)
        .map(StandardCard.class::cast).toList();
    return new SuitDevelopment(suit, held, blockers);
  }

  /**
   * 외부 상위를 빼는 동안 남길 길이가 있는지 확인합니다. 상대 소유·분산은 확정하지 않습니다.
   */
  public boolean canDevelop() {
    return held.size() >= 2 && !blockers.isEmpty() && blockers.size() < held.size()
        && (held.get(0).rank().value() >= Rank.TEN.value() || held.size() >= 4);
  }

  public StandardCard probingCard() {
    return held.get(held.size() - 1);
  }

}
