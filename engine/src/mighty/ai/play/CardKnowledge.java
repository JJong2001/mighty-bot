package mighty.ai.play;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import mighty.game.Seat;
import mighty.game.deal.DeckFactory;
import mighty.game.observation.DeclarerInfo;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.game.trick.PublicPlay;
import mighty.model.Card;
import mighty.model.StandardCard;
import mighty.model.Suit;

/**
 * 자기 패와 공개된 카드만으로 남은 외부 카드와 확정 무문양을 계산합니다.
 */
public final class CardKnowledge {

  private final View view;
  private final List<Card> outside;
  private final java.util.Map<Suit, List<StandardCard>> outsideBySuit;
  private final java.util.Map<Seat, Set<Suit>> shownVoids = new java.util.HashMap<>();
  private final mighty.game.observation.PublicCardHistory publicHistory;
  private final TrumpOwnership trumps;

  public CardKnowledge(View view) {
    this.view = java.util.Objects.requireNonNull(view);
    publicHistory = view.publicCardHistory();
    Set<Card> cards = new HashSet<>(view.hand());
    cards.addAll(publicHistory.cards());
    if (view.privateInfo() instanceof DeclarerInfo declarer) {
      cards.addAll(declarer.discardedCards());
    }
    Set<Card> known = Set.copyOf(cards);
    outside = DeckFactory.create().stream().filter(card -> !known.contains(card)).toList();
    outsideBySuit = new java.util.EnumMap<>(Suit.class);
    for (Suit suit : Suit.values()) {
      outsideBySuit.put(suit, outside.stream().filter(StandardCard.class::isInstance)
          .map(StandardCard.class::cast).filter(card -> card.suit() == suit).toList());
    }
    for (var completed : view.context().history()) {
      observeVoids(completed.cards(), completed.number());
    }
    observeVoids(view.trick(), view.trickNumber());
    trumps = new TrumpOwnership(view, this);
  }

  /**
   * 이번 관측에서 한 번 구성한 공통 공개 카드 조회를 공유합니다.
   */
  public mighty.game.observation.PublicCardHistory publicHistory() {
    return publicHistory;
  }

  /**
   * 모든 역할이 같은 공개 증거로 읽는 잔여 기루 추론입니다.
   */
  public TrumpOwnership trumps() {
    return trumps;
  }

  /**
   * 위치가 확인되지 않은 카드입니다. 외부 카드가 곧 야당 카드라는 뜻은 아닙니다.
   */
  public List<Card> outsideCards() {
    return outside;
  }

  /**
   * 지정 카드의 보유자는 프렌드이므로 야당의 일반 공격 카드로 가정하지 않습니다.
   */
  public boolean isDesignatedFriendCard(Card card) {
    return view.friendSelection() instanceof mighty.game.friend.CardFriend friend && friend.card()
        .equals(card);
  }

  /**
   * 지정 문양의 미확인 외부 장수를 반환합니다.
   */
  public long outsideCount(Suit suit) {
    return outsideCards(suit).size();
  }

  public List<StandardCard> outsideCards(Suit suit) {
    return outsideBySuit.get(suit);
  }

  /**
   * 일반 카드로 문양을 따르지 못한 공개 행동만 사용합니다.
   */
  public boolean isVoid(Seat seat, Suit suit) {
    return shownVoids.getOrDefault(seat, Set.of()).contains(suit);
  }

  private void observeVoids(List<PublicPlay> plays, mighty.game.TrickNumber number) {
    if (plays.size() < 2) {
      return;
    }
    var trump = view.contract().trump();
    var lead = Rules.conditionsFor(trump, number, plays.get(0).action()).leadSuit();
    var mighty = Rules.mighty(trump);
    for (var play : plays.subList(1, plays.size())) {
      if (play.action().card() instanceof StandardCard card
          && !card.equals(mighty) && card.suit() != lead) {
        shownVoids.computeIfAbsent(play.seat(), ignored -> java.util.EnumSet.noneOf(Suit.class))
            .add(lead);
      }
    }
  }

}
