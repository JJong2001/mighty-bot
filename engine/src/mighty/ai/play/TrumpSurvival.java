package mighty.ai.play;

import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.*;

/**
 * 자기 패와 공개 카운팅으로 기루가 외부 상위 소모를 버틸 장수를 계산합니다.
 */
public final class TrumpSurvival {

  private final View view;
  private final PlayFacts facts;

  public TrumpSurvival(View view, PlayFacts facts) {
    this.view = view;
    this.facts = facts;
  }

  /**
   * 미소모 마이티·활성 조커·더 높은 일반 기루를 중복 없이 셉니다.
   */
  public int threatsAbove(StandardCard card) {
    Card mighty = Rules.mighty(view.contract().trump());
    return (int) facts.cards().outsideCards().stream().filter(outside ->
            outside.equals(mighty)
                || (outside instanceof Joker && view.trickNumber().value() < 10)
                || (outside instanceof StandardCard higher && !higher.equals(mighty)
                && higher.suit() == card.suit() && higher.rank().value() > card.rank().value()))
        .count();
  }

  /**
   * 상대 상위가 순서대로 소모되어도 해당 높이의 공격 기회를 남길 장수인지 확인합니다.
   */
  public boolean survives(StandardCard card) {
    if (!view.contract().trump().matches(card.suit())) {
      return false;
    }
    return new TrumpControl(view, facts).own().size() > threatsAbove(card);
  }

}
