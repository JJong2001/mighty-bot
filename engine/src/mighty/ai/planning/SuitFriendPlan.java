package mighty.ai.planning;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import mighty.ai.analysis.HoldingControl;
import mighty.ai.bidding.BiddingHand;
import mighty.game.friend.CardFriend;
import mighty.game.friend.FriendSelection;
import mighty.game.friend.SpecialFriend;
import mighty.game.rule.Rules;
import mighty.model.Card;
import mighty.model.StandardCard;
import mighty.model.Suit;
import mighty.model.Trump;

/**
 * 조커를 총으로 처리할 수 있을 때 부기루에서 얻을 지원을 조커프와 비교합니다.
 */
public record SuitFriendPlan(FriendSelection selection, Suit openingSuit, int connectedWinners) {

  public static Optional<SuitFriendPlan> choose(
      BiddingHand hand, Collection<? extends Card> buried, Trump trump
  ) {
    if (trump == Trump.NO_TRUMP || !hand.hasMighty(trump) || hand.hasJoker() || !hand.hasGun(
        trump)) {
      return Optional.empty();
    }
    List<Card> owned = new ArrayList<>();
    for (Suit suit : Suit.values()) {
      for (var rank : hand.holding(suit).ranks()) {
        owned.add(new StandardCard(suit, rank));
      }
    }
    List<SuitFriendPlan> candidates = new ArrayList<>();
    for (Suit suit : Suit.values()) {
      if (trump.matches(suit)) {
        continue;
      }
      var cards = owned.stream().map(StandardCard.class::cast)
          .filter(card -> card.suit() == suit && !card.equals(Rules.mighty(trump))).toList();
      if (cards.size() < 3 || cards.stream().noneMatch(card -> card.points() == 0
          && !card.equals(Rules.gun(trump)))) {
        continue;
      }
      var control = HoldingControl.analyze(owned, List.copyOf(buried), trump, suit);
      if (control.blockers() == 0 || control.connected() < 2) {
        continue;
      }
      // 조커프는 한 구를 보탭니다. 지원 뒤 두 장 이상 이어지는 부기루를 비교합니다.
      if (control.blockers() == 1) {
        highestMissing(owned, buried, trump, suit).ifPresent(card ->
            candidates.add(new SuitFriendPlan(new CardFriend(card), suit, control.connected())));
      }
      candidates.add(new SuitFriendPlan(SpecialFriend.FIRST_TRICK, suit,
          control.connected() - Math.max(0, control.blockers() - 1)));
    }
    return candidates.stream().filter(plan -> plan.connectedWinners() >= 2)
        .max(Comparator.comparingInt(SuitFriendPlan::connectedWinners)
            .thenComparingInt(plan -> plan.selection() instanceof CardFriend ? 1 : 0));
  }

  private static Optional<StandardCard> highestMissing(
      List<Card> own, Collection<? extends Card> buried, Trump trump, Suit suit
  ) {
    return java.util.Arrays.stream(mighty.model.Rank.values())
        .map(rank -> new StandardCard(suit, rank))
        .filter(card -> !own.contains(card) && !buried.contains(card) && !card.equals(
            Rules.mighty(trump)))
        .max(Comparator.comparingInt(card -> card.rank().value()));
  }

}
