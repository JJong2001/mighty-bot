package mighty.ai.planning;

import java.util.*;
import mighty.ai.bidding.BiddingHand;
import mighty.game.rule.Rules;
import mighty.game.deal.DeckFactory;
import mighty.model.*;

/**
 * 자기 패의 주력·지원 카드·호출 시점을 구성합니다. 확률 판정은 distribution 패키지에만 두며 카드별 위험 합산은 하지 않습니다.
 */
public final class NoTrumpPlanner {

  private NoTrumpPlanner() {
  }

  public static NoTrumpPlan analyze(BiddingHand hand) {
    return analyze(hand, List.of());
  }

  public static NoTrumpPlan analyze(BiddingHand hand, Collection<? extends Card> excluded) {
    Card mighty = Rules.mighty(Trump.NO_TRUMP);
    Card friend = mighty;
    if (hand.contains(mighty)) {
      Optional<StandardCard> completion = completion(hand, excluded);
      int minimumGain = hand.hasJoker() ? 1 : 3;
      if (completion.isPresent() && connectionGain(hand, completion.get()) >= minimumGain) {
        friend = completion.get();
      } else if (!hand.hasJoker() && !excluded.contains(Joker.INSTANCE)) {
        friend = Joker.INSTANCE;
      }
    }
    if (excluded.contains(friend)) {
      return new NoTrumpPlan(false, primary(hand), friend, 0, 0);
    }
    return withFriend(hand, excluded, friend);
  }

  /**
   * 같은 운영 자료를 버리기·프렌드·플레이에서 재사용합니다. 분포 성공률은 별도 계산합니다.
   */
  public static NoTrumpPlan withFriend(BiddingHand hand, Collection<? extends Card> excluded,
      Card friend) {
    Suit primary = primary(hand);
    boolean mighty = hand.hasMighty(Trump.NO_TRUMP), joker = hand.hasJoker();
    boolean gun = hand.hasGun(Trump.NO_TRUMP);
    int ownTops = Arrays.stream(Suit.values())
        .mapToInt(s -> hand.ordinaryTopRunLength(s, Trump.NO_TRUMP)).sum();
    int ownMainRun = hand.ordinaryTopRunLength(primary, Trump.NO_TRUMP);
    int mainLength = hand.ordinaryHolding(primary, Trump.NO_TRUMP).size();
    int before = ownTops + (mighty ? 1 : 0) + (joker ? 1 : 0);
    if (mainLength >= 6 && ownMainRun >= 3) {
      before += mainLength - ownMainRun;
    }
    int breaks = !joker && !(friend instanceof Joker) && !gun ? 1 : 0;
    boolean available =
        (mighty || joker || gun) && (ownTops > 0 || mighty && joker && mainLength >= 6);
    if (!mighty && !joker && ownMainRun == 0) {
      available = false;
    }
    if (!mighty && !joker && gun) {
      List<Card> own = DeckFactory.create().stream().filter(hand::contains).toList();
      available &= NoTrumpControlPlan.analyze(own, excluded, primary).hasIndependentReturn();
    }
    return new NoTrumpPlan(available && !excluded.contains(friend), primary, friend, breaks,
        before);
  }

  private static Optional<StandardCard> completion(BiddingHand hand,
      Collection<? extends Card> excluded) {
    List<StandardCard> candidates = new ArrayList<>();
    for (Suit suit : Suit.values()) {
      Arrays.stream(Rank.values()).sorted(Comparator.comparingInt(Rank::value).reversed())
          .map(rank -> new StandardCard(suit, rank))
          .filter(c -> !c.equals(Rules.mighty(Trump.NO_TRUMP)) && !hand.contains(c)
              && !excluded.contains(c))
          .findFirst().ifPresent(candidates::add);
    }
    return candidates.stream()
        .max(Comparator.<StandardCard>comparingInt(c -> connectionGain(hand, c))
            .thenComparingInt(c -> hand.holding(c.suit()).size())
            .thenComparingInt(c -> -c.suit().ordinal()));
  }

  /**
   * 상위 빈자리는 후보로만 올리고, 배분 평가에서 자력 운영·다른 지원과 비교합니다.
   */
  public static List<Card> supportCandidates(BiddingHand hand,
      Collection<? extends Card> excluded) {
    Card preferred = analyze(hand, excluded).friend();
    if (!hand.hasMighty(Trump.NO_TRUMP)) {
      return List.of(preferred);
    }
    Set<Card> candidates = new LinkedHashSet<>();
    candidates.add(preferred);
    for (Suit suit : Suit.values()) {
      missingTop(hand, excluded, suit).filter(card ->
              connectionGain(hand, card) >= 2 || hand.hasJoker()
                  && hand.hasMighty(Trump.NO_TRUMP)
                  && hand.ordinaryHolding(suit, Trump.NO_TRUMP).size() >= 5
                  || hand.hasJoker() && connectionGain(hand, card) == 1
                  && preferred instanceof StandardCard ordinary && !hand.contains(ordinary)
                  && connectionGain(hand, ordinary) == 1)
          .ifPresent(candidates::add);
    }
    boolean longSelfRun = hand.hasJoker() && preferred instanceof StandardCard card
        && hand.ordinaryHolding(card.suit(), Trump.NO_TRUMP).size() >= 5;
    // 조커를 따로 처리할 수 없는 짧은 연결은 기존 지정 지원을 후보에 남깁니다.
    if (!hand.hasJoker() && !hand.hasGun(Trump.NO_TRUMP)
        && preferred instanceof StandardCard card && !hand.contains(card)
        && connectionGain(hand, card) >= 3
        && hand.ordinaryHolding(card.suit(), Trump.NO_TRUMP).size() < 6) {
      return List.of(preferred);
    }
    if (!hand.hasJoker() && !excluded.contains(Joker.INSTANCE)) {
      candidates.add(Joker.INSTANCE);
    }
    if ((hand.hasJoker() || hand.hasGun(Trump.NO_TRUMP))
        && (candidates.size() > 1 || splitControl(hand, preferred) || longSelfRun)) {
      candidates.add(Rules.mighty(Trump.NO_TRUMP));
      // 자기 마이티는 초구프 후보를 나타냅니다.
    }
    return List.copyOf(candidates);
  }

