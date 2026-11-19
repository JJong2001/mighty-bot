package mighty.game.rule;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import mighty.game.Seat;
import mighty.game.TrickNumber;
import mighty.game.action.Action;
import mighty.game.action.JokerFollow;
import mighty.game.action.JokerLead;
import mighty.game.action.StandardPlay;
import mighty.game.trick.PublicPlay;
import mighty.model.Card;
import mighty.model.Joker;
import mighty.model.Rank;
import mighty.model.StandardCard;
import mighty.model.Suit;
import mighty.model.Trump;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 선과 따라내기의 합법 행동을 검사한다. 후보의 전략적 우선순위는 검사하지 않는다.
 */
class LegalActionsTest {

  @Test
  @DisplayName("초구에 다른 문양이 있으면 기루로 리드할 수 없다")
  void firstTrickCannotLeadTrumpWhenOtherSuitExists() {
    StandardCard heartTwo = new StandardCard(Suit.HEART, Rank.TWO);
    StandardCard clubAce = new StandardCard(Suit.CLUB, Rank.ACE);
    LegalActionContext context = new LegalActionContext(
        List.of(heartTwo, clubAce),
        Trump.HEART,
        new TrickNumber(1),
        List.of()
    );
    List<Action> actions = LegalActions.legalActions(context);
    assertEquals(List.of(new StandardPlay(clubAce, false)), actions);
  }

  @Test
  @DisplayName("초구에 기루와 조커만 있으면 기루 리드와 조커의 네 문양 지정이 가능하다")
  void firstTrickCanLeadTrumpWhenHandContainsOnlyTrumpAndJoker() {
    StandardCard heartTwo = new StandardCard(Suit.HEART, Rank.TWO);
    StandardCard heartThree = new StandardCard(Suit.HEART, Rank.THREE);
    LegalActionContext context = new LegalActionContext(
        List.of(heartTwo, heartThree, Joker.INSTANCE),
        Trump.HEART,
        new TrickNumber(1),
        List.of()
    );
    List<Action> actions = LegalActions.legalActions(context);
    Set<Action> expected = Set.of(
        new StandardPlay(heartTwo, false),
        new StandardPlay(heartThree, false),
        new JokerLead(Suit.SPADE),
        new JokerLead(Suit.DIAMOND),
        new JokerLead(Suit.HEART),
        new JokerLead(Suit.CLUB)
    );
    // 후보 순서는 강제하지 않되, 같은 행동을 중복 생성하지 않는지도 확인한다.
    assertEquals(expected, Set.copyOf(actions));
    assertEquals(expected.size(), actions.size());
  }

  @Test
  @DisplayName("2구 이후 총 리드는 콜 선언 여부를 선택할 수 있다")
  void gunLeadAllowsChoosingWhetherToCall() {
    StandardCard clubThree = new StandardCard(Suit.CLUB, Rank.THREE);
    LegalActionContext context = new LegalActionContext(
        List.of(clubThree),
        Trump.NO_TRUMP,
        new TrickNumber(2),
        List.of()
    );
    List<Action> actions = LegalActions.legalActions(context);
    Set<Action> expected = Set.of(
        new StandardPlay(clubThree, false),
        new StandardPlay(clubThree, true)
    );
    assertEquals(expected, Set.copyOf(actions));
    assertEquals(expected.size(), actions.size());
  }

  @Test
  @DisplayName("리드 문양이 있으면 다른 문양은 마이티를 제외하고 낼 수 없다")
  void followsLedSuitWithMightyAndJokerExceptions() {
    StandardCard heartTwo = new StandardCard(Suit.HEART, Rank.TWO);
    StandardCard diamondTwo = new StandardCard(Suit.DIAMOND, Rank.TWO);
    StandardCard mighty = new StandardCard(Suit.SPADE, Rank.ACE);
    assertFollowingActions(
        List.of(heartTwo, diamondTwo, mighty, Joker.INSTANCE),
        Trump.DIAMOND,
        new StandardPlay(new StandardCard(Suit.HEART, Rank.NINE), false),
        Set.of(
            new StandardPlay(heartTwo, false),
            new StandardPlay(mighty, false),
            new JokerFollow()
        )
    );
  }

  @Test
  @DisplayName("리드 문양이 없으면 모든 손패 카드를 낼 수 있다")
  void allowsOtherSuitsWhenLedSuitIsAbsent() {
    StandardCard diamondTwo = new StandardCard(Suit.DIAMOND, Rank.TWO);
    StandardCard clubTwo = new StandardCard(Suit.CLUB, Rank.TWO);
    assertFollowingActions(
        List.of(diamondTwo, clubTwo, Joker.INSTANCE),
        Trump.DIAMOND,
        new StandardPlay(new StandardCard(Suit.HEART, Rank.NINE), false),
        Set.of(
            new StandardPlay(diamondTwo, false),
            new StandardPlay(clubTwo, false),
            new JokerFollow()
        )
    );
  }

  @Test
  @DisplayName("콜을 받고 조커가 있으면 조커 또는 마이티만 낼 수 있다")
  void callRestrictsJokerHolderToJokerOrMighty() {
    StandardCard clubTwo = new StandardCard(Suit.CLUB, Rank.TWO);
    StandardCard mighty = new StandardCard(Suit.SPADE, Rank.ACE);
    assertFollowingActions(
        List.of(clubTwo, mighty, Joker.INSTANCE),
        Trump.DIAMOND,
        new StandardPlay(new StandardCard(Suit.CLUB, Rank.THREE), true),
        Set.of(new JokerFollow(), new StandardPlay(mighty, false))
    );
  }

  @Test
  @DisplayName("콜을 받아도 조커가 없으면 일반 따라내기 규칙을 적용한다")
  void callDoesNotRestrictPlayerWithoutJoker() {
    StandardCard clubTwo = new StandardCard(Suit.CLUB, Rank.TWO);
    StandardCard heartTwo = new StandardCard(Suit.HEART, Rank.TWO);
    StandardCard mighty = new StandardCard(Suit.SPADE, Rank.ACE);
    assertFollowingActions(
        List.of(clubTwo, heartTwo, mighty),
        Trump.DIAMOND,
        new StandardPlay(new StandardCard(Suit.CLUB, Rank.THREE), true),
        Set.of(
            new StandardPlay(clubTwo, false),
            new StandardPlay(mighty, false)
        )
    );
  }

  /**
   * 공통 준비만 묶고, 각 검사의 기대 행동은 호출부에서 명시한다.
   */
  private static void assertFollowingActions(
      List<Card> hand,
      Trump trump,
      Action opening,
      Set<Action> expected
  ) {
    LegalActionContext context = new LegalActionContext(
        hand,
        trump,
        new TrickNumber(2),
        List.of(new PublicPlay(new Seat(0), opening))
    );
    List<Action> actual = LegalActions.legalActions(context);
    assertEquals(expected, Set.copyOf(actual));
    assertEquals(expected.size(), actual.size());
  }

}
