package mighty.ai.planning;

import java.util.*;

import static mighty.ai.planning.NoTrumpOpeningPlan.Purpose.*;

import mighty.ai.analysis.HoldingControl;
import mighty.ai.bidding.BiddingHand;
import mighty.game.bidding.Bid;
import mighty.game.bidding.PublicContractHistory;
import mighty.game.deal.DeckFactory;
import mighty.game.friend.*;
import mighty.game.rule.Rules;
import mighty.model.*;

/**
 * 공개 공약과 자기 카드만으로 초구의 지원 목적과 낼 카드를 함께 결정합니다.
 */
public final class NoTrumpOpeningPlanner {

  private NoTrumpOpeningPlanner() {
  }

  public static NoTrumpOpeningPlan forCandidate(BiddingHand hand, Collection<? extends Card> buried,
      Card candidate, int points, List<Bid> history) {
    FriendSelection selection = hand.contains(candidate) ? SpecialFriend.FIRST_TRICK
        : new CardFriend(candidate);
    return plan(hand, buried, selection, points, history);
  }

  public static NoTrumpOpeningPlan plan(BiddingHand hand, Collection<? extends Card> buried,
      FriendSelection selection, int points, List<Bid> history) {
    List<Card> own = DeckFactory.create().stream().filter(hand::contains).toList();
    if (!hand.hasMighty(Trump.NO_TRUMP) && !hand.hasJoker() && hand.hasGun(Trump.NO_TRUMP)
        && selection instanceof CardFriend friend
        && friend.card().equals(Rules.mighty(Trump.NO_TRUMP))) {
      var control = NoTrumpControlPlan.analyze(own, buried);
      if (control.hasIndependentReturn()
          && control.primary() != Rules.mighty(Trump.NO_TRUMP).suit()) {
        var opening = own.stream().filter(StandardCard.class::isInstance)
            .map(StandardCard.class::cast).filter(card -> card.suit() == control.primary())
            .filter(card -> isTop(card, own, buried))
            .max(Comparator.comparingInt(card -> card.rank().value()));
        var returned = control.returnEntries().stream().sorted().findFirst();
        return new NoTrumpOpeningPlan(selection, opening, returned, buried.size() == 3,
            false, FORCE_JOKER);
      }
    }
    if (selection instanceof CardFriend called && called.card().equals(Rules.mighty(Trump.NO_TRUMP))
        && !hand.hasMighty(Trump.NO_TRUMP) && (hand.hasJoker() || !hand.hasGun(Trump.NO_TRUMP))) {
      var main = NoTrumpPlanner.withFriend(hand, buried, called.card()).primary();
      var holding = HoldingControl.analyze(own, List.copyOf(buried), Trump.NO_TRUMP, main);
      var control = NoTrumpControlPlan.analyze(own, buried, main);
      if (control.runBeforeSupport()) {
        var opening = own.stream().filter(StandardCard.class::isInstance)
            .map(StandardCard.class::cast).filter(card -> card.suit() == main)
            .max(Comparator.comparingInt(card -> card.rank().value()));
        return new NoTrumpOpeningPlan(selection, opening, Optional.empty(), buried.size() == 3,
            false, RUN_PRIMARY);
      }
      if (holding.length() >= 5) {
        boolean hasEntry = own.stream().filter(StandardCard.class::isInstance)
            .map(StandardCard.class::cast)
            .anyMatch(card -> card.suit() != main && isTop(card, own, buried));
        if (holding.topRun() == 0 && hasEntry) {
          var promotion = sideAce(hand, own, main).filter(card -> points == Bid.MAX_POINTS
              && nextRankAvailable(card, hand, buried));
          var request = lowest(hand, own.stream().filter(StandardCard.class::isInstance)
              .map(StandardCard.class::cast).filter(card -> card.suit() == main).toList());
          return new NoTrumpOpeningPlan(selection, promotion.or(() -> request), Optional.empty(),
              buried.size() == 3, false, promotion.isPresent() ? PROMOTE_PARTNER : REQUEST_FRIEND);
        }
      }
    }
    if (selection instanceof CardFriend called && called.card() instanceof Joker
        && hand.hasMighty(Trump.NO_TRUMP)) {
      var main = NoTrumpPlanner.withFriend(hand, buried, called.card()).primary();
      var opening = sideAce(hand, own, main);
      if (opening.isPresent()) {
        return new NoTrumpOpeningPlan(selection, opening, Optional.empty(), buried.size() == 3,
            false, SECURE_FIRST_TRICK);
      }
    }
    if (selection == SpecialFriend.FIRST_TRICK) {
      Optional<Suit> returned = points == 0 || points == Bid.MAX_POINTS
          ? PublicContractHistory.latestSuit(history)
          .or(() -> NoTrumpPlanner.openingFriendReturn(hand))
          : Optional.empty();
      // 풀노에서 앞으로 공개할 반환 문양도 같은 계획이 정합니다.
      if (points == Bid.MAX_POINTS && returned.isEmpty()) {
        returned = Optional.of(NoTrumpPlanner.signallingSuit(hand));
      }
      Optional<Suit> requestedReturn = returned;
      var offerings = own.stream().filter(StandardCard.class::isInstance)
          .map(StandardCard.class::cast).filter(card -> !card.equals(Rules.mighty(Trump.NO_TRUMP)))
          .filter(card -> !isTop(card, own, buried)).toList();
      var matching = offerings.stream().filter(card -> card.points() == 0)
          .filter(card -> requestedReturn.filter(card.suit()::equals).isPresent()).toList();
      return new NoTrumpOpeningPlan(selection,
          lowest(hand, matching.isEmpty() ? offerings : matching),
          returned, buried.size() == 3, false, OPENING_FRIEND);
    }
    if (selection instanceof CardFriend called && called.card() instanceof StandardCard target
        && !target.equals(Rules.mighty(Trump.NO_TRUMP)) && !hand.contains(target)) {
      var request = own.stream().filter(StandardCard.class::isInstance)
          .map(StandardCard.class::cast)
          .filter(
              card -> card.suit() == target.suit() && card.rank().value() < target.rank().value())
          .toList();
      var opening = lowest(hand, request);
      int retainedGain = NoTrumpPlanner.connectionGain(hand, target)
          - opening.map(Card::points).orElse(0);
      boolean otherNeedsSupport = Arrays.stream(Suit.values()).filter(suit -> suit != target.suit())
          .map(suit -> HoldingControl.analyze(own, List.copyOf(buried), Trump.NO_TRUMP, suit))
          .anyMatch(control -> control.length() > control.topRun());
      int length = hand.ordinaryHolding(target.suit(), Trump.NO_TRUMP).size();
      long outside = DeckFactory.create().stream().filter(StandardCard.class::isInstance)
          .map(StandardCard.class::cast).filter(card -> card.suit() == target.suit())
          .filter(card -> !card.equals(Rules.mighty(Trump.NO_TRUMP)))
          .filter(card -> !hand.contains(card) && !buried.contains(card)).count();
      int specials = (hand.hasMighty(Trump.NO_TRUMP) ? 1 : 0) + (hand.hasJoker() ? 1 : 0);
      boolean longPrimary =
          target.suit() == NoTrumpPlanner.withFriend(hand, buried, target).primary()
              && length + specials >= outside;
      boolean connection =
          opening.isPresent() && retainedGain >= 2 && otherNeedsSupport && !longPrimary;
      boolean mayPullFriend = own.stream().filter(StandardCard.class::isInstance)
          .map(StandardCard.class::cast).anyMatch(card -> card.suit() == target.suit()
              && !card.equals(Rules.mighty(Trump.NO_TRUMP)) && card.rank().value() > target.rank()
              .value());
      return new NoTrumpOpeningPlan(selection,
          connection || mayPullFriend ? opening : Optional.empty(),
          Optional.empty(), buried.size() == 3, connection, REQUEST_FRIEND);
    }
    return new NoTrumpOpeningPlan(selection, Optional.empty(), Optional.empty(), buried.size() == 3,
        false, DEFER);
  }

