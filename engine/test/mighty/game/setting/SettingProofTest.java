package mighty.game.setting;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.stream.IntStream;
import mighty.data.CardParser;
import mighty.game.*;
import mighty.game.bidding.*;
import mighty.game.deal.DeckFactory;
import mighty.game.friend.*;
import mighty.game.round.RoundState;
import org.junit.jupiter.api.*;

class SettingProofTest {

  private static RoundState round(boolean joker, boolean friend) {
    return round(joker, friend ? new CardFriend(CardParser.parse("HQ")) : SpecialFriend.NONE);
  }

  private static RoundState round(boolean joker, FriendSelection friend) {
    var seats = IntStream.range(0, 5).mapToObj(Seat::new).toList();
    var order = PlayOrder.from(new TableLayout(GameMode.FIVE_PLAYER, seats), new HashSet<>(seats));
    var own = CardParser.parseList(
        joker ? "S6 S7 S8 S9 S10 SJ SQ SK SA JK" : "S5 S6 S7 S8 S9 S10 SJ SQ SK SA");
    var buried = CardParser.parseList(joker ? "DA S4 S5" : "DA JK S4");
    var other = DeckFactory.create().stream().filter(c -> !own.contains(c) && !buried.contains(c))
        .toList();
    Map<Seat, List<mighty.model.Card>> hands = new HashMap<>();
    hands.put(new Seat(0), own);
    for (int i = 1; i < 5; i++) {
      hands.put(new Seat(i), other.subList((i - 1) * 10, i * 10));
    }
    var round = RoundState.start(order,
        new BiddingResult(new Seat(0), new Bid(20, mighty.model.Trump.SPADE)),
        hands, buried);
    round.takeKitty(new Seat(0));
    round.discard(new Seat(0), buried);
    round.chooseFriend(new Seat(0), friend);
    return round;
  }

  @Test
  @DisplayName("보장된 셋팅은 미제출 50장을 별도 획득하고 가짜 구 이력을 만들지 않는다")
  void resolvesProvenSettingWithoutFabricatedPlays() {
    var round = round(false, false);
    assertTrue(SettingProof.canDeclare(round.currentView()));
    round.declareSetting(new Seat(0));
    assertEquals(GamePhase.FINISHED, round.phase());
    assertTrue(round.history().isEmpty());
    assertEquals(50, round.settingCollectedCards());
    assertEquals(20, round.result().orElseThrow().teamPoints());
    assertTrue(round.result().orElseThrow().succeeded());
  }

  @Test
  @DisplayName("조커 보유는 나머지가 강해도 셋팅하지 않는다")
  void jokerRejectsSetting() {
    var round = round(true, false);
    assertFalse(SettingProof.canDeclare(round.currentView()));
    assertThrows(IllegalArgumentException.class, () -> round.declareSetting(new Seat(0)));
    assertEquals(GamePhase.PLAY, round.phase());
  }

  @Test
  @DisplayName("셋팅 종료에서 미공개 실제 카드 친구는 정산을 위해 확정한다")
  void closesWithActualFriendWithoutGivingHandsToPolicy() {
    var round = round(false, true);
    assertInstanceOf(UnrevealedFriend.class, round.publicFriendStatus());
    assertTrue(SettingProof.canDeclare(round.currentView()));
    round.declareSetting(new Seat(0));
    assertTrue(round.result().orElseThrow().friend().isPresent());
    assertEquals(20, round.result().orElseThrow().teamPoints());
  }

  @Test
  void keepsTheRevealedCardFriendAtSettlement() {
    var called = CardParser.parse("S2");
    var round = round(false, new CardFriend(called));
    for (int i = 0; i < 5; i++) {
      var view = round.currentView();
      var legal = new mighty.ai.play.PlayFacts(view).legalActions();
      var action = legal.stream().filter(a -> a.card().equals(called)).findFirst().orElse(legal.get(0));
      round.play(new mighty.game.trick.PublicPlay(view.self(), action));
    }
    var friend = assertInstanceOf(RevealedFriend.class, round.publicFriendStatus());
    round.declareSetting(new Seat(0));
    assertEquals(Optional.of(friend.seat()), round.result().orElseThrow().friend());
  }

}