  public static Optional<StandardCard> missingTop(BiddingHand hand,
      Collection<? extends Card> excluded,
      Suit suit) {
    return Arrays.stream(Rank.values()).sorted(Comparator.comparingInt(Rank::value).reversed())
        .map(rank -> new StandardCard(suit, rank))
        .filter(card -> !card.equals(Rules.mighty(Trump.NO_TRUMP))
            && !hand.contains(card) && !excluded.contains(card)).findFirst();
  }

  /**
   * 두 문양을 나눠 맡을 수 있는지 검사합니다.
   */
  public static boolean splitControl(BiddingHand hand, Card friend) {
    return hand.hasMighty(Trump.NO_TRUMP) && hand.hasJoker()
        && friend instanceof StandardCard target && !hand.contains(target)
        && connectionGain(hand, target) >= 2
        && Arrays.stream(Suit.values()).anyMatch(suit -> suit != target.suit()
        && hand.ordinaryHolding(suit, Trump.NO_TRUMP).size() >= 4
        && hand.ordinaryTopRunLength(suit, Trump.NO_TRUMP) > 0);
  }

  /**
   * 특정 KQ 이름 대신 지정 카드 추가로 이어지는 일반 탑카의 증가량을 셉니다.
   */
  public static int connectionGain(BiddingHand hand, StandardCard card) {
    List<Card> augmented = new ArrayList<>();
    for (Suit suit : Suit.values()) {
      for (Rank rank : hand.holding(suit).ranks()) {
        augmented.add(new StandardCard(suit,
            rank));
      }
    }
    if (hand.hasJoker()) {
      augmented.add(Joker.INSTANCE);
    }
    augmented.add(card);
    return BiddingHand.from(augmented).ordinaryTopRunLength(card.suit(), Trump.NO_TRUMP)
        - hand.ordinaryTopRunLength(card.suit(), Trump.NO_TRUMP);
  }

  /**
   * 스스로 여러 바퀴 돌릴 주력과 특수카드를 써야 하는 반환 문양을 구분합니다.
   */
  public static Optional<Suit> openingFriendReturn(BiddingHand hand) {
    if (!hand.hasMighty(Trump.NO_TRUMP) || !hand.hasJoker()) {
      return Optional.empty();
    }
    Suit main = signallingSuit(hand);
    int mainRun = hand.ordinaryTopRunLength(main, Trump.NO_TRUMP);
    if (mainRun < 3 || hand.ordinaryHolding(main, Trump.NO_TRUMP).size() <= mainRun) {
      return Optional.empty();
    }
    return Arrays.stream(Suit.values()).filter(suit -> suit != main)
        .filter(suit -> hand.ordinaryTopRunLength(suit, Trump.NO_TRUMP) == 1)
        .filter(suit -> hand.ordinaryHolding(suit, Trump.NO_TRUMP).ranks().stream()
            .filter(rank -> rank.value() < Rank.TEN.value()).count() >= 2)
        .max(Comparator.comparingInt(suit -> hand.ordinaryHolding(suit, Trump.NO_TRUMP).size()));
  }

  public static Suit signallingSuit(BiddingHand hand) {
    Suit connected = Arrays.stream(Suit.values()).max(Comparator.<Suit>comparingInt(s ->
            hand.ordinaryTopRunLength(s, Trump.NO_TRUMP)).thenComparingInt(s -> hand.holding(s).size()))
        .orElseThrow();
    return hand.ordinaryTopRunLength(connected, Trump.NO_TRUMP) >= 3 ? connected : primary(hand);
  }

  /**
   * 닷노 입찰과 실제 운영이 같은 주력 문양과 상위 한 장의 빈자리를 보도록 합니다.
   */
  public static Optional<Suit> jokerSweepSuit(BiddingHand hand) {
    if (!hand.hasJoker() || hand.hasMighty(Trump.NO_TRUMP)) {
      return Optional.empty();
    }
    Suit suit = primary(hand);
    var ranks = hand.ordinaryHolding(suit, Trump.NO_TRUMP).ranks();
    int run = hand.ordinaryTopRunLength(suit, Trump.NO_TRUMP);
    if (ranks.size() < 4 || run < 2 || run >= ranks.size()
        || ranks.get(run).value() < Rank.TEN.value()
        || ranks.get(run - 1).value() - ranks.get(run).value() != 2) {
      return Optional.empty();
    }
    return Optional.of(suit);
  }

  private static Suit primary(BiddingHand hand) {
    return Arrays.stream(Suit.values())
        .max(Comparator.<Suit>comparingInt(s -> hand.holding(s).size())
            .thenComparingInt(s -> hand.ordinaryTopRunLength(s, Trump.NO_TRUMP))
            .thenComparingInt(s -> -s.ordinal())).orElseThrow();
  }

}
