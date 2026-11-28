package mighty.ai.play;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import mighty.game.Seat;
import mighty.game.action.Action;
import mighty.game.observation.View;
import mighty.game.rule.LegalActionContext;
import mighty.game.rule.LegalActions;
import mighty.game.rule.Rules;
import mighty.game.rule.TrickConditions;
import mighty.game.trick.PublicPlay;
import mighty.model.Card;
import mighty.model.StandardCard;

/**
 * 하나의 관측에서 공통 판단에 필요한 공개 사실을 계산합니다.
 */
public final class PlayFacts {

  private final View view;
  private final CardKnowledge cards;
  private final CardLocationInference locations;
  private final TeamKnowledge teams;
  private final PublicBehaviorInference behavior;
  private final JokerBelief joker;
  private final List<Action> legal;
  private final Optional<TrickConditions> conditions;
  private final Optional<PublicPlay> winner;
  private final List<Action> winning;
  private final List<Seat> remaining;
  private final int trickPoints;
  private final DeclarerSuitInference declarerSuits;

  public PlayFacts(View view) {
    this.view = java.util.Objects.requireNonNull(view);
    cards = new CardKnowledge(view);
    behavior = new PublicBehaviorInference(view);
    locations = new CardLocationInference(view, cards, behavior);
    teams = new TeamKnowledge(view, behavior);
    joker = new JokerBelief(view, teams, behavior, locations);
    legal = LegalActions.legalActions(new LegalActionContext(view.hand(), view.contract().trump(),
        view.trickNumber(), view.trick()));
    conditions = view.trick().stream().findFirst().map(play ->
        Rules.conditionsFor(view.contract().trump(), view.trickNumber(), play.action()));
    winner = Rules.currentWinner(view.contract().trump(), view.trickNumber(), view.trick());
    winning = calculateWinningActions();
    remaining = calculateRemainingSeats();
    trickPoints = view.trick().stream().mapToInt(play -> play.action().card().points()).sum();
    declarerSuits = new DeclarerSuitInference(view, cards);
  }

  /**
   * 이번 관측에서 계산한 카드 카운팅을 반환합니다.
   */
  public PublicBehaviorInference behavior() {
    return behavior;
  }

  public JokerBelief joker() {
    return joker;
  }

  public CardKnowledge cards() {
    return cards;
  }

  public CardLocationInference locations() {
    return locations;
  }

  /**
   * 역할마다 같은 공개 증거를 읽고, 지원·수비 판단은 각 역할에 맡깁니다.
   */
  public DeclarerSuitInference declarerSuits() {
    return declarerSuits;
  }

  /**
   * 확실한 동료 관계를 판정하는 정보를 반환합니다.
   */
  public TeamKnowledge teams() {
    return teams;
  }

  /**
   * 이번 차례의 변경 불가능한 합법 행동 목록을 반환합니다.
   */
  public List<Action> legalActions() {
    return legal;
  }

  /**
   * 현재까지 제출된 카드의 승자이며, 이후 자리의 역전 가능성과는 구분합니다.
   */
  public Optional<PublicPlay> currentWinner() {
    return winner;
  }

  /**
   * 현재까지 확실한 동료가 이기고 있는지 확인합니다.
   */
  public boolean allyWinning() {
    return currentWinner().filter(play -> teams.isAlly(play.seat())).isPresent();
  }

  /**
   * 현재 선의 문양과 콜 여부를 반환합니다.
   */
  public TrickConditions conditions() {
    if (view.trick().isEmpty()) {
      throw new IllegalStateException("아직 선의 카드가 없습니다.");
    }
    return conditions.orElseThrow();
  }

  /**
   * 현재 선두를 이기는 합법 행동만 반환합니다.
   */
  public List<Action> winningActions() {
    return winning;
  }

  /**
   * 후보의 순서를 유지하며 현재 선두를 이기지 않는 행동만 고릅니다.
   */
  public List<Action> passingActions(List<Action> candidates) {
    int strength = Rules.strength(currentWinner().orElseThrow().action().card(), conditions());
    return candidates.stream()
        .filter(action -> Rules.strength(action.card(), conditions()) <= strength).toList();
  }

  /**
   * 자기 제출 전 현재 구에 실린 점수입니다. 이후 지원 점수는 예측하지 않습니다.
   */
  public int trickPoints() {
    return trickPoints;
  }

  /**
   * 현재 선두가 동료의 간인지 판정합니다. 이후 역전 가능성은 별도입니다.
   */
  public boolean allyRuffWinning() {
    return allyWinning() && currentWinner().orElseThrow().action()
        .card() instanceof StandardCard card
        && view.contract().trump().matches(card.suit())
        && !view.contract().trump().matches(conditions().leadSuit());
  }

  /**
   * 해당 자리가 리드 문양도 기루도 아닌 일반 카드를 버린 공개 사실입니다.
   */
  public boolean discardedOffSuit(Seat seat) {
    return view.trick().stream().anyMatch(play -> play.seat().equals(seat)
        && play.action().card() instanceof StandardCard card
        && card.suit() != conditions().leadSuit()
        && !view.contract().trump().matches(card.suit())
        && !card.equals(Rules.mighty(view.contract().trump())));
  }

  private List<Action> calculateWinningActions() {
    if (view.trick().isEmpty()) {
      return List.of();
    }
    int strongest = Rules.strength(currentWinner().orElseThrow().action().card(), conditions());
    return legal.stream().filter(action -> Rules.strength(action.card(), conditions()) > strongest)
        .toList();
  }

  /**
   * 같은 문양의 미확인 일반 상위 카드가 없는지 확인합니다. 특수카드·간의 안전 보장은 아닙니다.
   */
  public boolean isSuitTop(Card card) {
    if (!(card instanceof StandardCard standard)) {
      return false;
    }
    return cards.outsideCards(standard.suit()).stream().noneMatch(other ->
        !other.equals(Rules.mighty(view.contract().trump()))
            && other.rank().value() > standard.rank().value());
  }

  /**
   * 뒤에 남은 자리 모두가 뒤집을 수 없음을 자기·공개 정보로 증명할 때만 true입니다.
   */
  public boolean isSecured(PublicPlay winner) {
    if (view.trick().isEmpty()) {
      return false;
    }
    int strength = Rules.strength(winner.action().card(), conditions());
    for (Seat seat : remainingSeats()) {
      for (Card card : cards.outsideCards()) {
        if (!locations.possibleHolders(card).contains(seat)) {
          continue;
        }
        if (Rules.strength(card, conditions()) > strength) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * 현재 행동자 뒤에 아직 제출하지 않은 자리만 테이블 순서로 구합니다.
   */
  public List<Seat> remainingSeats() {
    return remaining;
  }

  private List<Seat> calculateRemainingSeats() {
    List<Seat> remaining = new ArrayList<>();
    Seat seat = view.context().playOrder().nextAfter(view.self());
    while (!seat.equals(view.context().leader())) {
      remaining.add(seat);
      seat = view.context().playOrder().nextAfter(seat);
    }
    return List.copyOf(remaining);
  }

  /**
   * 가상으로 이 카드를 선으로 냈을 때 외부 카드보다 강함을 보수적으로 확인합니다.
   */
  public boolean isMasterLead(Action action) {
    TrickConditions lead = Rules.conditionsFor(view.contract().trump(), view.trickNumber(), action);
    int strength = Rules.strength(action.card(), lead);
    return cards.outsideCards().stream().noneMatch(card -> Rules.strength(card, lead) > strength);
  }

}
