package mighty.ai.play.opposition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;
import mighty.ai.play.PlayFacts;
import mighty.data.CardParser;
import mighty.game.GameMode;
import mighty.game.PlayOrder;
import mighty.game.Role;
import mighty.game.Seat;
import mighty.game.TableLayout;
import mighty.game.TrickNumber;
import mighty.game.action.JokerFollow;
import mighty.game.action.StandardPlay;
import mighty.game.bidding.Bid;
import mighty.game.friend.NoFriend;
import mighty.game.friend.SpecialFriend;
import mighty.game.observation.Context;
import mighty.game.observation.NonDeclarerInfo;
import mighty.game.observation.ObservationFactory;
import mighty.game.observation.View;
import mighty.game.trick.PublicPlay;
import mighty.game.trick.PublicTrick;
import mighty.model.Card;
import mighty.model.Rank;
import mighty.model.StandardCard;
import mighty.model.Suit;
import mighty.model.Trump;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class OppositionTrumpFollowTest {

  private static final List<Seat> SEATS = IntStream.range(0, 5).mapToObj(Seat::new).toList();
  private static final PlayOrder ORDER = PlayOrder.from(
      new TableLayout(GameMode.FIVE_PLAYER, SEATS), new HashSet<>(SEATS));
  private static final Bid CONTRACT = new Bid(15, Trump.DIAMOND);

  @Test
  @DisplayName("특수카드가 뒤 여당에 남으면 기루 두 장 중 T·J·Q를 희생한다")
  void sacrificesEachConfirmedIntermediateRankWhenThreatIsBehind() {
    for (String pressure : List.of("D10", "DJ", "DQ")) {
      View view = view("D5 " + pressure + " S2 S3 S4 H2 H3 H4 C2", new Seat(4),
          List.of(), SpecialFriend.NONE);
      PlayFacts facts = new PlayFacts(view);

      assertEquals(Role.OPPOSITION, view.role());
      assertTrue(facts.remainingSeats().contains(new Seat(3)));
      assertEquals(CardParser.parse(pressure),
          OppositionTrumpFollow.choose(view, facts, facts.legalActions()).action().card());
    }
  }

  @Test
  @DisplayName("일반 탑카는 보존하되 외부 상위에 막힌 짧은 점수는 압박한다")
  void preservesOrdinaryTopButPressuresBlockedShortHonor() {
    for (String honour : List.of("DA", "DK")) {
      View view = view(honour + " D5 S2 S3 S4 H2 H3 H4 C2", new Seat(4),
          List.of(), SpecialFriend.NONE);
      PlayFacts facts = new PlayFacts(view);

      assertEquals(facts.isSuitTop(CardParser.parse(honour)) ? CardParser.parse("D5")
              : CardParser.parse(honour),
          OppositionTrumpFollow.choose(view, facts, facts.legalActions()).action().card());
    }
  }

  @Test
  @DisplayName("마이티와 조커가 이미 소진됐다면 점수 기루를 희생하지 않는다")
  void doesNotSacrificeAfterBothSpecialsWerePlayed() {
    List<PublicPlay> firstPlays = List.of(
        play(0, "SA"), play(1, "H7"), play(2, "H8"), play(3, "H9"),
        new PublicPlay(new Seat(4), new JokerFollow()));
    var first = new PublicTrick(new TrickNumber(1), firstPlays, new Seat(0));
    View view = view("D10 D5 S2 S3 H2 H6 C2 C3 C4", new Seat(4), List.of(first),
        SpecialFriend.NONE);
    PlayFacts facts = new PlayFacts(view);

    assertEquals(CardParser.parse("D5"),
        OppositionTrumpFollow.choose(view, facts, facts.legalActions()).action().card());
  }

  @Test
  @DisplayName("특수카드 보유 가능성이 내 뒤가 아니라 이미 낸 주공에게만 있으면 희생하지 않는다")
  void doesNotSacrificeWhenDeclarerHasAlreadyPlayedThisTrick() {
    View view = view("D10 D5 S2 S3 S4 H2 H3 H4 C2", new Seat(3),
        List.of(previousTrick(new Seat(3))),
        SpecialFriend.NONE, List.of(play(3, "D2"), play(4, "H6"), play(0, "C6")));
    PlayFacts facts = new PlayFacts(view);

    assertTrue(facts.remainingSeats().stream().noneMatch(new Seat(0)::equals));
    assertEquals(CardParser.parse("D5"),
        OppositionTrumpFollow.choose(view, facts, facts.legalActions()).action().card());
  }

  private static View view(String hand, Seat declarer, List<PublicTrick> history,
      SpecialFriend selection) {
    List<PublicTrick> completed = history.isEmpty()
        ? List.of(previousTrick(new Seat(0))) : history;
    return view(hand, declarer, completed, selection, List.of(play(0, "D2")));
  }

  private static View view(String hand, Seat declarer, List<PublicTrick> history,
      SpecialFriend selection, List<PublicPlay> trick) {
    List<Card> cards = CardParser.parseList(hand);
    var privateInfo = new NonDeclarerInfo(cards);
    var context = new Context(ORDER, declarer, NoFriend.INSTANCE, history,
        List.of(CONTRACT));
    return ObservationFactory.create(new Seat(1), privateInfo, CONTRACT, selection, context, trick);
  }

  private static PublicTrick previousTrick(Seat winner) {
    return new PublicTrick(new TrickNumber(1), List.of(
        play(0, "C7"), play(1, "C8"), play(2, "H7"), play(3, "H8"), play(4, "S7")), winner);
  }

  private static PublicPlay play(int seat, String card) {
    return new PublicPlay(new Seat(seat),
        new StandardPlay((StandardCard) CardParser.parse(card), false));
  }
}
