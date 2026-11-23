package mighty.ai.play;

import static mighty.ai.play.PlayTestFixture.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import mighty.game.Seat;
import mighty.game.action.JokerLead;
import mighty.game.friend.CardFriend;
import mighty.game.friend.RevealedFriend;
import mighty.game.observation.View;
import mighty.game.trick.PublicPlay;
import mighty.model.Suit;
import mighty.model.Trump;
import org.junit.jupiter.api.Test;

class LastTrumpSupportTest {

  @Test
  void keepsTheLastOutsideTrumpInPlayWhenAFriendRuffCanSaveTheContract() {
    var view = position(19);
    var policy = new RulePlayPolicy();
    assertEquals(card("HK"), policy.usualDecision(view).action().card());
    assertEquals(card("C8"), policy.choose(view).card());
  }

  @Test
  void drawsTheLastOutsideTrumpWhenNormalPlayCanMeetTheContract() {
    var view = position(13);
    var policy = new RulePlayPolicy();
    assertEquals(card("HK"), policy.usualDecision(view).action().card());
    assertEquals(card("HK"), policy.choose(view).card());
  }

  private static View position(int points) {
    var trump = Trump.HEART;
    var friend = new Seat(2);
    // 남은 H7의 위치는 공개되지 않았고 프렌드가 클로버를 가지고 있는지도 아직 모릅니다.
    var history = List.of(
        row(1, trump, p(0, "C2"), p(1, "C10"), p(2, "SA"), p(3, "C6"), p(4, "C4")),
        row(2, trump, new PublicPlay(friend, new JokerLead(Suit.HEART)), p(3, "H3"), p(4, "H5"),
            p(0, "H9"), p(1, "H8")),
        row(3, trump, p(2, "H4"), p(3, "D4"), p(4, "H6"), p(0, "HA"), p(1, "S5")));
    return view(0, 0, trump, points, "HK HQ HJ H10 DQ D3 C8", "H2 S2 S3",
        new CardFriend(card("SA")), new RevealedFriend(friend), history, List.of());
  }
}
