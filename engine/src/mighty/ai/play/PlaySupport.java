package mighty.ai.play;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import mighty.game.bidding.BidOffer;
import mighty.game.bidding.PublicContractHistory;
import mighty.game.action.Action;
import mighty.game.action.JokerLead;
import mighty.game.action.StandardPlay;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.Joker;
import mighty.model.StandardCard;
import mighty.model.Suit;
import mighty.model.Trump;

/**
 * 선과 따라내기 전략에서 공유하는 카드 선택 기준입니다.
 */
public final class PlaySupport {

  private PlaySupport() {
  }

  /**
   * 특수카드, 기루, 일반 카드 순으로 보존 비용을 높게 둡니다.
   */
  public static int cost(Action action, View view) {
    if (action.card().equals(Rules.mighty(view.contract().trump()))) {
      return 100;
    }
    if (action.card() instanceof Joker) {
      return 90;
    }
    StandardCard card = (StandardCard) action.card();
    return card.rank().value() + (view.contract().trump().matches(card.suit()) ? 20 : 0)
        + (action instanceof StandardPlay play && play.call() ? 1 : 0);
  }

  /**
   * 같은 우선순위에서도 재현 가능한 가장 저렴한 행동을 선택합니다.
   */
  public static Action cheapest(List<Action> actions, View view) {
    return actions.stream().min(Comparator.<Action>comparingInt(action -> cost(action, view))
        .thenComparing(action -> action.card().toCode())).orElseThrow();
  }

  /**
   * 마이티와 조커를 제외한 일반 행동을 구합니다.
   */
  public static List<Action> ordinary(List<Action> actions, View view) {
    return actions.stream().filter(action -> action.card() instanceof StandardCard
        && !action.card().equals(Rules.mighty(view.contract().trump()))).toList();
  }

  /**
   * 공개 공약 문양을 최근 순서로, 이어서 주공이 선으로 낸 문양을 반환 후보로 구합니다.
   */
  public static List<Suit> returnSuits(View view) {
    List<Suit> suits = new ArrayList<>(mighty.game.bidding.PublicContractHistory
        .suitsMostRecentFirst(view.context().contractHistory()));
    for (var trick : view.context().history()) {
      if (trick.leader().equals(view.context().declarer())) {
        Suit suit = Rules.conditionsFor(view.contract().trump(), trick.number(),
            trick.cards().get(0).action()).leadSuit();
        if (!suits.contains(suit)) {
          suits.add(suit);
        }
      }
    }
    return List.copyOf(suits);
  }

  /**
   * 조커 선의 문양도 공개 반환 계획을 우선합니다. 없으면 자기 최장 문양을 씁니다.
   */
  public static Action jokerLead(List<Action> jokers, View view) {
    return JokerSuitPlan.choose(jokers, view);
  }

  public static Action jokerLead(List<Action> jokers, View view, PlayFacts facts) {
    return JokerSuitPlan.choose(jokers, view, facts);
  }

  /**
   * 콜을 포함하지 않는 일반 카드 중 해당 문양의 가장 높은 카드를 선택합니다.
   */
  public static Action highest(List<Action> actions) {
    return actions.stream()
        .max(Comparator.comparingInt(action -> ((StandardCard) action.card()).rank().value()))
        .orElseThrow();
  }

  /** 주공 본인의 낙찰 후 선언을 먼저 보고, 없으면 경매에서 마지막으로 부른 문양을 읽습니다. */
  public static Optional<Suit> announcedReturnSuit(View view) {
    var announced = PublicContractHistory.latestSuit(view.context().contractHistory());
    if (announced.isPresent()) {
      return announced;
    }
    var bids = view.context().auction();
    for (int i = bids.size() - 1; i >= 0; i--) {
      var entry = bids.get(i);
      if (entry.seat().equals(view.context().declarer())
          && entry.action() instanceof BidOffer offer) {
        var suit = PublicContractHistory.suitOf(offer.bid().trump());
        if (suit.isPresent()) {
          return suit;
        }
      }
    }
    return Optional.empty();
  }
}
