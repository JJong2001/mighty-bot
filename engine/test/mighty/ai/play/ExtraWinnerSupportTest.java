package mighty.ai.play;

import static mighty.ai.play.PlayTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import mighty.ai.play.declarer.DeclarerStrategy;
import mighty.game.Seat;
import mighty.game.bidding.Bid;
import mighty.game.bidding.BidOffer;
import mighty.game.bidding.PublicBid;
import mighty.game.friend.CardFriend;
import mighty.game.friend.RevealedFriend;
import mighty.game.friend.UnrevealedFriend;
import mighty.game.observation.Context;
import mighty.game.observation.View;
import mighty.model.Joker;
import mighty.model.Trump;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ExtraWinnerSupportTest {
  private static final Trump NT = Trump.NO_TRUMP;
  private static final CardFriend FRIEND = new CardFriend(card("SA"));

  private static View withReturn(View v) {
    var c = v.context();
    var heart = new Bid(v.contract().points(), Trump.HEART);
    var ctx = new Context(c.playOrder(), c.declarer(), c.friendStatus(), c.history(),
        List.of(heart, v.contract()), List.of(new PublicBid(c.declarer(), new BidOffer(heart))));
    return new View(v.self(), v.privateInfo(), v.role(), v.contract(), v.friendSelection(), ctx, v.trick());
  }

  @ParameterizedTest
  @CsvSource({"D3, DA", "C8, H3"})
  void suitedFriendCanCashSideTopBeforeTwoTrumpRoundsUnlessThereIsARuffRisk(
      String oppositionPlay, String expected) {
    var suit = Trump.HEART;
    var won = row(1, suit, p(0, "D2"), p(1, "SA"), p(2, oppositionPlay), p(3, "D6"), p(4, "D7"));
    var current = view(1, 0, suit, 16, "DA H2 D8 D9 C6 C5 S4 S3 H3", "", FRIEND,
        new RevealedFriend(new Seat(1)), List.of(won), List.of());
    assertEquals(card(expected), new RulePlayPolicy().choose(current).card());
  }

  @Test
  void noTrumpFriendCanCashWithoutAPublicReturnAnnouncement() {
    var won = row(1, NT, p(0, "D2"), p(1, "SA"), p(2, "D3"), p(3, "D7"), p(4, "D9"));
    var current = view(1, 0, NT, 16, "CA H8 H4 C7 S4 S3 S2 H3 C6", "", FRIEND,
        new RevealedFriend(new Seat(1)), List.of(won), List.of());
    assertEquals(card("CA"), new RulePlayPolicy().choose(current).card());
  }

  @ParameterizedTest
  @CsvSource({"15, CA, CA", "16, SK, SK", "19, SQ, H8"})
  void usesIndependentTopEvenWithoutEarlierDeclarerWinners(int points, String held, String expect) {
    var handoff = row(1, NT, p(0, "D2"), p(1, "SA"), p(2, "D3"), p(3, "D7"), p(4, "D9"));
    var current = withReturn(view(1, 0, NT, points, held + " H8 H4 C7 S4 S3 S2 H3 C6", "",
        FRIEND, new RevealedFriend(new Seat(1)), List.of(handoff), List.of()));
    assertEquals(card(expect), new RulePlayPolicy().choose(current).card());
  }

  @Test
  void jokerFriendCashesAnotherAceButKeepsSpadeKingWhileMightyIsOutside() {
    var first = row(1, NT, p(0, "DA"), p(1, "D8"), p(2, "D4"), p(3, "D6"), p(4, "D10"));
    var second = row(2, NT, p(0, "D2"), p(1, "JK"), p(2, "D3"), p(3, "D7"), p(4, "D9"));
    var friend = new CardFriend(Joker.INSTANCE);
    var revealed = new RevealedFriend(new Seat(1));
    var history = List.of(first, second);
    var policy = new RulePlayPolicy();
    var cash = withReturn(view(1, 0, NT, 16, "CA H8 H4 C7 S4 S3 S2 H3", "",
        friend, revealed, history, List.of()));
    assertEquals(card("CA"), policy.choose(cash).card());
    var unsafe = withReturn(view(1, 0, NT, 16, "SK H8 H4 C7 S4 S3 S2 H3", "",
        friend, revealed, history, List.of()));
    assertEquals(card("H8"), policy.choose(unsafe).card());
  }

  @ParameterizedTest
  @CsvSource({"CA DA H8 D5 S4 S3 C6, DA", "CA C3 H8 D5 S4 S3 C6, CA"})
  void suitedSideTopProtectsTheUnplayedJokerCallCard(String owned, String expect) {
    var hearts = Trump.HEART;
    var history = List.of(
        row(1, hearts, p(0, "D2"), p(1, "SA"), p(2, "D3"), p(3, "D6"), p(4, "D7")),
        row(2, hearts, p(1, "HA"), p(2, "H2"), p(3, "H3"), p(4, "H4"), p(0, "H5")),
        row(3, hearts, p(1, "HK"), p(2, "H6"), p(3, "H7"), p(4, "H9"), p(0, "H10")));
    var current = view(1, 0, hearts, 16, owned, "", FRIEND, new RevealedFriend(new Seat(1)),
        history, List.of());
    assertEquals(card(expect), new RulePlayPolicy().choose(current).card());
  }

  @ParameterizedTest
  @ValueSource(ints = {17, 18, 19, 20})
  void comparesCashingSideTopsAndThenTransferring(int points) {
    var first = row(1, NT, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var second = row(2, NT, p(0, "CK"), p(1, "C6"), p(2, "C7"), p(3, "C8"), p(4, "C9"));
    var hand = "HA HK HQ HJ H10 JK D2 S4";
    var before = withReturn(view(0, 0, NT, points, "CK " + hand, "D4 D5 D6", FRIEND,
        UnrevealedFriend.INSTANCE, List.of(first), List.of()));
    var after = withReturn(view(0, 0, NT, points, hand, "D4 D5 D6", FRIEND,
        UnrevealedFriend.INSTANCE, List.of(first, second), List.of()));
    var strategy = new DeclarerStrategy();
    var facts = new PlayFacts(before);
    assertEquals(card("CK"), strategy.spreadTopLead(before, facts, facts.legalActions())
        .orElseThrow().action().card());
    facts = new PlayFacts(after);
    assertEquals(card("D2"), strategy.spreadTopLead(after, facts, facts.legalActions())
        .orElseThrow().action().card());
  }

  @Test
  void comparesCashingMainTopsWithJokerReservedWhenSeveralLosersRemain() {
    var first = row(1, NT, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var current = withReturn(view(0, 0, NT, 20, "HA HK HQ HJ JK D2 D3 S3 S4", "D4 D5 D6",
        FRIEND, UnrevealedFriend.INSTANCE, List.of(first), List.of()));
    var facts = new PlayFacts(current);
    var candidate = new DeclarerStrategy().spreadTopLead(current, facts, facts.legalActions());
    assertEquals(card("HA"), candidate.orElseThrow().action().card());
  }

  @Test
  void shedsLoserOnExtraWinnerAndThenReturnsTheBidSuit() {
    var first = row(1, NT, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var second = row(2, NT, p(0, "CK"), p(1, "C6"), p(2, "C7"), p(3, "C8"), p(4, "C9"));
    var third = row(3, NT, p(0, "D2"), p(1, "SA"), p(2, "D3"), p(3, "D7"), p(4, "D9"));
    var friend = new RevealedFriend(new Seat(1));
    var extra = List.of(p(1, "CQ"), p(2, "CJ"), p(3, "C10"), p(4, "S8"));
    var current = withReturn(view(0, 0, NT, 20, "HA HK HQ HJ H10 JK S4", "D4 D5 D6",
        FRIEND, friend, List.of(first, second, third), extra));
    assertEquals(card("S4"), new RulePlayPolicy().choose(current).card());
    var fourth = row(4, NT, extra.get(0), extra.get(1), extra.get(2), extra.get(3), p(0, "S4"));
    var returning = withReturn(view(1, 0, NT, 20, "DA H8 H4 S3 S2 H3", "",
        FRIEND, friend, List.of(first, second, third, fourth), List.of()));
    assertEquals(card("H8"), new RulePlayPolicy().choose(returning).card());
  }

  @ParameterizedTest
  @CsvSource({"CQ,CQ", "CJ,H8", "DA,DA"})
  void checksOwnExtraWinnerBeforeReturning(String held, String expected) {
    var first = row(1, NT, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5"));
    var second = row(2, NT, p(0, "CK"), p(1, "C6"), p(2, "C7"), p(3, "C8"), p(4, "C9"));
    var third = row(3, NT, p(0, "D2"), p(1, "SA"), p(2, "D3"), p(3, "D7"), p(4, "D9"));
    var friend = new RevealedFriend(new Seat(1));
    var current = withReturn(view(1, 0, NT, 20, held + " H8 H4 S4 S3 S2 H3", "",
        FRIEND, friend, List.of(first, second, third), List.of()));
    assertEquals(card(expected), new RulePlayPolicy().choose(current).card());
  }
}