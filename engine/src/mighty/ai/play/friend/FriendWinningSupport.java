package mighty.ai.play.friend;

import java.util.*;
import mighty.ai.play.*;
import mighty.game.Role;
import mighty.game.action.Action;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.game.trick.PublicPlay;
import mighty.model.*;

/**
 * 보호·상위 탐색을 통과한 실제 지원은 공약과 리드 문양에 따라 일반 승리와 마이티의 순서를 결정합니다.
 */
public final class FriendWinningSupport {

  private FriendWinningSupport() {
  }

  public static Optional<PlayDecision> choose(View view, PlayFacts facts, List<Action> legal) {
    if (view.role() != Role.FRIEND || view.trick().isEmpty()) {
      return Optional.empty();
    }
    var winner = facts.currentWinner().orElseThrow();
    if (facts.allyWinning() && (facts.isSecured(winner) || facts.isSuitTop(winner.action().card())
        || winner.action().card() instanceof Joker || winner.action().card()
        .equals(Rules.mighty(view.contract().trump()))
        || winner.action().card() instanceof StandardCard c && view.contract().trump()
        .matches(c.suit())
        && c.suit() != facts.conditions().leadSuit())) {
      return Optional.empty();
    }
    var winning = facts.winningActions().stream().filter(legal::contains).toList();
    var mighty = winning.stream()
        .filter(a -> a.card().equals(Rules.mighty(view.contract().trump()))).findFirst();
    var ordinary = PlaySupport.ordinary(winning, view).stream()
        .filter(a -> ((StandardCard) a.card()).suit() == facts.conditions().leadSuit())
        .filter(a -> facts.isSuitTop(a.card())
            || facts.isSecured(new PublicPlay(view.self(), a))).toList();
    // 문양 공약의 사이드는 일반 탑카로 잡아 기루 지원에 쓸 마이티를 남깁니다.
    Trump trump = view.contract().trump();
    boolean sideLead = trump != Trump.NO_TRUMP && !trump.matches(facts.conditions().leadSuit());
    if (sideLead && !ordinary.isEmpty()) {
      return Optional.of(new PlayDecision(PlaySupport.cheapest(ordinary, view),
          "사이드의 일반 승리 카드로 지원하고 기루에 쓸 마이티를 남깁니다."));
    }
    if (mighty.isPresent()) {
      if (view.contract().trump() != Trump.NO_TRUMP) {
        var run = ordinary.stream().filter(a -> continuesRun(view, facts, (StandardCard) a.card()))
            .min(Comparator.comparingInt(a -> ((StandardCard) a.card()).rank().value()));
        if (run.isPresent()) {
          return Optional.of(new PlayDecision(run.get(),
              "일반 카드로 이긴 뒤에도 카운팅상 연속 승리가 이어져 마이티를 다른 지원에 남깁니다."));
        }
      }
      return Optional.of(
          new PlayDecision(mighty.get(), "지원이 필요한 승리는 마이티로 먼저 잡고 일반 탑카와 조커의 후속 활용을 남깁니다."));
    }
    if (!ordinary.isEmpty()) {
      return Optional.of(new PlayDecision(PlaySupport.cheapest(ordinary, view),
          "마이티가 없으므로 일반 승리 카드로 지원합니다."));
    }
    if (PlaySupport.ordinary(winning, view).stream().anyMatch(a ->
        view.contract().trump().matches(((StandardCard) a.card()).suit()))) {
      return Optional.empty();
    }
    return winning.stream().filter(a -> a.card() instanceof Joker).findFirst()
        .map(a -> new PlayDecision(a, "일반 탑카로 확보할 수 없어 조커로 지원하고 반환합니다."));
  }

  private static boolean continuesRun(View view, PlayFacts facts, StandardCard used) {
    if (!view.contract().trump().matches(used.suit())) {
      return false;
    }
    var remaining = view.hand().stream()
        .filter(c -> c instanceof StandardCard s && s.suit() == used.suit()
            && !c.equals(used) && !c.equals(Rules.mighty(view.contract().trump())))
        .map(StandardCard.class::cast)
        .sorted(Comparator.comparingInt((StandardCard c) -> c.rank().value()).reversed()).toList();
    int connected = 0;
    for (StandardCard card : remaining) {
      boolean blocked = facts.cards().outsideCards().stream()
          .anyMatch(c -> c instanceof StandardCard higher
              && higher.suit() == card.suit() && !c.equals(Rules.mighty(view.contract().trump()))
              && higher.rank().value() > card.rank().value());
      if (blocked) {
        break;
      }
      connected++;
    }
    return connected >= (facts.remainingSeats().isEmpty() ? 1 : 2);
  }

}
