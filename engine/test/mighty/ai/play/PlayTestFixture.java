package mighty.ai.play;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import java.util.stream.IntStream;
import mighty.data.CardParser;
import mighty.game.*;
import mighty.game.action.*;
import mighty.game.bidding.Bid;
import mighty.game.deal.DeckFactory;
import mighty.game.friend.*;
import mighty.game.observation.*;
import mighty.game.rule.Rules;
import mighty.game.trick.*;
import mighty.model.*;

/**
 * 공개 이력과 자기 패만으로 전략 검사 입력을 만듭니다.
 */
final class PlayTestFixture {

  private PlayTestFixture() {
  }

  static Card card(String code) {
    return CardParser.parse(code);
  }

  static PublicPlay p(int seat, String code) {
    return new PublicPlay(new Seat(seat), code.equals("JK") ? new JokerFollow()
        : new StandardPlay((StandardCard) card(code), false));
  }

  static PublicTrick row(int number, Trump trump, PublicPlay... plays) {
    var cards = List.of(plays);
    return new PublicTrick(new TrickNumber(number), cards,
        Rules.currentWinner(trump, new TrickNumber(number), cards).orElseThrow().seat());
  }

  static View view(int self, int declarer, Trump trump, int points, String owned,
      String buried,
      FriendSelection selection, FriendStatus friend, List<PublicTrick> history,
      List<PublicPlay> trick) {
    var hand = CardParser.parseList(owned);
    var discards = CardParser.parseList(buried);
    Set<Card> unique = new HashSet<>();
    List<Card> all = new ArrayList<>(hand);
    if (self == declarer) {
      all.addAll(discards);
    }
    history.forEach(t -> t.cards().forEach(play -> all.add(play.action().card())));
    trick.forEach(play -> all.add(play.action().card()));
    for (Card c : all) {
      assertTrue(unique.add(c), "중복: " + c);
    }
    var seats = IntStream.range(0, 5).mapToObj(Seat::new).toList();
    var order = PlayOrder.from(new TableLayout(GameMode.FIVE_PLAYER, seats), new HashSet<>(seats));
    PrivateInfo info =
        self == declarer ? new DeclarerInfo(hand, discards) : new NonDeclarerInfo(hand);
    return ObservationFactory.create(new Seat(self), info, new Bid(points, trump), selection,
        new Context(order, new Seat(declarer), friend, history), trick);
  }

  /**
   * 카운팅 검사 전용 공개 이력. 실제 승자·차례·중복은 검사하며 상대의 손패는 만들지 않습니다.
   */
  static List<PublicTrick> countedHistory(String owned, String buried, String known, String unseen,
      List<PublicPlay> current, PublicTrick last) {
    return countedHistory(Trump.SPADE, owned, buried, known, unseen, current, last);
  }

  static View withPriorContract(View view, Bid prior) {
    var c = view.context();
    var context = new Context(c.playOrder(), c.declarer(), c.friendStatus(), c.history(),
        List.of(prior, view.contract()), c.auction());
    return new View(view.self(), view.privateInfo(), view.role(), view.contract(),
        view.friendSelection(), context, view.trick());
  }

  static List<PublicTrick> countedHistory(Trump trump, String owned, String buried, String known,
      String unseen,
      List<PublicPlay> current, PublicTrick last) {
    int count = 10 - CardParser.parseList(owned).size();
    Set<Card> excluded = new HashSet<>(CardParser.parseList(owned + " " + buried + " " + unseen));
    current.forEach(play -> excluded.add(play.action().card()));
    if (last != null) {
      last.cards().forEach(play -> excluded.add(play.action().card()));
    }
    LinkedHashSet<Card> pool = new LinkedHashSet<>();
    pool.addAll(CardParser.parseList(known));
    pool.removeAll(excluded);
    DeckFactory.create().stream().filter(c -> !excluded.contains(c)).forEach(pool::add);
    List<Card> available = new ArrayList<>(pool);
    List<PublicTrick> history = new ArrayList<>();
    int automatic = count - (last == null ? 0 : 1);
    for (int number = 1; number <= automatic; number++) {
      List<PublicPlay> plays = new ArrayList<>();
      for (int seat = 0; seat < 5; seat++) {
        Card c = available.get((number - 1) * 5 + seat);
        Action a = c instanceof Joker ? (seat == 0 ? new JokerLead(Suit.SPADE) : new JokerFollow())
            : new StandardPlay((StandardCard) c, false);
        plays.add(new PublicPlay(new Seat(seat), a));
      }
      int winner = Rules.currentWinner(trump, new TrickNumber(number), plays).orElseThrow()
          .seat().index();
      if (winner != 0) {
        Action strongest = plays.get(winner).action();
        if (strongest instanceof JokerFollow) {
          strongest = new JokerLead(Suit.SPADE);
        }
        Action old = plays.get(0).action();
        if (old instanceof JokerLead) {
          old = new JokerFollow();
        }
        plays.set(winner, new PublicPlay(new Seat(winner), old));
        plays.set(0, new PublicPlay(new Seat(0), strongest));
      }
      Seat actual = Rules.currentWinner(trump, new TrickNumber(number), plays).orElseThrow()
          .seat();
      assertEquals(new Seat(0), actual);
      history.add(new PublicTrick(new TrickNumber(number), plays, actual));
    }
    if (last != null) {
      history.add(last);
    }
    return history;
  }

}
