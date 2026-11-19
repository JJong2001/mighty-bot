package mighty.application.match;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.stream.IntStream;
import mighty.ai.AiPolicies;
import mighty.application.round.RoundPreparation;
import mighty.ai.bidding.RuleBiddingPolicy;
import mighty.ai.play.RulePlayPolicy;
import mighty.ai.preparation.RuleDiscardPolicy;
import mighty.ai.preparation.RuleFriendPolicy;
import mighty.data.ActionConverter;
import mighty.data.Journal;
import mighty.data.ObservationSnapshot;
import mighty.game.*;
import mighty.game.action.Action;
import mighty.game.bidding.*;
import mighty.game.deal.*;
import mighty.game.friend.FriendSelection;
import mighty.game.observation.BiddingView;
import mighty.game.observation.View;
import mighty.game.round.RoundResult;
import mighty.game.round.RoundState;
import mighty.game.rule.LegalActionContext;
import mighty.game.rule.LegalActions;
import mighty.game.trick.PublicPlay;
import mighty.game.trick.PublicTrick;
import mighty.model.Card;

/**
 * 화면과 현재 엔진의 연결부입니다. 전체 배분은 보관하되 AI에는 자기 관측만 전달합니다.
 */
public final class MatchSession {

  private static final Seat HUMAN = new Seat(0);
  private final String id = UUID.randomUUID().toString();
  private final Journal journal;
  private final Random random;
  private final List<Seat> seats = IntStream.range(0, 5).mapToObj(Seat::new).toList();
  private final Map<Seat, List<Card>> dealtHands = new HashMap<>();
  private final List<Card> visibleHand = new ArrayList<>();
  private final List<String> messages = new ArrayList<>();
  private final Map<Seat, Integer> practiceScores = new HashMap<>();
  private final AiPolicies policies = AiPolicies.current();
  private final RoundPreparation preparation = new RoundPreparation(policies.preparation(),
      policies.friend());
  private final RuleBiddingPolicy biddingPolicy = policies.bidding();
  private final RuleDiscardPolicy discardPolicy = policies.discard();
  private final RuleFriendPolicy friendPolicy = policies.friend();
  private final RulePlayPolicy playPolicy = policies.play();
  private List<Card> kitty = List.of();
  private BiddingState bidding;
  private Optional<RoundState> round = Optional.empty();

  public MatchSession(long seed, Journal journal) throws IOException {
    this.journal = java.util.Objects.requireNonNull(journal);
    random = new Random(seed);
    seats.forEach(seat -> practiceScores.put(seat, 40));
    deal(seats.get(random.nextInt(seats.size())));
    journal.gameEvent(id, "start", "seed=" + seed + "; engine=current; practice=one-hand");
  }

  /**
   * 새 배분에서는 비딩 기록과 주공 준비 상태도 새로 만듭니다.
   */
  private void deal(Seat first) {
    List<Card> deck = new ArrayList<>(DeckFactory.create());
    Collections.shuffle(deck, random);
    dealtHands.clear();
    for (int index = 0; index < seats.size(); index++) {
      dealtHands.put(seats.get(index), List.copyOf(deck.subList(index * 10, index * 10 + 10)));
    }
    kitty = List.copyOf(deck.subList(50, 53));
    visibleHand.clear();
    visibleHand.addAll(dealtHands.get(HUMAN));
    bidding = BiddingState.start(new DealParticipants(DealFormat.FIVE_PLAYER_OPENING, seats),
        first);
    round = Optional.empty();
    messages.add("새 배분 · " + name(first) + "부터 비딩합니다.");
  }

  public String id() {
    return id;
  }

  public GamePhase phase() {
    return round.map(RoundState::phase).orElse(GamePhase.BIDDING);
  }

  public Optional<Seat> currentSeat() {
    return round.isPresent() ? round.get().currentSeat() : bidding.currentSeat();
  }

  public List<Card> humanHand() {
    return List.copyOf(visibleHand);
  }

  public List<String> messages() {
    return List.copyOf(messages);
  }

  public int settingCollectedCards() {
    return round.map(RoundState::settingCollectedCards).orElse(0);
  }

  public List<PublicTrick> history() {
    return round.map(RoundState::history).orElse(List.of());
  }

  public List<PublicPlay> currentTrick() {
    return round.map(RoundState::currentTrick).orElse(List.of());
  }

