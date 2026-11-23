package mighty.ai.play.friend;

import java.util.Comparator;
import java.util.List;
import mighty.ai.play.*;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.*;

/**
 * 점수가 강제로 빨려 나오는 것을 막을 물패와 주공 운영 문양의 연결을 구분합니다.
 */
final class FriendResources {

  private final View view;
  private final PlayFacts facts;

  FriendResources(View view, PlayFacts facts) {
    this.view = view;
    this.facts = facts;
  }

  private List<StandardCard> holding(Suit suit) {
    return view.hand().stream().filter(c -> c instanceof StandardCard card && card.suit() == suit
        && !c.equals(Rules.mighty(view.contract().trump()))).map(StandardCard.class::cast).toList();
  }

  boolean preserve(StandardCard card) {
    var held = holding(card.suit());
    var highest = held.stream().max(Comparator.comparingInt(c -> c.rank().value())).orElseThrow();
    long blockers = facts.cards().outsideCards(card.suit()).stream()
        .filter(other -> !other.equals(Rules.mighty(view.contract().trump())))
        .filter(other -> other.rank().value() > highest.rank().value()).count();
    // 상위 소모를 버티지 못하는 세컨탑카는 반환용 한 장이어도 점수로 처리합니다.
    if (card.points() > 0 && !facts.isSuitTop(card) && held.size() <= blockers) {
      return false;
    }
    if (FriendReturnReserve.protects(view, card)) {
      return true;
    }
    // 탑카도 실제로 선을 얻어 사용할 경로가 있어야 보존합니다.
    if (facts.isSuitTop(card)) {
      boolean entry = view.hand().stream().anyMatch(c -> c instanceof Joker
          || c.equals(Rules.mighty(view.contract().trump())));
      if (entry) {
        return true;
      }
      int possibleTrump = facts.cards().trumps().outside().size();
      int special = (facts.cards().outsideCards().contains(Joker.INSTANCE) ? 1 : 0)
          + (facts.cards().outsideCards().contains(Rules.mighty(view.contract().trump())) ? 1 : 0);
      int sideSlots = facts.cards().trumps().remainingHand(view.context().declarer())
          - possibleTrump - special;
      if (sideSlots > 0 && DeclarerOperations.contains(view, facts, card.suit())
          && !facts.cards().isVoid(view.context().declarer(), card.suit())) {
        return true;
      }
    }
    if (!DeclarerOperations.contains(view, facts, card.suit())) {
      return false;
    }
    if (held.size() == 1) {
      return false;
    }
    if (held.size() <= blockers) {
      return false;
    }
    if (card.points() == 0) {
      return true;
    }
    return facts.cards().outsideCards().stream().noneMatch(c -> c instanceof StandardCard other
        && other.suit() == card.suit() && !c.equals(Rules.mighty(view.contract().trump()))
        && other.rank().value() > card.rank().value() && other.rank().value() < highest.rank()
        .value());
  }

  int supportCost(StandardCard card) {
    var held = holding(card.suit());
    long water = held.stream().filter(c -> c.points() == 0).count();
    long points = held.stream().filter(c -> c.points() > 0).count();
    return (preserve(card) ? 1000 : 0) + (facts.isSuitTop(card) ? 200 : 0)
        + (int) (100 * water / Math.max(1, points)) + card.rank().value();
  }

  int discardCost(StandardCard card) {
    var held = holding(card.suit());
    long water = held.stream().filter(c -> c.points() == 0).count();
    long points = held.stream().filter(c -> c.points() > 0).count();
    int coverage = FriendReturnReserve.protects(view, card) ? 80 : 0;
    return coverage + (preserve(card) ? 1000 : 0)
        + (points > 0 ? (int) (100 * points / Math.max(1, water)) : 0) + card.rank().value();
  }

}
