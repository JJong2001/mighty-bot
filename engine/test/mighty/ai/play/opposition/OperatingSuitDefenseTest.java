package mighty.ai.play.opposition;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;
import mighty.ai.play.*;
import mighty.data.CardParser;
import mighty.game.*;
import mighty.game.action.StandardPlay;
import mighty.game.action.JokerFollow;
import mighty.game.bidding.Bid;
import mighty.game.friend.*;
import mighty.game.observation.*;
import mighty.game.rule.Rules;
import mighty.game.trick.*;
import mighty.model.*;
import org.junit.jupiter.api.Test;

class OperatingSuitDefenseTest {

  private static PublicPlay play(int seat, String code) {
    return new PublicPlay(new Seat(seat),
        new StandardPlay((StandardCard) CardParser.parse(code), false));
  }

  private static View view(String hand, List<PublicTrick> history, String lead) {
    var seats = IntStream.range(0, 5).mapToObj(Seat::new).toList();
    var order = PlayOrder.from(new TableLayout(GameMode.FIVE_PLAYER, seats), new HashSet<>(seats));
    return ObservationFactory.create(new Seat(1), new NonDeclarerInfo(CardParser.parseList(hand)),
        new Bid(17, Trump.NO_TRUMP), new CardFriend(Joker.INSTANCE),
        new Context(order, new Seat(0), UnrevealedFriend.INSTANCE, history),
        List.of(play(0, lead)));
  }

  private static java.util.Optional<PlayDecision> preserve(View view) {
    var facts = new PlayFacts(view);
    return OppositionPressure.preserveOperatingDefense(view, facts, facts.legalActions());
  }

  @Test
  void weakPointsAreSupportedInsteadOfProtectedAsDefense() {
    for (String suit : List.of("H", "D", "C")) {
      String filler = switch (suit) {
        case "H" -> "S2 S3 S4 D2 D3 D4 C2 C4";
        case "D" -> "S2 S3 S4 H2 H3 H4 C2 C4";
        default -> "S2 S3 S4 H2 H3 H4 D2 D4";
      };
      var view = view(suit + "J " + suit + "5 " + filler, List.of(), suit + "K");
      assertTrue(preserve(view).isEmpty(), "상위와 특수카드를 버틸 수 없는 J5는 수비가 아닙니다.");
      assertEquals(CardParser.parse(suit + "J"), new RulePlayPolicy().choose(view).card());
    }
  }

  @Test
  void waterOnlyDoesNotActivateDefenseException() {
    var view = view("H8 H5 S2 S3 S4 D2 D3 D4 C2 C4", List.of(), "HK");
    assertTrue(preserve(view).isEmpty());
  }

  @Test
  void enoughLengthToSurviveBlockersProtectsPoints() {
    var view = view("HQ HJ H8 H5 S2 S3 D2 D3 C2 C4", List.of(), "HK");
    assertEquals(CardParser.parse("H5"), preserve(view).orElseThrow().action().card());
    assertEquals(CardParser.parse("H5"), new RulePlayPolicy().choose(view).card());
  }

  @Test
  void protectionUsesOperatingEvidenceAfterOpeningToo() {
    var plays = List.of(play(0, "HQ"), play(1, "H2"), play(2, "H3"), play(3, "H4"), play(4, "H6"));
    var first = new PublicTrick(new TrickNumber(1), plays, new Seat(0));
    var view = view("HK H10 H8 H5 S2 S3 D2 D3 C2", List.of(first), "HJ");
    assertEquals(CardParser.parse("H5"), preserve(view).orElseThrow().action().card());
    assertTrue(new PlayFacts(view).declarerSuits().assess(Suit.HEART).reinforced());
  }

  @Test
  void immediateTwoPointCaptureIsNotBlocked() {
    var view = view("HA HQ H8 H5 S2 S3 D2 D3 C2 C4", List.of(), "HK");
    var context = view.context();
    var next = ObservationFactory.create(new Seat(2), view.privateInfo(), view.contract(),
        view.friendSelection(),
        context, List.of(play(0, "HK"), play(1, "HJ")));
    assertTrue(preserve(next).isEmpty());
    assertEquals(CardParser.parse("HA"), new RulePlayPolicy().choose(next).card());
  }

  @Test
  void currentOpeningEvidenceIsSharedWithFriendResources() {
    var opposition = view("HQ HJ H8 H5 S2 S3 D2 D3 C2 C4", List.of(), "HK");
    var friend = view("HQ HJ H8 H5 S2 S3 D2 D3 C2 JK", List.of(), "HK");
    assertEquals(Role.FRIEND, friend.role());
    var facts = new PlayFacts(friend);
    assertEquals(new PlayFacts(opposition).declarerSuits().assess(Suit.HEART),
        facts.declarerSuits().assess(Suit.HEART));
    assertTrue(DeclarerOperations.contains(friend, facts, Suit.HEART));
    assertFalse(facts.declarerSuits().assess(Suit.HEART).reinforced());
    assertFalse(facts.declarerSuits().assess(Suit.HEART).beyondOpeningSignal());
  }

