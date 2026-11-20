package mighty.ai.play;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import mighty.ai.play.PlayPolicyCharacterizationTest.Scenario;
import mighty.data.CardParser;
import mighty.game.Role;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.Joker;
import mighty.model.Suit;
import mighty.model.Trump;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 특정 공개 상황에서 확인받은 선택 의도를 검사합니다. 전체 대국 회귀와 이유 문구는 별도입니다.
 */
class PlayPolicyIntentTest {

  @Test
  @DisplayName("주공이 클로버를 비웠고 공약 경계면 특수카드 소진 뒤에도 십 기루를 선출한다")
  void leadsPointCardAtDeclarerFailureBoundaryAfterSpecialsAreSpent() {
    for (boolean sixSeats : new boolean[] {false, true}) {
      for (int friend : new int[] {1, 2}) {
        View view = new Scenario(Trump.SPADE, friend, sixSeats, 29).viewAtTurn(36);
        PlayFacts facts = new PlayFacts(view);

        assertEquals(Role.OPPOSITION, view.role());
        assertTrue(facts.cards().isVoid(view.context().declarer(), Suit.CLUB));
        assertTrue(view.publicCardHistory().hasBeenPlayed(Rules.mighty(Trump.SPADE)));
        assertTrue(view.publicCardHistory().hasBeenPlayed(Joker.INSTANCE));
        assertEquals(CardParser.parse("C10"), new RulePlayPolicy().choose(view).card());
      }
    }
  }

  @Test
  @DisplayName("주공이 하트를 비운 실패 경계에서는 하트 3 대신 퀸으로 점수를 압박한다")
  void leadsPointCardInDeclarerVoidSuitAtFailureBoundary() {
    for (boolean sixSeats : new boolean[] {false, true}) {
      View view = new Scenario(Trump.CLUB, 1, sixSeats, 11).viewAtTurn(31);
      PlayFacts facts = new PlayFacts(view);

      assertEquals(Role.OPPOSITION, view.role());
      assertTrue(facts.cards().isVoid(view.context().declarer(), Suit.HEART));
      assertTrue(view.publicCardHistory().hasBeenPlayed(Joker.INSTANCE));
      assertEquals(CardParser.parse("HQ"), new RulePlayPolicy().choose(view).card());
    }
  }

  @Test
  @DisplayName("기루 두 장 중 K는 예외로 보존하고 낮은 기루를 낸다")
  void preservesKingWhenTwoTrumpsAndSpecialThreatRemainsBehind() {
    for (boolean sixSeats : new boolean[] {false, true}) {
      View view = new Scenario(Trump.DIAMOND, 2, sixSeats, 3).viewAtTurn(7);

      assertTrue(view.hand().contains(CardParser.parse("DK")));
      assertTrue(view.hand().contains(CardParser.parse("D6")));
      assertEquals(CardParser.parse("D6"), new RulePlayPolicy().choose(view).card());
    }
  }
}