  public Optional<RoundResult> result() {
    return round.flatMap(RoundState::result);
  }

  public Optional<Seat> declarer() {
    return round.map(RoundState::declarer);
  }

  public Optional<Bid> contract() {
    return round.map(RoundState::contract)
        .or(() -> bidding.highestBid().map(play -> ((BidOffer) play.action()).bid()));
  }

  public String friendLabel() {
    if (round.isEmpty() || round.get().friendSelection().isEmpty()) {
      return "미지정";
    }
    FriendSelection selection = round.get().friendSelection().orElseThrow();
    String label = selection instanceof mighty.game.friend.CardFriend card
        ? card.card().displayName() + " 프렌드"
        : selection == mighty.game.friend.SpecialFriend.NONE ? "노프렌드" : "초구 프렌드";
    var status = round.get().publicFriendStatus();
    if (status instanceof mighty.game.friend.RevealedFriend friend) {
      return label + " · " + name(friend.seat()) + " 공개";
    }
    if (status instanceof mighty.game.friend.NoFriend) {
      return label + " · 실제 혼자";
    }
    return label + " · 미공개";
  }

  /**
   * 상대는 공개된 남은 장수만 표시합니다.
   */
  public int handSize(Seat seat) {
    return round.map(state -> state.handSize(seat)).orElse(dealtHands.get(seat).size());
  }

  /**
   * UI의 인간 자리만 선택지를 받습니다. AI의 패와 비공개 후보는 화면으로 넘기지 않습니다.
   */
  public List<BiddingAction> humanBids() {
    if (phase() != GamePhase.BIDDING || !currentSeat().filter(HUMAN::equals).isPresent()) {
      return List.of();
    }
    return LegalBiddingActions.legalActions(bidding, dealtHands.get(HUMAN),
        practiceScores.get(HUMAN));
  }

  public List<Action> humanPlays() {
    if (phase() != GamePhase.PLAY || !currentSeat().filter(HUMAN::equals).isPresent()) {
      return List.of();
    }
    return legalPlays(round.orElseThrow().currentView());
  }

  /**
   * 실제 제출 전 인간 자기 자리의 추천만 설명합니다.
   */
  public String humanHint() {
    if (!currentSeat().filter(HUMAN::equals).isPresent()) {
      return "현재는 다른 참가자의 차례입니다.";
    }
    return switch (phase()) {
      case BIDDING -> "추천: " + biddingLabel(biddingPolicy.choose(biddingView()));
      case BEFORE_KITTY -> "바닥을 받은 뒤 버릴 세 장을 선택하세요.";
      case DISCARD -> {
        var state = round.orElseThrow();
        var plan = policies.preparation()
            .choose(state.discardView(HUMAN), state.contractHistory().get(0),
                state.contractHistory());
        yield "추천 공약: " + bidLabel(plan.contract()) + "\n추천 버리기: " + ObservationSnapshot.cards(
            plan.discarded());
      }
      case FRIEND ->
          "추천 프렌드: " + friendLabel(friendPolicy.choose(round.orElseThrow().friendView(HUMAN)));
      case PLAY -> {
        var decision = playPolicy.recommend(round.orElseThrow().currentView());
        yield "추천: " + ActionConverter.encode(decision.action()) + "\n" + decision.reason();
      }
      default -> "대국이 종료됐습니다.";
    };
  }

  public void bid(BiddingAction action) throws IOException {
    bid(action, "human");
  }

  public void takeKitty() throws IOException {
    takeKitty("human");
  }

  public void discard(List<Card> cards) throws IOException {
    discard(cards, "human");
  }

  public void friend(FriendSelection selection) throws IOException {
    friend(selection, "human");
  }

  public void play(Action action) throws IOException {
    play(action, "human");
  }

