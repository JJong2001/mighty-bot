package mighty.ai.play;

import static mighty.ai.play.PlayTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import mighty.ai.preparation.PreparationPlanner;
import mighty.data.CardParser;
import mighty.game.Seat;
import mighty.game.bidding.Bid;
import mighty.game.bidding.BidOffer;
import mighty.game.bidding.PublicBid;
import mighty.game.friend.CardFriend;
import mighty.game.friend.RevealedFriend;
import mighty.game.friend.UnrevealedFriend;
import mighty.game.observation.DiscardView;
import mighty.game.observation.Context;
import mighty.game.observation.ObservationFactory;
import mighty.game.trick.PublicTrick;
import mighty.model.Card;
import mighty.model.Trump;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class SpreadNoTrumpTest {

  @Test
  void buriesTheUncontrolledSuitAndCashesSideTopsBeforeCalling() {
    int points = 15;
    var trump = Trump.NO_TRUMP;
    var contract = new Bid(points, trump);
    var cards = CardParser.parseList("SA SK SJ S3 HA H3 CA CK C7 JK D2 D4 D6");
    var plan = new PreparationPlanner().choose(new DiscardView(new Seat(0), contract, cards));
    assertEquals(contract, plan.contract());
    assertEquals(Set.copyOf(CardParser.parseList("D2 D4 D6")), Set.copyOf(plan.discarded()));
    var selection = plan.friend();
    assertEquals(new CardFriend(card("SQ")), selection);
    var kept = new ArrayList<>(cards);
    kept.removeAll(plan.discarded());
    var buried = codes(plan.discarded());
    var history = new ArrayList<PublicTrick>();
    var policy = new RulePlayPolicy();
    String[][] plays = {
        {"CA", "C2", "C3", "C4", "C5"},
        {"CK", "C6", "C8", "C9", "C10"},
        {"HA", "H2", "H4", "H5", "H6"},
        {"S3", "SQ", "S4", "S5", "S2"}
    };
    for (var trick : plays) {
      var view = view(0, 0, trump, points, codes(kept), buried, selection,
          UnrevealedFriend.INSTANCE, history, List.of());
      var action = policy.choose(view);
      assertEquals(card(trick[0]), action.card());
      kept.remove(action.card());
      history.add(row(history.size() + 1, trump, p(0, trick[0]), p(1, trick[1]), p(2, trick[2]),
          p(3, trick[3]), p(4, trick[4])));
    }
    // 프렌드가 다이아 탑으로 돌아오면 스페이드 회수와 조커를 지키고 사이드 물패를 버립니다.
    var friend = new RevealedFriend(new Seat(1));
    var returnView = view(0, 0, trump, points, codes(kept), buried, selection,
        friend, history, List.of(p(1, "DA"), p(2, "D5"), p(3, "D7"), p(4, "D8")));
    assertTrue(Set.of(card("C7"), card("H3")).contains(policy.choose(returnView).card()));
    var cheap = view(0, 0, trump, points, codes(kept), buried, selection,
        friend, history, List.of(p(1, "D3"), p(2, "D5"), p(3, "D10"), p(4, "DJ")));
    assertTrue(Set.of(card("C7"), card("H3")).contains(policy.choose(cheap).card()));
    var costly = view(0, 0, trump, points, codes(kept), buried, selection,
        friend, history, List.of(p(1, "D3"), p(2, "D10"), p(3, "DJ"), p(4, "DQ")));
    assertEquals(card("JK"), policy.choose(costly).card());
    kept.remove(card("JK"));
    history.add(row(5, trump, p(1, "D3"), p(2, "D10"), p(3, "DJ"), p(4, "DQ"), p(0, "JK")));
    var regained = view(0, 0, trump, points, codes(kept), buried, selection,
        friend, history, List.of());
    assertTrue(Set.of(card("SA"), card("SK"), card("SJ")).contains(policy.choose(regained).card()));
  }

  @ParameterizedTest
  @ValueSource(ints = {16, 17})
  void choosesBetweenSideTopsAndCallingFriendByContractSuccess(int points) {
    var policy = new RulePlayPolicy();
    var friend = new CardFriend(card("SQ"));
    var sideTops = view(0, 0, Trump.NO_TRUMP, points, "SA SK S6 S2 HA HK H3 CA C3 JK",
        "D2 D4 D6", friend, UnrevealedFriend.INSTANCE, List.of(), List.of());
    assertEquals(card("S2"), policy.usualDecision(sideTops).action().card());
    assertEquals(card("HA"), policy.choose(sideTops).card());

    var callFriend = view(0, 0, Trump.NO_TRUMP, points, "SA SK SJ S3 HA H3 CA CK C7 JK",
        "D2 D4 D6", friend, UnrevealedFriend.INSTANCE, List.of(), List.of());
    assertEquals(card("CA"), policy.spreadTopContinuation(callFriend).action().card());
    assertEquals(card("S3"), policy.choose(callFriend).card());
  }

  @Test
  void cashesTheAceThenTransfersToTheBiddersPossibleKing() {
    var trump = Trump.NO_TRUMP;
    var contract = new Bid(18, trump);
    var self = new Seat(0);
    var auction = List.of(new PublicBid(new Seat(1), new BidOffer(new Bid(18, Trump.HEART))),
        new PublicBid(self, new BidOffer(contract)));
    var history = new ArrayList<>(List.of(
        row(1, trump, p(0, "CA"), p(1, "C2"), p(2, "C3"), p(3, "C4"), p(4, "C5")),
        row(2, trump, p(0, "CK"), p(1, "C6"), p(2, "C8"), p(3, "C9"), p(4, "C10")),
        row(3, trump, p(0, "SK"), p(1, "S2"), p(2, "S4"), p(3, "S5"), p(4, "S6"))));
    var held = new ArrayList<>(CardParser.parseList("SA SJ S3 HA H3 C7 JK"));
    var friend = new CardFriend(card("HK"));
    for (var expected : List.of("HA", "H3")) {
      var obs = view(0, 0, trump, 18, codes(held), "D2 D4 D6", friend,
          UnrevealedFriend.INSTANCE, history, List.of());
      var context = new Context(obs.context().playOrder(), self, UnrevealedFriend.INSTANCE,
          history, List.of(contract), auction);
      var turn = ObservationFactory.create(self, obs.privateInfo(), contract, friend, context, List.of());
      assertEquals(card(expected), new RulePlayPolicy().choose(turn).card());
      if (expected.equals("HA")) {
        held.remove(card("HA"));
        history.add(row(4, trump, p(0, "HA"), p(1, "H2"), p(2, "H4"), p(3, "H5"), p(4, "H6")));
      }
    }
  }

  private static String codes(List<Card> cards) {
    return cards.stream().map(Card::toCode).collect(Collectors.joining(" "));
  }
}
