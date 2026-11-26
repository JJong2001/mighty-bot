package mighty.ai.play.opposition;

import mighty.ai.play.PlayFacts;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.*;

/**
 * 공개 공약·리드·무문양으로 주공의 운영 문양과 그 문양을 버틸 자기 수비를 구분합니다.
 */
final class DeclarerPressure {

  private final View view;
  private final PlayFacts facts;

  DeclarerPressure(View view, PlayFacts facts) {
    this.view = view;
    this.facts = facts;
  }

  boolean operating(Suit suit) {
    return mighty.ai.play.DeclarerOperations.contains(view, facts, suit);
  }

  /**
   * 나중에 지원할 기대가 아니라 그 문양을 직접 이길 높이와 버틸 장수를 확인합니다.
   */
  boolean defense(StandardCard card) {
    if (!operating(card.suit())) {
      return false;
    }
    var held = view.hand().stream().filter(StandardCard.class::isInstance)
        .map(StandardCard.class::cast)
        .filter(c -> c.suit() == card.suit() && !c.equals(Rules.mighty(view.contract().trump())))
        .toList();
    if (!held.contains(card)) {
      return false;
    }
    if (card.points() > 0 && OppositionGuards.protectsDevelopment(view, facts, card.suit())) {
      return true;
    }
    int length = held.size();
    int highest = held.stream().mapToInt(c -> c.rank().value()).max().orElseThrow();
    // 상위가 모두 빠진 9도 수비가 됩니다. 낮은 받침 카드까지 공격 카드로 보지는 않습니다.
    if (card.points() == 0 && card.rank().value() < highest) {
      return false;
    }
    long blockers = facts.cards().outsideCards().stream().filter(c -> c instanceof StandardCard s
        && s.suit() == card.suit() && !s.equals(Rules.mighty(view.contract().trump()))
        && s.rank().value() > highest).count();
    long specials = facts.cards().outsideCards().stream().filter(c -> c instanceof Joker
        || c.equals(Rules.mighty(view.contract().trump()))).count();
    return length > blockers + specials;
  }

}
