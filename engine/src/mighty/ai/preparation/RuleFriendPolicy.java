package mighty.ai.preparation;

import java.util.Objects;
import mighty.ai.bidding.BiddingHand;
import mighty.ai.planning.NoTrumpPlanner;
import mighty.ai.planning.SuitFriendPlan;
import mighty.ai.planning.distribution.NoTrumpDistributionEvaluator;
import mighty.game.friend.CardFriend;
import mighty.game.friend.FriendSelection;
import mighty.game.friend.SpecialFriend;
import mighty.game.observation.FriendView;
import mighty.game.rule.Rules;
import mighty.model.Card;
import mighty.model.Joker;
import mighty.model.Trump;

/**
 * 기루의 마이티 확보와 노기루 공통 운영 계획의 지원 카드를 구분합니다.
 */
public final class RuleFriendPolicy implements FriendPolicy {

  @Override
  public FriendSelection choose(FriendView view) {
    Objects.requireNonNull(view);
    Card mighty = Rules.mighty(view.contract().trump());
    if (!owns(view, mighty)) {
      return new CardFriend(mighty);
    }
    BiddingHand hand = BiddingHand.from(view.hand());
    if (view.contract().trump() == Trump.NO_TRUMP) {
      if ((hand.hasJoker() || hand.hasGun(Trump.NO_TRUMP)
          || hand.ordinaryTopRunLength(NoTrumpPlanner.signallingSuit(hand),
          Trump.NO_TRUMP) >= 5) && view.contract().points() >= 18
          && NoTrumpDistributionEvaluator.soloCeiling(NoTrumpDistributionEvaluator.evaluateSolo(
          hand, view.discardedCards())) >= view.contract().points()) {
        return SpecialFriend.NONE;
      }
      var plan = NoTrumpPlanner.analyze(hand, view.discardedCards());
      if (plan.available()) {
        var support = NoTrumpDistributionEvaluator.bestSupport(hand, view.discardedCards(),
            view.contract().points());
        // 낮은 공약에서는 분포 모델이 경로를 충분히 찾지 못한 경우 기존 운영 근거를 유지합니다.
        Card selected = view.contract().points() < 16 && support.distribution().sixteenRate() < 0.50
            ? plan.friend() : support.friend();
        if (!owns(view, selected)) {
          return new CardFriend(selected);
        }
        if (hand.hasJoker()) {
          return SpecialFriend.FIRST_TRICK;
        }
      }
    }
    var side = SuitFriendPlan.choose(hand, view.discardedCards(), view.contract().trump());
    if (side.isPresent()) {
      return side.get().selection();
    }
    if (!owns(view, Joker.INSTANCE)) {
      return new CardFriend(Joker.INSTANCE);
    }
    return SpecialFriend.FIRST_TRICK;
  }

  /**
   * 자기 손패와 자기 버린 카드만 확인하며 실제 상대 소유자는 조회하지 않습니다.
   */
  private static boolean owns(FriendView view, Card card) {
    return view.hand().contains(card) || view.discardedCards().contains(card);
  }

}
