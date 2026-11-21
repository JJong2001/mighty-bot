package mighty.game.round;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import mighty.game.GameMode;
import mighty.game.GamePhase;
import mighty.game.PlayOrder;
import mighty.game.Seat;
import mighty.game.TrickNumber;
import mighty.game.bidding.Bid;
import mighty.game.bidding.BiddingResult;
import mighty.game.bidding.ContractChangeRules;
import mighty.game.friend.*;
import mighty.game.observation.*;
import mighty.game.rule.LegalActionContext;
import mighty.game.rule.LegalActions;
import mighty.game.rule.Rules;
import mighty.game.trick.PublicPlay;
import mighty.game.trick.PublicTrick;
import mighty.model.Card;

/**
 * 낙찰 후부터 10구 종료까지 한 판을 진행하는 상태 관리자입니다. 전체 손패와 바닥은 이 객체 내부에 보관하고, AI에는 전용 관측만 제공합니다. 행동이 검증되면 이 객체의
 * 상태를 변경합니다.
 */
public final class RoundState {

  private final PlayOrder playOrder;
  private final Seat declarer;
  private final Bid awarded;
  private Bid contract;
  private final List<Bid> contractHistory = new ArrayList<>();
  private final Map<Seat, List<Card>> hands;
  private final List<Card> kitty;
  private final List<Card> discarded = new ArrayList<>();
  private final List<PublicPlay> trick = new ArrayList<>();
  private final List<PublicTrick> history = new ArrayList<>();
  private int settingPoints;
  private int settingCards;
  private GamePhase phase = GamePhase.BEFORE_KITTY;
  private Optional<FriendSelection> selection = Optional.empty();
  private FriendStatus friendStatus = UnrevealedFriend.INSTANCE;

  private RoundState(
      PlayOrder playOrder, BiddingResult auction,
      Map<Seat, List<Card>> hands, List<Card> kitty
  ) {
    this.playOrder = playOrder;
    this.declarer = auction.declarer();
    this.awarded = auction.contract();
    this.contract = auction.contract();
    this.contractHistory.add(contract);
    this.hands = hands;
    this.kitty = kitty;
  }

  /**
   * 다섯 명이 10장씩 받은 배분으로 시작합니다. 육마에서는 제외와 재배분이 끝난 뒤의 다섯 명을 전달합니다.
   */
  public static RoundState start(
      PlayOrder order, BiddingResult auction,
      Map<Seat, List<Card>> dealtHands, List<Card> kitty
  ) {
    Objects.requireNonNull(order, "플레이 순서가 필요합니다.");
    Objects.requireNonNull(auction, "낙찰 결과가 필요합니다.");
    Objects.requireNonNull(dealtHands, "배분된 손패가 필요합니다.");
    List<Card> bottom = List.copyOf(kitty);
    if (!dealtHands.keySet().equals(new HashSet<>(order.seats()))
        || !order.seats().contains(auction.declarer()) || bottom.size() != 3) {
      throw new IllegalArgumentException("주공을 포함한 참가자 다섯 명과 바닥 3장이 필요합니다.");
    }
    Map<Seat, List<Card>> hands = new HashMap<>();
    List<Card> allCards = new ArrayList<>(bottom);
    for (Seat seat : order.seats()) {
      List<Card> hand = List.copyOf(dealtHands.get(seat));
      if (hand.size() != TrickNumber.LAST) {
        throw new IllegalArgumentException("모든 참가자는 10장으로 시작해야 합니다.");
      }
      hands.put(seat, new ArrayList<>(hand));
      allCards.addAll(hand);
    }
    if (new HashSet<>(allCards).size() != allCards.size()) {
      throw new IllegalArgumentException("배분된 손패와 바닥에 중복 카드가 있습니다.");
    }
    return new RoundState(order, auction, hands, bottom);
  }

  /**
   * 화면에 필요한 공개 주공 자리입니다.
   */
  public Seat declarer() {
    return declarer;
  }

  /**
   * 현재 공개 공약입니다.
   */
  public Bid contract() {
    return contract;
  }

  /**
   * 공개된 프렌드 지정 방식과 공개 상태만 반환합니다.
   */
  public Optional<FriendSelection> friendSelection() {
    return selection;
  }

  public FriendStatus publicFriendStatus() {
    return friendStatus;
  }

  /**
   * 상대 카드 내용을 포함하지 않는 남은 장수입니다.
   */
  public int handSize(Seat seat) {
    if (!hands.containsKey(seat)) {
      throw new IllegalArgumentException("플레이 참가자가 아닙니다.");
    }
    return hands.get(seat).size();
  }

  /**
   * 현재 진행 단계를 반환합니다.
   */
  public GamePhase phase() {
    return phase;
  }

  /**
   * 준비 단계는 주공, 플레이 중에는 다음 제출자, 종료 후에는 빈 값입니다.
   */
  public Optional<Seat> currentSeat() {
    if (phase == GamePhase.FINISHED) {
      return Optional.empty();
    }
    if (phase != GamePhase.PLAY) {
      return Optional.of(declarer);
    }
    if (!trick.isEmpty()) {
      return Optional.of(playOrder.nextAfter(trick.get(trick.size() - 1).seat()));
    }
    return Optional.of(history.isEmpty() ? declarer : history.get(history.size() - 1).winner());
  }

