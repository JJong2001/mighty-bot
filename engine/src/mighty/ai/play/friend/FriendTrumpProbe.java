package mighty.ai.play.friend;

import java.util.*;
import mighty.ai.play.*;
import mighty.game.action.Action;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.*;

/**
 * 현재 서열의 바로 아래에서 시작한 상위 탐색과 새로운 지원 요청을 구분합니다.
 */
final class FriendTrumpProbe {

  private FriendTrumpProbe() {
  }

  static Optional<PlayDecision> choose(View view, PlayFacts facts, List<Action> legal) {
    var lead = view.trick().get(0);
    if (!lead.seat().equals(view.context().declarer()) || !(lead.action()
        .card() instanceof StandardCard card)
        || !view.contract().trump().matches(card.suit())) {
      return Optional.empty();
    }
    if (facts.currentWinner().orElseThrow().action().card() instanceof Joker
        && !facts.allyWinning()) {
      return Optional.empty();
    }
    var spent = facts.cards().publicHistory().completedPlays().stream().map(p -> p.action().card())
        .toList();
    var above = Arrays.stream(Rank.values()).filter(rank -> rank.value() > card.rank().value())
        .map(rank -> new StandardCard(card.suit(), rank))
        .filter(c -> !c.equals(Rules.mighty(view.contract().trump())))
        .filter(c -> !spent.contains(c)).toList();
    // 고정된 K/Q/J가 아니라 공개 소모 뒤의 실제 남은 서열을 확인합니다.
    if (above.isEmpty()) {
      return Optional.empty();
    }
    // 처음부터 여러 상위가 남은 리드는 지원 요청입니다. 바로 아래에서 시작한
    // 탐색만 같은 최고 카드가 남아 있는 동안 후속 숫자까지 이어집니다.
    if (above.size() > 1 && !continuedProbe(view, card, above)) {
      return Optional.empty();
    }
    Card target = above.stream().max(Comparator.comparingInt(c -> c.rank().value())).orElseThrow();
    var showing = legal.stream()
        .filter(a -> a.card().equals(target) && facts.winningActions().contains(a)).findFirst();
    if (showing.isPresent()) {
      return Optional.of(new PlayDecision(showing.get(),
          "주공이 찾는 현재 미소모 최고 일반 기루를 보여주고 특수카드를 남깁니다."));
    }
    if (view.trick().stream().anyMatch(p -> p.action().card().equals(target))) {
      return Optional.empty();
    }
    List<Action> ordinary = PlaySupport.ordinary(legal, view);
    var lowTrump = ordinary.stream().filter(a -> a.card() instanceof StandardCard low
        && low.suit() == card.suit() && low.points() == 0).toList();
    if (!lowTrump.isEmpty()) {
      return Optional.of(new PlayDecision(PlaySupport.highest(lowTrump),
          "상위 확인을 기다리며 비점수 기루를 높은 것부터 따릅니다."));
    }
    if (!ordinary.isEmpty()) {
      return Optional.of(new PlayDecision(PlaySupport.cheapest(ordinary, view),
          "현재 남은 상위 확인 중에는 특수카드를 쓰지 않고 일반 카드로 따릅니다."));
    }
    return legal.stream().filter(a -> a.card() instanceof Joker).findFirst()
        .map(a -> new PlayDecision(a, "일반 카드가 없어 조커로 따르고 상위 탐색용 마이티를 남깁니다."));
  }

  private static boolean continuedProbe(View view, StandardCard current, List<StandardCard> above) {
    Set<Card> spent = new HashSet<>();
    StandardCard target = above.stream().max(Comparator.comparingInt(c -> c.rank().value()))
        .orElseThrow();
    for (var trick : view.context().history()) {
      var lead = trick.cards().get(0);
      if (lead.seat().equals(view.context().declarer())
          && lead.action().card() instanceof StandardCard previous
          && previous.suit() == current.suit()
          && previous.rank().value() > current.rank().value()) {
        var remainingAbove = Arrays.stream(Rank.values())
            .filter(rank -> rank.value() > previous.rank().value())
            .map(rank -> new StandardCard(previous.suit(), rank))
            .filter(c -> !c.equals(Rules.mighty(view.contract().trump())) && !spent.contains(c))
            .toList();
        if (remainingAbove.equals(List.of(target))) {
          return true;
        }
      }
      trick.cards().forEach(p -> spent.add(p.action().card()));
    }
    return false;
  }

}
