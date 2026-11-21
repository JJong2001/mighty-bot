package mighty.ai.play;

import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.Card;
import mighty.model.Joker;
import mighty.model.StandardCard;

/**
 * 점수 여부와 앞으로의 승리·부기루 전개 가치를 분리합니다.
 */
public final class CardResources {

  private final View view;
  private final PlayFacts facts;

  public CardResources(View view, PlayFacts facts) {
    this.view = view;
    this.facts = facts;
  }

  public boolean reserved(Card card) {
    if (!(card instanceof StandardCard standard)) {
      return true;
    }
    return card.equals(Rules.mighty(view.contract().trump()))
        || facts.isSuitTop(card)
        || mighty.ai.play.friend.FriendReturnReserve.protects(view, card)
        || mighty.ai.play.opposition.OppositionGuards.protectsDevelopment(view, facts,
        standard.suit())
        || primaryWinner(standard)
        || developmentRole(standard);
  }

  private boolean primaryWinner(StandardCard card) {
    if (view.role() != mighty.game.Role.DECLARER
        || view.contract().trump() != mighty.model.Trump.NO_TRUMP) {
      return false;
    }
    java.util.List<Card> original = new java.util.ArrayList<>(view.hand());
    facts.cards().publicHistory().completedPlays().stream()
        .filter(p -> p.seat().equals(view.self()))
        .forEach(p -> original.add(p.action().card()));
    var hand = mighty.ai.bidding.BiddingHand.from(original);
    var primary = java.util.Arrays.stream(mighty.model.Suit.values()).max(java.util.Comparator
        .<mighty.model.Suit>comparingInt(s -> hand.holding(s).size())
        .thenComparingInt(s -> -s.ordinal())).orElseThrow();
    return card.suit() == primary;
  }

  private boolean developmentRole(StandardCard card) {
    if (view.contract().trump().matches(card.suit())) {
      return false;
    }
    var plan = SuitDevelopment.analyze(view, facts, card.suit());
    // Q와 낮은 탐색 카드는 남기고 T 같은 중간 비탑 점수는 동료에게 지원할 수 있습니다.
    return plan.canDevelop() && (plan.held().get(0).equals(card) || plan.probingCard()
        .equals(card));
  }

  /**
   * 실제 운영 문양의 수비 장수는 다른 문양의 독립 탑카보다 먼저 보존합니다.
   */
  public int retentionPriority(Card card) {
    if (!(card instanceof StandardCard standard)
        || card.equals(Rules.mighty(view.contract().trump()))) {
      return 3;
    }
    if (mighty.ai.play.opposition.OppositionGuards.protectsDevelopment(view, facts,
        standard.suit())) {
      return 2;
    }
    return reserved(card) ? 1 : 0;
  }

  /**
   * 일반 물패를 먼저 버리되, 확인된 수비의 받침까지 점수보다 먼저 버리지는 않습니다.
   */
  public int discardTier(Card card) {
    int retention = retentionPriority(card);
    if (retention >= 2) {
      return retention;
    }
    return card.points() == 0 ? 0 : 1;
  }

  /**
   * 강제 따라내기 등 보존할 수 없는 경우에는 손실이 작은 자원부터 선택합니다.
   */
  public int preservationCost(Card card) {
    if (card.equals(Rules.mighty(view.contract().trump()))) {
      return 2100;
    }
    if (card instanceof Joker) {
      return 2000;
    }
    StandardCard standard = (StandardCard) card;
    return retentionPriority(card) * 500
        + (view.contract().trump().matches(standard.suit()) ? 100 : 0)
        + standard.rank().value();
  }

}