  /**
   * 현재 AI에게 자기 정보만 전달하고 현행 정책의 선택을 제출합니다.
   */
  public void advanceAi() throws IOException {
    switch (phase()) {
      case BIDDING -> bid(biddingPolicy.choose(biddingView()), "bot");
      case BEFORE_KITTY -> takeKitty("bot");
      case DISCARD -> preparation.discard(round.orElseThrow(), this::reviseContract,
          cards -> discard(cards, "bot"));
      case FRIEND -> preparation.chooseFriend(round.orElseThrow(), this::reviseContract,
          selection -> friend(selection, "bot"));
      case PLAY -> {
        var state = round.orElseThrow();
        if (mighty.game.setting.SettingProof.canDeclare(state.currentView())) {
          state.declareSetting(state.currentSeat().orElseThrow());
          visibleHand.clear();
          journal.gameEvent(id, "setting", "주공이 공개 관측으로 남은 모든 순서의 승리를 입증해 셋팅했습니다.");
          var result = state.result().orElseThrow();
          journal.gameEvent(id, "result",
              "셋팅 종료 · 여당 " + result.teamPoints() + "점 / 야당 " + result.oppositionPoints() + "점");
          messages.add("주공 셋팅 · 남은 " + state.settingCollectedCards() + "장 획득 · 여당 "
              + result.teamPoints() + "점 / 야당 " + result.oppositionPoints() + "점 · 공약 "
              + (result.succeeded() ? "성공" : "실패"));
        } else {
          play(playPolicy.choose(state.currentView()), "bot");
        }
      }
      default -> throw new IllegalStateException("대국이 종료됐습니다.");
    }
  }

  private BiddingView biddingView() {
    Seat actor = currentSeat().orElseThrow();
    return new BiddingView(actor, dealtHands.get(actor), practiceScores.get(actor), bidding);
  }

  private void bid(BiddingAction action, String source) throws IOException {
    if (phase() != GamePhase.BIDDING) {
      throw new IllegalStateException("비딩 단계가 아닙니다.");
    }
    BiddingView view = biddingView();
    Map<String, Object> snapshot = new LinkedHashMap<>();
    snapshot.put("hand", ObservationSnapshot.cards(view.hand()));
    snapshot.put("playerScore", view.playerScore());
    snapshot.put("currentBid", contract().map(Object::toString).orElse("첫 입찰"));
    snapshot.put("publicAuction", bidding.history().stream().map(Object::toString).toList());
    List<String> alternatives = LegalBiddingActions.legalActions(bidding, view.hand(),
            view.playerScore()).stream()
        .map(MatchSession::bidCode).toList();
    bidding = bidding.submit(new PublicBid(view.self(), action), view.hand(), view.playerScore());
    journal.decision(id, view.self(), "BIDDING", snapshot, bidCode(action), alternatives,
        "후보 기루별 상한 안의 경쟁 입찰 또는 사용자 선택", source);
    messages.add(name(view.self()) + " · " + biddingLabel(action));
    if (bidding.result().isPresent()) {
      BiddingOutcome outcome = bidding.result().orElseThrow();
      if (outcome instanceof DealMissResult miss) {
        practiceScores.merge(miss.declaredBy(), -5, Integer::sum);
        journal.gameEvent(id, "deal-miss", "seat=" + miss.declaredBy().index());
        messages.add("딜미스 · 선언자 5점 차감 후 같은 자리부터 재배분합니다.");
        deal(miss.declaredBy());
      } else {
        BiddingResult award = (BiddingResult) outcome;
        PlayOrder order = PlayOrder.from(new TableLayout(GameMode.FIVE_PLAYER, seats),
            new HashSet<>(seats));
        round = Optional.of(RoundState.start(order, award, dealtHands, kitty));
        messages.add(name(award.declarer()) + " 주공 낙찰 · " + bidLabel(award.contract()));
      }
    }
  }

  private void takeKitty(String source) throws IOException {
    Seat actor = currentSeat().orElseThrow();
    round.orElseThrow().takeKitty(actor);
    if (actor.equals(HUMAN)) {
      visibleHand.addAll(kitty);
    }
    journal.gameEvent(id, "kitty", "주공 바닥 수령");
    messages.add(name(actor) + " 바닥 수령 · 세 장을 버립니다.");
  }

  private void discard(List<Card> cards, String source) throws IOException {
    Seat actor = currentSeat().orElseThrow();
    var view = round.orElseThrow().discardView(actor);
    Map<String, Object> snapshot = Map.of("hand", ObservationSnapshot.cards(view.hand()),
        "contract", view.contract().toString());
    round.orElseThrow().discard(actor, cards);
    if (actor.equals(HUMAN)) {
      visibleHand.removeAll(cards);
    }
    journal.decision(id, actor, "DISCARD", snapshot, ObservationSnapshot.cards(cards), List.of(),
        "주공 자기 13장에서 세 장 선택", source);
    messages.add(name(actor) + " 버리기 완료");
  }

