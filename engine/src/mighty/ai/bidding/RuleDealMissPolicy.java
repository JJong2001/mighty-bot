package mighty.ai.bidding;

import java.util.Arrays;
import mighty.game.bidding.DealMissRules;
import mighty.game.deal.DealFormat;
import mighty.game.observation.BiddingView;
import mighty.model.*;

/**
 * 선언 자격은 게임 규칙에 맡기고, 오마·육마의 패 유지 기준만 판단합니다.
 */
public final class RuleDealMissPolicy {

  public boolean shouldDeclare(BiddingView view) {
    if (!DealMissRules.canDeclare(view.bidding(), view.hand(), view.playerScore())) {
      return false;
    }
    BiddingHand hand = BiddingHand.from(view.hand());
    boolean hasMighty = hand.hasMighty(Trump.NO_TRUMP);
    boolean joker = hand.hasJoker();
    if (view.mode() == mighty.game.GameMode.SIX_PLAYER) {
      return !hasMighty && !joker;
    }
    if (hasMighty && joker) {
      return false;
    }
    boolean fiveCards = Arrays.stream(Suit.values()).anyMatch(s -> hand.holding(s).size() >= 5);
    if (hasMighty && !joker) {
      boolean onlyMighty = view.hand().stream().noneMatch(
          c -> c.points() > 0 && !c.equals(mighty.game.rule.Rules.mighty(Trump.NO_TRUMP)));
      return !onlyMighty || (hand.holding(Suit.SPADE).size() < 3 && !fiveCards);
    }
    if (joker) {
      long points = view.hand().stream().filter(c -> c.points() > 0).count();
      boolean supportedAce = Arrays.stream(Suit.values()).anyMatch(s ->
          hand.ordinaryHolding(s, Trump.NO_TRUMP).contains(Rank.ACE)
              && hand.holding(s).size() >= 3);
      boolean fourCardSuit = Arrays.stream(Suit.values())
          .anyMatch(suit -> hand.holding(suit).size() >= 4);
      // 점수 한 장과 4장 문양이 있으면 조커로 비딩과 수비 모두 할 수 있습니다.
      return points < 2 && !supportedAce && !(points > 0 && fourCardSuit);
    }
    return true;
  }

}
