package mighty.ai.play.friend;

import mighty.ai.play.*;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.*;

/**
 * 야당이 간친 문양·반복된 문양의 재반환을 막되 주공이 직접 요청한 문양은 구분합니다.
 */
public final class FriendReturnSafety {

  private FriendReturnSafety() {
  }

  /**
   * 주공이 운영 문양으로 공개한 경우에만 미소모 마이티 문양을 반환 후보에 둡니다.
   */
  static boolean risksMightyAttack(Suit suit, View view, PlayFacts facts) {
    var mighty = Rules.mighty(view.contract().trump());
    return suit == mighty.suit() && facts.cards().outsideCards().contains(mighty)
        && !facts.declarerSuits().assess(suit).beyondOpeningSignal()
        && !FriendSupportIntent.returnSuit(view).filter(suit::equals).isPresent();
  }

  public static boolean allows(Suit suit, View view, PlayFacts facts) {
    Card mighty = Rules.mighty(view.contract().trump());
    // 공개 주력 또는 지원 요청의 반환은 지원에 몇 구를 썼는지와 무관합니다.
    if (view.contract().trump() == Trump.NO_TRUMP
        && FriendSupportIntent.returnSuit(view).filter(suit::equals).isPresent()) {
      return true;
    }
    if (view.contract().trump() == Trump.NO_TRUMP && suit == Rules.mighty(Trump.NO_TRUMP).suit()
        && facts.cards().outsideCards().contains(mighty)
        && FriendSupportIntent.disclosedPrimary(view).filter(primary -> primary != suit).isPresent()
        && !facts.declarerSuits().assess(suit).beyondOpeningSignal()) {
      return false;
    }
    if (mighty instanceof StandardCard card && card.suit() == suit
        && facts.cards().outsideCards().contains(mighty)
        && (view.friendSelection() instanceof mighty.game.friend.CardFriend f
        && f.card() instanceof Joker
        || view.context().history().stream()
        .anyMatch(t -> t.leader().equals(view.context().declarer())
            && Rules.conditionsFor(view.contract().trump(), t.number(), t.cards().get(0).action())
            .leadSuit() == suit))) {
      return false;
    }
    if (!JokerSuitPlan.enemiesMayHoldTrump(view, facts) || view.contract().trump().matches(suit)) {
      return true;
    }
    boolean requested = view.context().history().stream().anyMatch(t ->
        t.leader().equals(view.context().declarer()) && Rules.conditionsFor(view.contract().trump(),
            t.number(), t.cards().get(0).action()).leadSuit() == suit);
    long rounds = view.context().history().stream()
        .filter(t -> Rules.conditionsFor(view.contract().trump(),
            t.number(), t.cards().get(0).action()).leadSuit() == suit).count();
    boolean ruffed = view.context().history().stream()
        .filter(t -> Rules.conditionsFor(view.contract().trump(),
            t.number(), t.cards().get(0).action()).leadSuit() == suit)
        .anyMatch(t -> t.cards().stream()
            .anyMatch(
                p -> !facts.teams().isAlly(p.seat()) && p.action().card() instanceof StandardCard c
                    && view.contract().trump().matches(c.suit())));
    int lastRequest = -1;
    int lastRuff = -1;
    for (int i = 0; i < view.context().history().size(); i++) {
      var trick = view.context().history().get(i);
      if (Rules.conditionsFor(view.contract().trump(), trick.number(),
          trick.cards().get(0).action()).leadSuit() != suit) {
        continue;
      }
      if (trick.leader().equals(view.context().declarer())
          && trick.cards().get(0).action() instanceof mighty.game.action.StandardPlay lead
          && !lead.call()) {
        lastRequest = i;
      }
      if (trick.cards().stream().anyMatch(p -> !facts.teams().isAlly(p.seat())
          && p.action().card() instanceof StandardCard c && view.contract().trump()
          .matches(c.suit()))) {
        lastRuff = i;
      }
    }
    return (!ruffed && rounds < 2) || (requested && lastRequest >= lastRuff
        && lastRequest == view.context().history().size() - 1);
  }

}