  /**
   * 바닥을 들기 전 주공에게만 자기 열 장을 제공합니다.
   */
  public List<Card> beforeKittyHand(Seat viewer) {
    requirePhase(GamePhase.BEFORE_KITTY);
    requireDeclarer(viewer);
    return List.copyOf(hands.get(viewer));
  }

  /**
   * 완료된 구의 공개 기록을 변경 불가능한 목록으로 반환합니다.
   */
  public List<PublicTrick> history() {
    return List.copyOf(history);
  }

  /**
   * 현재 구에 공개된 제출 목록을 반환합니다.
   */
  public List<PublicPlay> currentTrick() {
    return List.copyOf(trick);
  }

  /**
   * 허용된 준비 단계에서 공약을 변경하고 공개 선언 이력을 보존합니다.
   */
  public void reviseContract(Seat actor, Bid proposed) {
    requireDeclarer(actor);
    Objects.requireNonNull(proposed, "변경할 공약이 필요합니다.");
    if (!ContractChangeRules.canChange(awarded, contract, proposed, phase)) {
      throw new IllegalArgumentException("현재 단계의 공약 변경 규칙에 맞지 않습니다.");
    }
    if (!contract.equals(proposed)) {
      contract = proposed;
      contractHistory.add(proposed);
    }
  }

  /**
   * 최종 기루와 혼동하지 않도록 실제 공개된 선언 순서를 반환합니다.
   */
  public List<Bid> contractHistory() {
    return List.copyOf(contractHistory);
  }

  /**
   * 주공이 허용된 단계에서만 바닥을 받아 손패를 13장으로 만듭니다.
   */
  public void takeKitty(Seat actor) {
    requirePhase(GamePhase.BEFORE_KITTY);
    requireDeclarer(actor);
    hands.get(declarer).addAll(kitty);
    phase = GamePhase.DISCARD;
  }

  /**
   * 현재 버리기 단계의 주공에게만 13장 관측을 제공합니다.
   */
  public DiscardView discardView(Seat viewer) {
    requirePhase(GamePhase.DISCARD);
    requireDeclarer(viewer);
    return new DiscardView(declarer, contract, hands.get(declarer));
  }

  /**
   * 손패에 있는 서로 다른 세 장을 버리고 프렌드 지정 단계로 이동합니다.
   */
  public void discard(Seat actor, List<Card> cards) {
    requirePhase(GamePhase.DISCARD);
    requireDeclarer(actor);
    List<Card> chosen = List.copyOf(cards);
    if (chosen.size() != 3 || new HashSet<>(chosen).size() != 3
        || !hands.get(declarer).containsAll(chosen)) {
      throw new IllegalArgumentException("주공 손패에서 서로 다른 세 장을 선택해야 합니다.");
    }
    discarded.addAll(chosen);
    hands.get(declarer).removeAll(chosen);
    phase = GamePhase.FRIEND;
  }

  /**
   * 프렌드 지정 단계의 주공에게만 손패와 자기 버린 카드를 제공합니다.
   */
  public FriendView friendView(Seat viewer) {
    requirePhase(GamePhase.FRIEND);
    requireDeclarer(viewer);
    return new FriendView(declarer, contract, hands.get(declarer), discarded, contractHistory);
  }

  /**
   * 카드 프렌드, 초구 프렌드 또는 노프렌드를 지정하고 플레이를 시작합니다.
   */
  public void chooseFriend(Seat actor, FriendSelection friendSelection) {
    requirePhase(GamePhase.FRIEND);
    requireDeclarer(actor);
    selection = Optional.of(Objects.requireNonNull(friendSelection, "프렌드 지정이 필요합니다."));
    // 카드의 실제 소유자를 찾아 공개하지 않고, 공개된 행동으로만 프렌드를 확정합니다.
    friendStatus = friendSelection == SpecialFriend.NONE
        ? NoFriend.INSTANCE : UnrevealedFriend.INSTANCE;
    phase = GamePhase.PLAY;
  }

  /**
   * 현재 제출자의 자기 손패와 공개 정보만 포함하는 관측을 만듭니다.
   */
  public View currentView() {
    requirePhase(GamePhase.PLAY);
    Seat self = currentSeat().orElseThrow();
    PrivateInfo privateInfo = self.equals(declarer)
        ? new DeclarerInfo(hands.get(self), discarded)
        : new NonDeclarerInfo(hands.get(self));
    return ObservationFactory.create(self, privateInfo, contract, selection.orElseThrow(),
        new Context(playOrder, declarer, friendStatus, history, contractHistory), trick);
  }

