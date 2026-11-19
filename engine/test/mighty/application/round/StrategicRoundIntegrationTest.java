package mighty.application.round;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.IntStream;
import mighty.ai.bidding.RuleBiddingPolicy;
import mighty.ai.play.RulePlayPolicy;
import mighty.application.bidding.BiddingService;
import mighty.game.*;
import mighty.game.bidding.*;
import mighty.game.deal.*;
import mighty.game.friend.*;
import mighty.game.observation.BiddingView;
import mighty.game.round.*;
import mighty.game.trick.PublicPlay;
import mighty.model.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class StrategicRoundIntegrationTest {

  private record Fixture(PlayOrder order, Map<Seat, List<Card>> hands, List<Card> kitty) {

  }

  private static Fixture deal(int seed, boolean sixSeatTable) {
    List<Seat> seats = IntStream.range(0, sixSeatTable ? 6 : 5).mapToObj(Seat::new).toList();
    var active = new HashSet<>(seats);
    if (sixSeatTable) {
      active.remove(new Seat(2));
    }
    PlayOrder order = PlayOrder.from(
        new TableLayout(sixSeatTable ? GameMode.SIX_PLAYER : GameMode.FIVE_PLAYER,
            seats), active);
    List<Card> deck = new ArrayList<>(DeckFactory.create());
    Collections.shuffle(deck, new Random(seed));
    Map<Seat, List<Card>> hands = new HashMap<>();
    for (int index = 0; index < 5; index++) {
      hands.put(order.seats().get(index), List.copyOf(deck.subList(index * 10, index * 10 + 10)));
    }
    return new Fixture(order, hands, List.copyOf(deck.subList(50, 53)));
  }

  @Test
  @DisplayName("새 전략으로 오마와 육마 활성 다섯 자리에서 모든 기루의 100판을 완주한다")
  void strategicPoliciesCompleteAllTrumpCandidates() {
    for (Trump trump : Trump.values()) {
      for (int seed = 0; seed < 20; seed++) {
        Fixture fixture = deal(seed, seed % 2 == 0);
        RoundState round = RoundState.start(fixture.order(),
            new BiddingResult(new Seat(0), new Bid(15, trump)), fixture.hands(), fixture.kitty());
        RoundResult result = RoundService.strategic().playToEnd(round);
        assertEquals(10, round.history().size() + round.settingCollectedCards() / 5);
        assertEquals(50, round.history().stream().flatMap(trick -> trick.cards().stream())
            .map(play -> play.action().card()).distinct().count() + round.settingCollectedCards());
        assertEquals(20, result.teamPoints() + result.oppositionPoints());
        assertEquals(result, round.result().orElseThrow());
      }
    }
  }

  @Test
  @DisplayName("새 비딩 전략부터 준비와 10구까지 40판을 연속 검증한다")
  void strategicAuctionToFinalScore() {
    int completed = 0;
    Seat opening = new Seat(0);
    for (int seed = 0; completed < 40; seed++) {
      assertTrue(seed < 200, "딜미스 재배분이 지나치게 반복됩니다.");
      Fixture fixture = deal(seed, false);
      List<Seat> seats = fixture.order().seats();
      BiddingState bidding = BiddingState.start(
          new DealParticipants(DealFormat.FIVE_PLAYER_OPENING, seats),
          opening);
      BiddingService biddingService = new BiddingService(new RuleBiddingPolicy());
      int actions = 0;
      while (!bidding.isFinished()) {
        Seat self = bidding.currentSeat().orElseThrow();
        bidding = biddingService.playTurn(
            new BiddingView(self, fixture.hands().get(self), 40, bidding));
        assertTrue(++actions < 200);
      }
      if (bidding.result().orElseThrow() instanceof DealMissResult miss) {
        assertTrue(DealMissRules.isEligible(DealFormat.FIVE_PLAYER_OPENING,
            fixture.hands().get(miss.declaredBy()), 40));
        opening = miss.declaredBy();
        continue;
      }
      BiddingResult award = assertInstanceOf(BiddingResult.class, bidding.result().orElseThrow());
      opening = award.declarer();
      completed++;
      RoundState round = RoundState.start(fixture.order(), award, fixture.hands(), fixture.kitty());
      RoundResult result = RoundService.strategic().playToEnd(round);
      assertEquals(20, result.teamPoints() + result.oppositionPoints());
      assertEquals(round.contract(), result.contract());
      assertEquals(award.contract(), round.contractHistory().get(0));
      for (int i = 1; i < round.contractHistory().size(); i++) {
        assertTrue(
            ContractChangeRules.canChange(award.contract(), round.contractHistory().get(i - 1),
                round.contractHistory().get(i), GamePhase.FRIEND));
      }
    }
  }

  @Test
  @DisplayName("다른 두 사람의 패를 교환해도 자기 관측과 추천은 바뀌지 않는다")
  void recommendationsDoNotUseHiddenHands() {
    Fixture fixture = deal(11, false);
    Map<Seat, List<Card>> changed = new HashMap<>(fixture.hands());
    changed.put(new Seat(2), fixture.hands().get(new Seat(3)));
    changed.put(new Seat(3), fixture.hands().get(new Seat(2)));
    BiddingResult award = new BiddingResult(new Seat(0), new Bid(15, Trump.NO_TRUMP));
    RoundState first = RoundState.start(fixture.order(), award, fixture.hands(), fixture.kitty());
    RoundState second = RoundState.start(fixture.order(), award, changed, fixture.kitty());
    RoundService service = RoundService.strategic();
    for (int step = 0; step < 3; step++) {
      service.advance(first);
      service.advance(second);
    }
    RulePlayPolicy policy = new RulePlayPolicy();
    var opening = policy.choose(first.currentView());
    assertEquals(opening, policy.choose(second.currentView()));
    first.play(new PublicPlay(new Seat(0), opening));
    second.play(new PublicPlay(new Seat(0), opening));
    assertEquals(first.currentView(), second.currentView());
    assertEquals(policy.recommend(first.currentView()), policy.recommend(second.currentView()));
  }

  @Test
  @DisplayName("풀의 반복 공약 변경 이력을 관측에 전달하고 플레이 중 변경은 거절한다")
  void publishesOnlyActualContractChanges() {
    Fixture fixture = deal(13, false);
    Bid award = new Bid(20, Trump.SPADE);
    RoundState round = RoundState.start(fixture.order(), new BiddingResult(new Seat(0), award),
        fixture.hands(),
        fixture.kitty());
    round.reviseContract(new Seat(0), new Bid(20, Trump.DIAMOND));
    round.takeKitty(new Seat(0));
    round.reviseContract(new Seat(0), new Bid(20, Trump.SPADE));
    RoundService service = RoundService.strategic();
    service.advance(round);
    round.reviseContract(new Seat(0), new Bid(20, Trump.NO_TRUMP));
    service.advance(round);
    assertEquals(List.of(award, new Bid(20, Trump.DIAMOND), award, new Bid(20, Trump.NO_TRUMP)),
        round.currentView().context().contractHistory());
    assertThrows(UnsupportedOperationException.class, () -> round.contractHistory().clear());
    assertThrows(IllegalArgumentException.class, () -> round.reviseContract(new Seat(0), award));
    assertEquals(Trump.NO_TRUMP, RoundService.strategic().playToEnd(round).contract().trump());
  }

}