  @Test
  void friendKeepsLongOpeningSuitQueenForFutureSupport() {
    var view = view("DQ D9 D5 D4 JK S2 S3 S9 HA H6", List.of(), "DA");
    assertEquals(Role.FRIEND, view.role());
    // 야당이 이 문양을 아직 따라낼 수 있으므로 역순 숫자까지 고정하지 않습니다.
    assertEquals(0, new RulePlayPolicy().choose(view).card().points());
  }

  @Test
  void plainLowOpeningAndSpecialLeadAreNotSideEvidence() {
    for (String lead : List.of("H2", "SA")) {
      var view = view("HQ HJ H8 H5 S2 S3 D2 D3 C2 C4", List.of(), lead);
      assertFalse(new PlayFacts(view).declarerSuits().assess(Suit.HEART).operating());
      assertTrue(preserve(view).isEmpty());
    }
  }

  @Test
  void shownVoidOverridesOldOpeningSignal() {
    var firstPlays = List.of(play(0, "HK"), play(1, "H2"), play(2, "HA"), play(3, "H3"),
        play(4, "H4"));
    var first = new PublicTrick(new TrickNumber(1), firstPlays, new Seat(2));
    var secondPlays = List.of(play(2, "H6"), play(3, "H7"), play(4, "H9"), play(0, "C3"),
        play(1, "H10"));
    var second = new PublicTrick(new TrickNumber(2), secondPlays, new Seat(1));
    var seats = IntStream.range(0, 5).mapToObj(Seat::new).toList();
    var order = PlayOrder.from(new TableLayout(GameMode.FIVE_PLAYER, seats), new HashSet<>(seats));
    var view = ObservationFactory.create(new Seat(1),
        new NonDeclarerInfo(CardParser.parseList("HQ HJ H8 H5 S2 S3 D2 JK")),
        new Bid(17, Trump.NO_TRUMP), new CardFriend(Joker.INSTANCE),
        new Context(order, new Seat(0), new RevealedFriend(new Seat(1)), List.of(first, second)),
        List.of());
    var assessment = new PlayFacts(view).declarerSuits().assess(Suit.HEART);
    assertTrue(assessment.shownVoid());
    assertFalse(assessment.operating());
    assertFalse(assessment.evidence().isEmpty(), "과거 근거는 남기되 현재 운영 판단은 철회합니다.");
  }

  @Test
  void promotedNineIsDefenseButItsLowerPaddingIsNotTheWinner() {
    var firstPlays = List.of(play(0, "SA"), play(1, "HA"), play(2, "HK"), play(3, "HQ"),
        play(4, "HJ"));
    var secondPlays = List.of(play(0, "H10"), new PublicPlay(new Seat(1), new JokerFollow()),
        play(2, "H2"), play(3, "H3"), play(4, "H4"));
    var seats = IntStream.range(0, 5).mapToObj(Seat::new).toList();
    var order = PlayOrder.from(new TableLayout(GameMode.FIVE_PLAYER, seats), new HashSet<>(seats));
    var view = ObservationFactory.create(new Seat(1),
        new NonDeclarerInfo(CardParser.parseList("H9 H8 S2 S3 D2 D3 C2 C4")),
        new Bid(17, Trump.NO_TRUMP), SpecialFriend.NONE,
        new Context(order, new Seat(0), NoFriend.INSTANCE, List.of(
            new PublicTrick(new TrickNumber(1), firstPlays, new Seat(0)),
            new PublicTrick(new TrickNumber(2), secondPlays, new Seat(1)))), List.of());
    var pressure = new DeclarerPressure(view, new PlayFacts(view));
    assertTrue(pressure.defense((StandardCard) CardParser.parse("H9")));
    assertFalse(pressure.defense((StandardCard) CardParser.parse("H8")));
  }

  @Test
  void defenseExceptionDoesNotSuppressSpecialCardCapture() {
    var plays = List.of(play(0, "HQ"), play(1, "H2"), play(2, "H3"), play(3, "H4"), play(4, "H6"));
    var first = new PublicTrick(new TrickNumber(1), plays, new Seat(0));
    var base = view("HK H10 H8 H5 S2 S3 D2 D3 JK", List.of(first), "HJ");
    var view = ObservationFactory.create(base.self(), base.privateInfo(), base.contract(),
        SpecialFriend.NONE,
        new Context(base.context().playOrder(), new Seat(0), NoFriend.INSTANCE, List.of(first)),
        base.trick());
    assertTrue(preserve(view).isEmpty());
  }
}
