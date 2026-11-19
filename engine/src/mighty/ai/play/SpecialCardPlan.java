package mighty.ai.play;

import java.util.List;
import java.util.Optional;
import mighty.game.Role;
import mighty.game.action.Action;
import mighty.game.friend.SpecialFriend;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.Joker;
import mighty.model.StandardCard;
import mighty.model.Trump;

/**
 * 마이티의 강제 소모 위험과 조커의 문양 선택권을 고려하는 공통 사용 순서입니다.
 */
public final class SpecialCardPlan {

  private SpecialCardPlan() {
  }

  /**
   * 둘로 잡을 수 있으면 마이티가 기본입니다. 9구의 활성 조커 사용 기한은 먼저 처리합니다.
   */
  public static Optional<Action> mightyBeforeJoker(List<Action> winning, View view) {
    if (view.role() == Role.OPPOSITION || view.role() == Role.UNDETERMINED
        || view.trickNumber().value() == 9) {
      return Optional.empty();
    }
    boolean jokerWins = winning.stream().anyMatch(action -> action.card() instanceof Joker);
    return jokerWins ? winning.stream()
        .filter(action -> action.card().equals(Rules.mighty(view.contract().trump()))).findFirst()
        : Optional.empty();
  }

  public static Optional<Action> leadMighty(List<Action> legal, View view, PlayFacts facts) {
    if (view.trickNumber().value() == 9 || !view.hand().contains(Joker.INSTANCE)
        || view.role() == Role.OPPOSITION || view.role() == Role.UNDETERMINED
        || (view.trickNumber().value() == 1
        && view.friendSelection() == SpecialFriend.FIRST_TRICK)) {
      return Optional.empty();
    }
    var mighty = Rules.mighty(view.contract().trump());
    // NT 초구의 K/Q 연결 신호처럼 확인된 자력 초구 계획은 유지합니다.
    long mightySuitLength = view.hand().stream()
        .filter(card -> card instanceof StandardCard standard
            && standard.suit() == mighty.suit()).count();
    if (view.contract().trump() == Trump.NO_TRUMP && view.trickNumber().value() == 1
        && mightySuitLength > 1 && PlaySupport.ordinary(legal, view).stream()
        .anyMatch(action -> facts.isSuitTop(action.card()))) {
      return Optional.empty();
    }
    return legal.stream().filter(action -> action.card().equals(mighty)).findFirst();
  }

  /**
   * 자신이 마이티를 쓴 이력만 읽습니다. 다른 사람의 마이티를 자기 자원으로 계산하지 않습니다.
   */
  public static boolean usedOwnMighty(View view) {
    var mighty = Rules.mighty(view.contract().trump());
    return view.publicCardHistory().wasPlayedBy(view.self(), mighty);
  }

}