  private void friend(FriendSelection selection, String source) throws IOException {
    Seat actor = currentSeat().orElseThrow();
    var view = round.orElseThrow().friendView(actor);
    round.orElseThrow().chooseFriend(actor, selection);
    journal.decision(id, actor, "FRIEND", Map.of("hand", ObservationSnapshot.cards(view.hand()),
            "ownDiscards", ObservationSnapshot.cards(view.discardedCards())), selection.toString(),
        List.of(),
        "자기 손패와 버린 카드로 프렌드를 지정", source);
    messages.add(name(actor) + " · " + friendLabel(selection));
  }

  private void play(Action action, String source) throws IOException {
    View view = round.orElseThrow().currentView();
    var decision = playPolicy.recommend(view);
    List<String> alternatives = legalPlays(view).stream().map(ActionConverter::encode).toList();
    round.orElseThrow().play(new PublicPlay(view.self(), action));
    if (view.self().equals(HUMAN)) {
      visibleHand.remove(action.card());
    }
    journal.decision(id, view.self(), "PLAY / " + view.trickNumber().value() + "구",
        ObservationSnapshot.play(view),
        ActionConverter.encode(action), alternatives,
        "당시 엔진 추천 " + ActionConverter.encode(decision.action())
            + " · " + decision.reason(), source);
    messages.add(
        view.trickNumber().value() + "구 · " + name(view.self()) + " → " + ActionConverter.encode(
            action));
    if (round.orElseThrow().currentTrick().isEmpty()) {
      var completed = history().get(history().size() - 1);
      messages.add(completed.number().value() + "구 " + name(completed.winner()) + " 승리 · "
          + completed.points() + "점");
    }
    if (result().isPresent()) {
      RoundResult result = result().orElseThrow();
      String text = "여당 " + result.teamPoints() + "점 / 야당 " + result.oppositionPoints() + "점 · "
          + (result.succeeded() ? "공약 성공" : "공약 실패");
      messages.add(text);
      journal.gameEvent(id, "result", text);
    }
  }

  /**
   * 준비 단계의 공약 변경도 현재 엔진의 검증을 통과해야 합니다.
   */
  public void reviseContract(Bid proposed) throws IOException {
    Seat actor = currentSeat().orElseThrow();
    round.orElseThrow().reviseContract(actor, proposed);
    journal.gameEvent(id, "contract-change", proposed.toString());
    messages.add(name(actor) + " 공약 변경 · " + bidLabel(proposed));
  }

  /**
   * 새 판을 열거나 창을 닫으면 미완료 판을 중단 기록으로 닫습니다.
   */
  public void abandon() throws IOException {
    if (phase() != GamePhase.FINISHED) {
      journal.gameEvent(id, "abandoned", "phase=" + phase());
    }
  }

  private static List<Action> legalPlays(View view) {
    return LegalActions.legalActions(
        new LegalActionContext(view.hand(), view.contract().trump(), view.trickNumber(),
            view.trick()));
  }

  public static String name(Seat seat) {
    return seat.index() == 0 ? "나" : "AI " + seat.index();
  }

  private static String biddingLabel(BiddingAction action) {
    if (action instanceof BidOffer offer) {
      return bidLabel(offer.bid());
    }
    return action instanceof DealMiss ? "딜미스 선언" : "패스";
  }

  private static String bidLabel(Bid bid) {
    String suit = switch (bid.trump()) {
      case SPADE -> "♠ 스페이드";
      case DIAMOND -> "♦ 다이아";
      case HEART -> "♥ 하트";
      case CLUB -> "♣ 클럽";
      case NO_TRUMP -> "노기루";
    };
    return bid.points() + " " + suit;
  }

  private static String friendLabel(FriendSelection selection) {
    if (selection instanceof mighty.game.friend.CardFriend card) {
      return card.card().displayName() + " 프렌드";
    }
    return selection == mighty.game.friend.SpecialFriend.NONE ? "노프렌드" : "초구 프렌드";
  }

  public static String bidCode(BiddingAction action) {
    if (action instanceof BidOffer offer) {
      return offer.bid().points() + ":" + offer.bid().trump().name();
    }
    return action instanceof DealMiss ? "DEAL_MISS" : "PASS";
  }

}