  /**
   * 차례와 합법 행동을 검사하고 카드를 제출합니다. 다섯 장이면 구를 즉시 마칩니다.
   */
  public void play(PublicPlay submission) {
    Objects.requireNonNull(submission, "제출 행동이 필요합니다.");
    requirePhase(GamePhase.PLAY);
    Seat actor = currentSeat().orElseThrow();
    if (!actor.equals(submission.seat())) {
      throw new IllegalArgumentException("현재 카드를 제출할 차례가 아닙니다.");
    }
    List<mighty.game.action.Action> legal = LegalActions.legalActions(
        new LegalActionContext(hands.get(actor), contract.trump(),
            new TrickNumber(history.size() + 1), trick));
    if (!legal.contains(submission.action())) {
      throw new IllegalArgumentException("지금 제출할 수 없는 카드 행동입니다.");
    }
    hands.get(actor).remove(submission.action().card());
    trick.add(submission);
    revealCardFriend(submission);
    if (trick.size() == GameMode.PLAYING_PLAYER_COUNT) {
      completeTrick();
    }
  }

  /**
   * 셋팅은 제출하지 않은 카드를 가짜 플레이 이력으로 만들지 않고 별도 획득으로 기록합니다.
   */
  public void declareSetting(Seat actor) {
    requirePhase(GamePhase.PLAY);
    requireDeclarer(actor);
    if (!currentSeat().orElseThrow().equals(actor) || !mighty.game.setting.SettingProof.canDeclare(
        currentView())) {
      throw new IllegalArgumentException("자기 선에서 모든 순서의 승리를 공개 정보로 입증해야 합니다.");
    }
    if (selection.orElseThrow() instanceof CardFriend friend) {
      friendStatus = NoFriend.INSTANCE;
      for (var entry : hands.entrySet()) {
        if (!entry.getKey().equals(declarer) && entry.getValue().contains(friend.card())) {
          friendStatus = new RevealedFriend(entry.getKey());
        }
      }
    } else if (friendStatus instanceof UnrevealedFriend) {
      friendStatus = NoFriend.INSTANCE;
    }
    settingCards = hands.values().stream().mapToInt(List::size).sum();
    settingPoints = hands.values().stream().flatMap(List::stream).mapToInt(Card::points).sum();
    hands.values().forEach(List::clear);
    phase = GamePhase.FINISHED;
  }

  public int settingCollectedCards() {
    return settingCards;
  }

  /**
   * 지정 카드가 공개됐을 때만 그 카드를 낸 사람의 프렌드 여부를 공개합니다.
   */
  private void revealCardFriend(PublicPlay submission) {
    if (selection.orElseThrow() instanceof CardFriend friend
        && friend.card().equals(submission.action().card())) {
      friendStatus = submission.seat().equals(declarer)
          ? NoFriend.INSTANCE : new RevealedFriend(submission.seat());
    }
  }

  /**
   * 승자와 점수를 기록하고 다음 선을 정합니다. 10구 후에는 판을 종료합니다.
   */
  private void completeTrick() {
    TrickNumber number = new TrickNumber(history.size() + 1);
    Seat winner = Rules.currentWinner(contract.trump(), number, trick).orElseThrow().seat();
    history.add(new PublicTrick(number, trick, winner));
    if (number.isFirst() && selection.orElseThrow() == SpecialFriend.FIRST_TRICK) {
      friendStatus = winner.equals(declarer) ? NoFriend.INSTANCE : new RevealedFriend(winner);
    }
    trick.clear();
    if (number.isLast()) {
      // 버린 카드 프렌드처럼 끝까지 소유자가 등장하지 않은 경우에는 실제 혼자입니다.
      if (friendStatus instanceof UnrevealedFriend) {
        friendStatus = NoFriend.INSTANCE;
      }
      phase = GamePhase.FINISHED;
    }
  }

  /**
   * 종료 후에만 참가자별 득점, 여야 득점 및 공약 성공 여부를 공개합니다.
   */
  public Optional<RoundResult> result() {
    if (phase != GamePhase.FINISHED) {
      return Optional.empty();
    }
    Map<Seat, Integer> captured = new HashMap<>();
    for (Seat seat : playOrder.seats()) {
      captured.put(seat, 0);
    }
    for (PublicTrick completed : history) {
      captured.merge(completed.winner(), completed.points(), Integer::sum);
    }
    captured.merge(declarer, settingPoints, Integer::sum);
    Optional<Seat> friend = friendStatus instanceof RevealedFriend revealed
        ? Optional.of(revealed.seat()) : Optional.empty();
    int discardedPoints = discarded.stream().mapToInt(Card::points).sum();
    return Optional.of(new RoundResult(declarer, contract, friend, captured, discardedPoints));
  }

  /**
   * 현재 단계에서 허용된 요청인지 확인합니다.
   */
  private void requirePhase(GamePhase expected) {
    if (phase != expected) {
      throw new IllegalStateException("현재 단계에서 할 수 없는 행동입니다: " + phase);
    }
  }

  /**
   * 주공에게만 허용된 관측과 행동의 요청자를 확인합니다.
   */
  private void requireDeclarer(Seat seat) {
    if (!declarer.equals(Objects.requireNonNull(seat, "요청자 자리가 필요합니다."))) {
      throw new IllegalArgumentException("주공에게만 허용된 요청입니다.");
    }
  }

}
