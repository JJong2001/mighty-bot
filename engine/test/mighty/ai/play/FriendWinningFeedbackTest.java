package mighty.ai.play;

import static mighty.ai.play.PlayTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import mighty.game.Seat;
import mighty.game.action.StandardPlay;
import mighty.game.deal.DeckFactory;
import mighty.game.friend.CardFriend;
import mighty.game.friend.RevealedFriend;
import mighty.game.friend.UnrevealedFriend;
import mighty.game.trick.PublicPlay;
import mighty.model.Card;
import mighty.model.StandardCard;
import mighty.model.Suit;
import mighty.model.Trump;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class FriendWinningFeedbackTest {

  private final RulePlayPolicy policy = new RulePlayPolicy();

  @ParameterizedTest
  @CsvSource({"H9", "H10", "HQ"})
  void usesWinningNonTopInLastSeatBeforeMighty(String winning) {
    var first = row(1, Trump.HEART, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var v = view(4, 0, Trump.HEART, 16, "SA " + winning + " HA H2 D2 D3 C6 S2 S3", "",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(first),
        List.of(p(0, "H3"), p(1, "H4"), p(2, "H5"), p(3, "H6")));
    assertEquals(card(winning), policy.choose(v).card());
  }

  @Test
  void answersEnemyJokerDuringDeclarersTopSearch() {
    var h = List.of(row(1, Trump.HEART, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5")),
        row(2, Trump.HEART, p(0, "H10"), p(1, "H3"), p(2, "H4"), p(3, "H5"), p(4, "H6")));
    var v = view(4, 0, Trump.HEART, 16, "SA HJ H2 D2 D3 D4 C6 S2", "", new CardFriend(card("SA")),
        UnrevealedFriend.INSTANCE, h, List.of(p(0, "HK"), p(1, "JK"), p(2, "H7"), p(3, "H8")));
    assertEquals(card("SA"), policy.choose(v).card(), policy.recommend(v).reason());
  }

  @Test
  void winsWithOrdinaryTopBeforeDeclarerInsteadOfForcingHisRuff() {
    var history = List.of(
        row(1, Trump.HEART, p(4, "DA"), p(0, "D2"), p(1, "D3"), p(2, "D4"), p(3, "D5")),
        row(2, Trump.HEART, p(4, "C2"), p(0, "CA"), p(1, "C3"), p(2, "C4"), p(3, "C5")));
    var v = view(3, 4, Trump.HEART, 16, "SA DK D6 H2 H3 S2 S3 C6", "", new CardFriend(card("SA")),
        new RevealedFriend(new Seat(3)), history, List.of(p(0, "D7"), p(1, "D8"), p(2, "D9")));
    assertEquals(card("DK"), policy.choose(v).card());
  }

  @Test
  void friendTakesPromotedRunRatherThanLeavingItStranded() {
    String own = "S7 S9 C2";
    var trick = List.of(p(0, "S8"));
    var h = countedHistory(Trump.NO_TRUMP, own, "", "SA SK SQ SJ S10 JK", "S2", trick, null);
    var v = view(1, 0, Trump.NO_TRUMP, 17, own, "", new CardFriend(card("C2")),
        UnrevealedFriend.INSTANCE, h, trick);
    assertEquals(card("S9"), policy.choose(v).card());
    var used = new HashSet<Card>(v.publicCardHistory().cards());
    used.addAll(v.hand());
    used.add(card("S8"));
    var discards = DeckFactory.create().stream()
        .filter(c -> !used.contains(c) && c instanceof StandardCard a && a.suit() != Suit.SPADE)
        .limit(3).toList();
    var won = row(8, Trump.NO_TRUMP, p(0, "S8"), p(1, "S9"),
        new PublicPlay(new Seat(2), new StandardPlay((StandardCard) discards.get(0), false)),
        new PublicPlay(new Seat(3), new StandardPlay((StandardCard) discards.get(1), false)),
        new PublicPlay(new Seat(4), new StandardPlay((StandardCard) discards.get(2), false)));
    var continued = new ArrayList<>(h);
    continued.add(won);
    var next = view(1, 0, Trump.NO_TRUMP, 17, "S7 C2", "", new CardFriend(card("C2")),
        UnrevealedFriend.INSTANCE, continued, List.of());
    assertEquals(card("S7"), policy.choose(next).card());
  }
}
