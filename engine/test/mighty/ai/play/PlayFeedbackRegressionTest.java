package mighty.ai.play;

import static mighty.ai.play.PlayTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import mighty.game.Role;
import mighty.game.action.StandardPlay;
import mighty.game.friend.*;
import mighty.game.observation.View;
import mighty.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * 이번 피드백과 기존 보존 원칙을 같은 입력에서 함께 검사합니다.
 */
class PlayFeedbackRegressionTest {

  private final RulePlayPolicy policy = new RulePlayPolicy();

  @Test
  void doesNotFeedConnectedPointsToDeclarerOpeningKing() {
    View view = view(1, 0, Trump.NO_TRUMP, 17, "SJ S10 S4 H2 H3 H4 D2 D3 D4 C2", "",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(), List.of(p(0, "SK")));
    assertEquals(card("S4"), policy.choose(view).card());
  }

  @Test
  void doesNotStackPointTrumpsWhenAnotherOpponentAlreadyPresses() {
    var first = row(1, Trump.HEART, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    View view = view(2, 0, Trump.HEART, 17, "H5 HJ HK S2 S3 D2 D3 C6 C7", "",
        new CardFriend(card("C5")), new RevealedFriend(new mighty.game.Seat(4)), List.of(first),
        List.of(p(0, "H2"), p(1, "H10")));
    assertEquals(card("H5"), policy.choose(view).card());
  }

  @Test
  void stillPressesBelowOwnAnchorAfterAnInterveningRankWasSpent() {
    var first = row(1, Trump.HEART, p(4, "C2"), p(0, "C3"), p(1, "HQ"), p(2, "C4"), p(3, "C5"));
    View view = view(2, 4, Trump.HEART, 17, "H5 HJ HK S2 S3 D2 D3 C6 C7", "",
        new CardFriend(card("C3")), new RevealedFriend(new mighty.game.Seat(0)), List.of(first),
        List.of(p(1, "H2")));
    // Q가 이미 빠졌으므로 J와 K는 연결 신호로 바뀔 수 있지만 물패로 돌아가면 안 됩니다.
    assertEquals(1, policy.choose(view).card().points());
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void pressesWithJackFromThreeTrumpsRegardlessOfSpecialFriend(boolean jokerFriend) {
    var first = row(1, Trump.HEART, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var selection = new CardFriend(jokerFriend ? Joker.INSTANCE : card("SA"));
    View view = view(1, 0, Trump.HEART, 17, "H5 HJ HK S2 S3 D2 D3 C6 C7", "", selection,
        UnrevealedFriend.INSTANCE, List.of(first), List.of(p(0, "H2")));
    assertEquals(card("HJ"), policy.choose(view).card());
  }

  @Test
  void takesFriendQueenWithPointTrumpWhileJokerIsStillOutside() {
    var first = row(1, Trump.HEART, p(4, "C2"), p(0, "SA"), p(1, "C3"), p(2, "C4"), p(3, "C5"));
    View view = view(1, 4, Trump.HEART, 17, "HA HK H5 S2 S3 D2 D3 C6 C7", "",
        new CardFriend(card("SA")), new RevealedFriend(new mighty.game.Seat(0)), List.of(first),
        List.of(p(0, "HQ")));
    var action = policy.choose(view);
    assertEquals(1, action.card().points());
    assertTrue(new PlayFacts(view).winningActions().contains(action));
  }

  @ParameterizedTest
  @EnumSource(value = Trump.class, names = {"SPADE", "NO_TRUMP"})
  void leadsPointsWhenFourPointsRemainEvenWithoutKnownWinningAlly(Trump trump) {
    var first = row(1, trump, p(0, "C2"), p(1, "CA"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var second = row(2, trump, p(1, "H9"), p(2, "H2"), p(3, "H3"), p(4, "H4"), p(0, "D2"));
    View view = view(1, 0, trump, 17, "H5 H6 H7 H8 H10 HJ HQ HK", "",
        new CardFriend(Joker.INSTANCE), UnrevealedFriend.INSTANCE, List.of(first, second),
        List.of());
    assertTrue(new PlayFacts(view).cards().isVoid(view.context().declarer(), Suit.HEART));
    assertEquals(1, policy.choose(view).card().points());
  }

  @ParameterizedTest
  @EnumSource(value = Trump.class, names = {"HEART", "NO_TRUMP"})
  void retainsLowCardsSupportingDeclaredDiamondDefenseAfterOneLead(Trump trump) {
    var first = row(1, trump, p(0, "D10"), p(1, "D2"), p(2, "D3"), p(3, "D4"), p(4, "D5"));
    View view = view(1, 0, trump, 17, "DA D6 D7 HA HK HQ HJ SK SQ", "",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(first), List.of(p(0, "CA")));
    var decision = policy.recommend(view);
    assertNotEquals(Suit.DIAMOND, ((StandardCard) decision.action().card()).suit(),
        decision.toString()
            + " / 받침 보존=" + new CardResources(view, new PlayFacts(view)).reserved(card("D6")));
  }

  @ParameterizedTest
  @EnumSource(value = Role.class, names = {"DECLARER", "FRIEND", "OPPOSITION"})
  void spendsBlockedPointBeforeLastTrickForEveryRole(Role role) {
    var current = List.of(p(0, "CA"));
    int declarer = role == Role.DECLARER ? 1 : 4;
    FriendSelection selected =
        role == Role.FRIEND ? new CardFriend(Joker.INSTANCE) : new CardFriend(card("DA"));
    FriendStatus friend = new RevealedFriend(new mighty.game.Seat(role == Role.FRIEND ? 1 : 0));
    for (String[] sample : new String[][]{{"SJ", "S8", "SA"}, {"SQ", "S7", "SK"},
        {"S10", "S6", "SJ"}}) {
      String owned = sample[0] + " " + sample[1];
      var history = countedHistory(owned, "C2 C3 C4", "DA JK", sample[2] + " S2 S3", current, null);
      View view = view(1, declarer, Trump.SPADE, 17, owned,
          role == Role.DECLARER ? "C2 C3 C4" : "", selected, friend, history, current);
      assertEquals(role, view.role());
      assertEquals(card(sample[0]), policy.choose(view).card(), owned);
    }
  }

  @ParameterizedTest
  @ValueSource(strings = {"D2 H2", "D4 H6"})
  void keepsMightyAvailableForWaterEvenWithFourConnectedOrdinarySpades(String water) {
    View view = view(0, 0, Trump.NO_TRUMP, 17, "SK SQ SJ S10 S9 S8 S7 JK " + water, "C2 C3 C4",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(), List.of());
    assertNotEquals(Suit.SPADE, ((StandardPlay) policy.choose(view)).card().suit());
  }

  @ParameterizedTest
  @ValueSource(strings = {"D2", "H2", "S2"})
  void callsMightyWithWaterWhenEightCardPrimaryNeedsNoEarlyHelp(String water) {
    View view = view(0, 0, Trump.NO_TRUMP, 17, "CA CK CQ C5 C6 C7 C8 C9 JK " + water, "C2 C3 C4",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(), List.of());
    assertEquals(card(water), policy.choose(view).card());
  }

  @Test
  void ownJokerAndGunStillAllowMightyToHandleSideWater() {
    View view = view(0, 0, Trump.NO_TRUMP, 17, "CA CK CQ C3 C5 C6 C7 C8 JK D2", "D3 H3 H4",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(), List.of());
    assertEquals(card("D2"), policy.choose(view).card());
  }
}
