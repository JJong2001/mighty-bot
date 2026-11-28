package mighty.game.bidding;

import static org.junit.jupiter.api.Assertions.*;

import mighty.game.GamePhase;
import mighty.model.Trump;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ContractChangeRulesTest {
  @Test
  void fifteenNoTrumpMayUpgradeToSeventeenAfterKitty() {
    var award = new Bid(15, Trump.NO_TRUMP);
    var raised = new Bid(17, Trump.NO_TRUMP);
    assertTrue(ContractChangeRules.canChange(award, award, raised, GamePhase.DISCARD));
    assertTrue(ContractChangeRules.canChange(award, award, raised, GamePhase.FRIEND));
    assertFalse(ContractChangeRules.canChange(award, award, raised, GamePhase.PLAY));
    assertFalse(ContractChangeRules.canChange(award, award,
        new Bid(16, Trump.NO_TRUMP), GamePhase.DISCARD));
    var suited = new Bid(15, Trump.CLUB);
    assertFalse(ContractChangeRules.canChange(suited, suited,
        new Bid(17, Trump.CLUB), GamePhase.DISCARD));
  }

  @Test
  @DisplayName("풀 낙찰은 20점을 유지하며 기루와 노기루를 반복 변경할 수 있다")
  void fullAwardAllowsRepeatedSuitChanges() {
    Bid award = new Bid(20, Trump.SPADE);
    assertTrue(
        ContractChangeRules.canChange(award, award, new Bid(20, Trump.DIAMOND), GamePhase.DISCARD));
    assertTrue(ContractChangeRules.canChange(award, new Bid(20, Trump.DIAMOND),
        new Bid(20, Trump.NO_TRUMP),
        GamePhase.FRIEND));
    assertFalse(
        ContractChangeRules.canChange(award, award, new Bid(19, Trump.HEART), GamePhase.FRIEND));
    assertFalse(
        ContractChangeRules.canChange(award, award, new Bid(20, Trump.HEART), GamePhase.PLAY));
  }

  @Test
  @DisplayName("바닥 전후 문양 변경에 필요한 공약 인상 폭을 구분한다")
  void respectsStageAndChangeDirection() {
    Bid award = new Bid(16, Trump.SPADE);
    assertTrue(ContractChangeRules.canChange(award, award, new Bid(16, Trump.NO_TRUMP),
        GamePhase.BEFORE_KITTY));
    assertFalse(ContractChangeRules.canChange(award, award, new Bid(16, Trump.NO_TRUMP),
        GamePhase.DISCARD));
    assertTrue(ContractChangeRules.canChange(award, award, new Bid(17, Trump.HEART),
        GamePhase.BEFORE_KITTY));
    assertFalse(
        ContractChangeRules.canChange(award, award, new Bid(17, Trump.HEART), GamePhase.DISCARD));
    assertTrue(ContractChangeRules.canChange(award, award, new Bid(17, Trump.NO_TRUMP),
        GamePhase.DISCARD));
    Bid noTrump = new Bid(16, Trump.NO_TRUMP);
    assertFalse(ContractChangeRules.canChange(noTrump, noTrump, new Bid(17, Trump.SPADE),
        GamePhase.DISCARD));
    assertTrue(ContractChangeRules.canChange(noTrump, noTrump, new Bid(18, Trump.SPADE),
        GamePhase.DISCARD));
  }

}
