package mighty.ai.play;

import static mighty.ai.play.PlayTestFixture.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import mighty.game.Seat;
import mighty.game.action.Action;
import mighty.game.bidding.Bid;
import mighty.game.bidding.BidOffer;
import mighty.game.bidding.PublicBid;
import mighty.game.friend.CardFriend;
import mighty.game.friend.RevealedFriend;
import mighty.game.friend.UnrevealedFriend;
import mighty.game.observation.Context;
import mighty.game.observation.View;
import mighty.model.Trump;
import org.junit.jupiter.api.Test;

class HonorGambleTest {

  private View opening(String honors, List<PublicBid> auction) {
    var base = view(3, 0, Trump.CLUB, 18, honors + " SA D3 D4 C4 C5 H7 H8 S6", "",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(),
        List.of(p(0, "H2"), p(1, "H3"), p(2, "HJ")));
    var ctx = base.context();
    return new View(base.self(), base.privateInfo(), base.role(), base.contract(),
        base.friendSelection(), new Context(ctx.playOrder(), ctx.declarer(), ctx.friendStatus(),
        ctx.history(), List.of(new Bid(16, Trump.NO_TRUMP), base.contract()), auction), base.trick());
  }

  private java.util.Optional<Action> candidate(View view) {
    var facts = new PlayFacts(view);
    return HonorGamble.candidate(view, facts, facts.legalActions());
  }

  @Test
  void friendKeepsSeparateSpadeInsuranceAndRequiresKingEvidenceForQueen() {
    assertEquals(card("HK"), candidate(opening("HK SQ", List.of())).orElseThrow().card());
    assertTrue(candidate(opening("HK S7", List.of())).isEmpty());
    assertTrue(candidate(opening("HQ SK", List.of())).isEmpty());
    var earlier = new PublicBid(new Seat(0), new BidOffer(new Bid(16, Trump.HEART)));
    assertEquals(card("HQ"), candidate(opening("HQ SK", List.of(earlier))).orElseThrow().card());
  }

  @Test
  void recommendsInsuredHonorsInDifficultConvertedContract() {
    assertChosenHonor("SA SQ HK D6 C3 C4 C7 DQ C6 D8", "SA");
    assertChosenHonor("SA SQ HK S9 S3 C5 C4 C10 CJ DA", "HK");
    assertChosenHonor("SA SQ HQ D5 S7 C3 C5 S4 CQ S5", "HQ");
    assertEquals(card("SA"), new RulePlayPolicy().choose(opening("HK S7", List.of())).card());
  }

  private void assertChosenHonor(String hand, String expected) {
    var bid = new PublicBid(new Seat(0), new BidOffer(new Bid(16, Trump.HEART)));
    var template = opening("HK SQ", List.of(bid));
    var v = new View(template.self(), new mighty.game.observation.NonDeclarerInfo(
        mighty.data.CardParser.parseList(hand)), template.role(), template.contract(),
        template.friendSelection(), template.context(), template.trick());
    assertEquals(card(expected), new RulePlayPolicy().choose(v).card());
  }

  @Test
  void keepsGuaranteedJokerWhenHonorsFailConfirmation() {
    var first = row(1, Trump.CLUB, p(0, "D2"), p(1, "D4"), p(2, "SA"), p(3, "D6"), p(4, "D7"));
    var trick = List.of(p(2, "C3"), p(3, "C5"), p(4, "CJ"));
    var friend = new CardFriend(card("SA"));
    var revealed = new RevealedFriend(new Seat(2));
    var king = view(0, 0, Trump.CLUB, 16, "CK C7 JK C6 C10 HA D10 H6 SQ", "S4 S5 H2",
        friend, revealed, List.of(first), trick);
    var queen = view(0, 0, Trump.CLUB, 18, "CQ C7 JK C6 C10 HA DJ DQ S9", "S4 S5 H2",
        friend, revealed, List.of(first), trick);
    assertEquals(card("CK"), candidate(king).orElseThrow().card());
    assertEquals(card("JK"), new RulePlayPolicy().choose(king).card());
    assertEquals(card("CQ"), candidate(queen).orElseThrow().card());
    assertEquals(card("JK"), new RulePlayPolicy().choose(queen).card());
  }

  @Test
  void triesKingWhenLowTrumpYieldsTheLeadAndJokerIsStillExposedToMighty() {
    var first = row(1, Trump.CLUB, p(1, "D7"), p(2, "DJ"), p(3, "DA"), p(4, "D5"), p(0, "D4"));
    var position = view(1, 1, Trump.CLUB, 15, "JK CQ CK S4 C4 C9 S10 S9 S2", "H3 D6 H8",
        new CardFriend(card("SA")), UnrevealedFriend.INSTANCE, List.of(first),
        List.of(p(3, "CJ"), p(4, "C3"), p(0, "C7")));
    var policy = new RulePlayPolicy();
    assertEquals(card("C4"), policy.usualDecision(position).action().card());
    assertEquals(card("CK"), policy.choose(position).card());
  }
  @Test
  void confirmedSuccessesMustAlsoPayForLossesAcrossTheSameDeals() {
    var costlyLosses = new HonorGambleTrial.Results();
    var noisyGain = new HonorGambleTrial.Results();
    var reliableGain = new HonorGambleTrial.Results();
    for (int i = 0; i < 400; i++) {
      boolean controlWon = i < 20;
      boolean gambleWon = i < 26;
      costlyLosses.add(controlWon, gambleWon, i >= 20 && i < 26 ? 30 : -1);
      noisyGain.add(controlWon, gambleWon, i == 0 ? 40 : 0);
      reliableGain.add(controlWon, gambleWon, i >= 20 && i < 26 ? 30 : 0);
    }
    assertFalse(costlyLosses.confirms());
    assertFalse(noisyGain.confirms());
    assertTrue(reliableGain.confirms());
  }
  @Test
  void onlyGamblesWhenTheUsualLineStrugglesAndBothMeasuresImprove() {
    assertFalse(HonorGamble.improvesHardContract(24, 12, 20, 25));
    assertFalse(HonorGamble.improvesHardContract(24, 8, 9, 10));
    assertFalse(HonorGamble.improvesHardContract(24, 5, 9, -1));
    assertTrue(HonorGamble.improvesHardContract(24, 8, 12, 10));
  }
}