  private static Optional<StandardCard> sideAce(BiddingHand hand, List<Card> own, Suit main) {
    return lowest(hand, own.stream().filter(StandardCard.class::isInstance)
        .map(StandardCard.class::cast)
        .filter(card -> card.suit() != main && card.rank() == Rank.ACE)
        .filter(card -> !card.equals(Rules.mighty(Trump.NO_TRUMP))).toList());
  }

  private static boolean nextRankAvailable(StandardCard top, BiddingHand hand,
      Collection<? extends Card> buried) {
    return Arrays.stream(Rank.values()).filter(rank -> rank.value() < top.rank().value())
        .max(Comparator.comparingInt(Rank::value)).map(rank -> new StandardCard(top.suit(), rank))
        .filter(card -> !hand.contains(card) && !buried.contains(card)).isPresent();
  }

  private static Optional<StandardCard> lowest(BiddingHand hand, List<StandardCard> cards) {
    return cards.stream().min(Comparator.comparingInt(StandardCard::points)
        .thenComparingInt(card -> hand.holding(card.suit()).size())
        .thenComparingInt(card -> card.rank().value()).thenComparing(Card::toCode));
  }

  private static boolean isTop(StandardCard card, List<Card> own,
      Collection<? extends Card> buried) {
    var control = HoldingControl.analyze(own, List.copyOf(buried), Trump.NO_TRUMP, card.suit());
    return own.stream().filter(StandardCard.class::isInstance).map(StandardCard.class::cast)
        .filter(held -> held.suit() == card.suit() && !held.equals(Rules.mighty(Trump.NO_TRUMP)))
        .filter(held -> held.rank().value() >= card.rank().value()).count() <= control.topRun();
  }
}
