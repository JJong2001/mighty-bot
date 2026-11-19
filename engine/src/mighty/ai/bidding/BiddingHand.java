package mighty.ai.bidding;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import mighty.game.rule.Rules;
import mighty.model.Card;
import mighty.model.Joker;
import mighty.model.Rank;
import mighty.model.StandardCard;
import mighty.model.Suit;
import mighty.model.Trump;

public final class BiddingHand {

  private final Map<Suit, SuitHolding> holdings;
  private final boolean hasJoker;

  private BiddingHand(
      Map<Suit, SuitHolding> holdings,
      boolean hasJoker
  ) {
    this.holdings = Map.copyOf(holdings);
    this.hasJoker = hasJoker;
  }

  public static BiddingHand from(List<Card> hand) {
    List<Card> cards = List.copyOf(hand);
    if (new HashSet<>(cards).size() != cards.size()) {
      throw new IllegalArgumentException(
          "손패에 중복된 카드가 있습니다."
      );
    }
    List<StandardCard> standardCards = cards.stream()
        .filter(StandardCard.class::isInstance)
        .map(StandardCard.class::cast)
        .toList();
    Map<Suit, SuitHolding> holdings = new EnumMap<>(Suit.class);
    for (Suit suit : Suit.values()) {
      var ranks = standardCards.stream()
          .filter(card -> card.suit() == suit)
          .map(StandardCard::rank)
          .toList();
      holdings.put(suit, new SuitHolding(suit, ranks));
    }
    return new BiddingHand(
        holdings,
        cards.contains(Joker.INSTANCE)
    );
  }

  public SuitHolding holding(Suit suit) {
    Objects.requireNonNull(suit, "조회할 문양이 필요합니다.");
    return holdings.get(suit);
  }

  public boolean hasJoker() {
    return hasJoker;
  }

  /**
   * 지정한 카드를 자기 손패에 가지고 있는지 확인합니다.
   */
  public boolean contains(Card card) {
    Objects.requireNonNull(card, "확인할 카드가 필요합니다.");
    if (card instanceof Joker) {
      return hasJoker;
    }
    StandardCard standard = (StandardCard) card;
    return holding(standard.suit()).contains(standard.rank());
  }

  /**
   * 후보 기루에서 마이티를 보유했는지 확인합니다.
   */
  public boolean hasMighty(Trump trump) {
    return contains(Rules.mighty(trump));
  }

  /**
   * 후보 기루에서 조커콜 카드를 보유했는지 확인합니다.
   */
  public boolean hasGun(Trump trump) {
    return contains(Rules.gun(trump));
  }

  /**
   * 후보 기루에 해당하는 카드의 보유 장수를 반환합니다. 노기루 후보에서는 0을 반환합니다.
   */
  public int trumpCount(Trump trump) {
    Objects.requireNonNull(trump, "후보 기루가 필요합니다.");
    for (Suit suit : Suit.values()) {
      if (trump.matches(suit)) {
        return holding(suit).size();
      }
    }
    return 0;
  }

  /**
   * 후보 기루의 마이티를 제외한 문양별 평가 정보를 반환합니다. 실제 손패의 문양 장수는 holding()으로 조회해야 합니다.
   */
  public SuitHolding ordinaryHolding(Suit suit, Trump trump) {
    SuitHolding original = holding(suit);
    StandardCard mighty = Rules.mighty(trump);
    if (mighty.suit() != suit) {
      return original;
    }
    return new SuitHolding(
        suit,
        original.ranks().stream()
            .filter(rank -> rank != mighty.rank())
            .toList()
    );
  }

  /**
   * 가장 높은 일반 카드부터 연속 보유한 장수를 반환합니다. 마이티인 A를 제외하는 문양에서는 K부터 확인합니다.
   */
  public int ordinaryTopRunLength(Suit suit, Trump trump) {
    SuitHolding ordinary = ordinaryHolding(suit, trump);
    StandardCard mighty = Rules.mighty(trump);
    Rank highestOrdinaryRank = mighty.suit() == suit
        ? Rank.KING
        : Rank.ACE;
    return ordinary.consecutiveFrom(highestOrdinaryRank);
  }

  /**
   * AKQJ 여섯 장 이상과 마이티로 기루를 자력 장악할 수 있는 핵심 구조입니다.
   */
  public boolean hasSolidTrumpCore(Trump trump) {
    return hasMighty(trump) && java.util.Arrays.stream(Suit.values()).filter(trump::matches)
        .anyMatch(s -> holding(s).size() >= 6 && ordinaryTopRunLength(s, trump) >= 4);
  }

}
