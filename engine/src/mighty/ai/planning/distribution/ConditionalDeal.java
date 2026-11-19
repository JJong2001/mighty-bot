package mighty.ai.planning.distribution;

import java.util.*;
import mighty.game.Seat;
import mighty.game.deal.DeckFactory;
import mighty.game.friend.*;
import mighty.model.Card;

/**
 * 실제 상대 패를 받지 않고 지정 카드의 소유 조건을 만족하는 가상 배분만 만듭니다.
 */
record ConditionalDeal(Map<Seat, List<Card>> hands, List<Card> buried, int friendIndex) {

  ConditionalDeal {
    Map<Seat, List<Card>> copy = new LinkedHashMap<>();
    hands.forEach((seat, hand) -> copy.put(seat, List.copyOf(hand)));
    hands = Map.copyOf(copy);
    buried = List.copyOf(buried);
  }

  static ConditionalDeal sample(List<Card> own, Collection<? extends Card> excluded,
      FriendSelection selection, int index, Random random) {
    int partner = 1 + index % 4;
    List<Card> fixed = selection instanceof CardFriend friend ? List.of(friend.card()) : List.of();
    List<Card> pool = new ArrayList<>(DeckFactory.create().stream()
        .filter(c -> !own.contains(c) && !excluded.contains(c) && !fixed.contains(c)).toList());
    Collections.shuffle(pool, random);
    List<Card> buried = new ArrayList<>(excluded);
    while (buried.size() < 3) {
      buried.add(pool.remove(pool.size() - 1));
    }
    Map<Seat, List<Card>> hands = new LinkedHashMap<>();
    hands.put(new Seat(0), own);
    int offset = 0;
    for (int seat = 1; seat < 5; seat++) {
      List<Card> hand = new ArrayList<>();
      if (seat == partner) {
        hand.addAll(fixed);
      }
      int take = 10 - hand.size();
      hand.addAll(pool.subList(offset, offset + take));
      offset += take;
      hands.put(new Seat(seat), hand);
    }
    return new ConditionalDeal(hands, buried, selection instanceof CardFriend ? partner : -1);
  }

  /**
   * 초구프로 연결한 배분은 초구에 소비한 카드까지 제외한 뒤 나머지 운영을 평가합니다.
   */
  ConditionalDeal afterOpening() {
    return afterOpening(Optional.empty());
  }

  ConditionalDeal afterOpening(Optional<mighty.model.Suit> requested) {
    var own = hands.get(new Seat(0));
    var mightyCard = mighty.game.rule.Rules.mighty(mighty.model.Trump.NO_TRUMP);
    var lead = own.stream().filter(mighty.model.StandardCard.class::isInstance)
        .map(mighty.model.StandardCard.class::cast)
        .filter(card -> !card.equals(mightyCard))
        .filter(card -> requested.isEmpty() || requested.get() == card.suit())
        .min(Comparator.<mighty.model.StandardCard>comparingInt(Card::points)
            .thenComparingLong(card -> own.stream().filter(held ->
                held instanceof mighty.model.StandardCard standard
                    && standard.suit() == card.suit()).count())
            .thenComparingInt(card -> card.rank().value())
            .thenComparing(Card::toCode));
    if (lead.isEmpty()) {
      return this;
    }
    int winner = 0;
    Card winningCard = lead.get();
    var conditions = new mighty.game.rule.TrickConditions(mighty.model.Trump.NO_TRUMP,
        lead.get().suit(),
        new mighty.game.TrickNumber(1), false);
    for (int index = 1; index < 5; index++) {
      for (Card card : hands.get(new Seat(index))) {
        if (mighty.game.rule.Rules.strength(card, conditions) > mighty.game.rule.Rules.strength(
            winningCard, conditions)) {
          winningCard = card;
          winner = index;
        }
      }
    }
    Map<Seat, List<Card>> remaining = new LinkedHashMap<>();
    for (int index = 0; index < 5; index++) {
      Seat seat = new Seat(index);
      var held = new ArrayList<>(hands.get(seat));
      if (index == 0) {
        held.remove(lead.get());
      } else if (index == winner) {
        held.remove(winningCard);
      } else {
        var following = held.stream()
            .filter(card -> card instanceof mighty.model.StandardCard standard
                && standard.suit() == lead.get().suit() && !card.equals(mightyCard)).toList();
        var choices = following.isEmpty() ? held : following;
        choices.stream().min(Comparator.comparingInt((Card card) -> card.equals(mightyCard) ? 2
                : card instanceof mighty.model.Joker ? 1 : 0).thenComparingInt(Card::points)
            .thenComparing(Card::toCode)).ifPresent(held::remove);
      }
      remaining.put(seat, held);
    }
    return new ConditionalDeal(remaining, buried, winner == 0 ? -1 : winner);
  }

}
