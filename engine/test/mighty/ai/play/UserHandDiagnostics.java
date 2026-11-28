package mighty.ai.play;

import java.util.*;
import mighty.ai.bidding.*;
import mighty.ai.planning.*;
import mighty.ai.preparation.*;
import mighty.ai.evaluation.PreparedRoundEvaluator;
import mighty.data.CardParser;
import mighty.game.*;
import mighty.game.bidding.*;
import mighty.game.deal.*;
import mighty.game.observation.*;
import mighty.game.round.*;
import mighty.game.trick.PublicPlay;
import mighty.model.*;

/** 사용자 제보 진단. 상대 배분은 공개 정보가 아니라 재현 장치이며 봇에는 View만 전달. */
public class UserHandDiagnostics {
  static final List<Seat> SEATS = java.util.stream.IntStream.range(0, 5).mapToObj(Seat::new).toList();
  static final String PASS = "S2 S3 SJ DK DA C6 CJ H10 HA JK";
  static final String TWO = "D3 D4 C2 C6 C10 CA H2 H4 HK HA";
  static final String HELP = "S7 D5 D6 D7 DK DA C6 CJ CA JK";
  static final String SEVENTEEN = "S7 D2 D5 D7 D8 DJ DA C8 HA JK";

  public static void main(String[] args) throws Exception {
    if (args.length > 1) System.setOut(new java.io.PrintStream(
        java.nio.file.Files.newOutputStream(java.nio.file.Path.of(args[1])), true,
        java.nio.charset.StandardCharsets.UTF_8));
    if (args[0].equals("new-bid")) {
      bid("SPADE_FOUR", "S4 S8 SK SA DQ DK C5 H3 H8 HA", null);
      bid("SPADE_FOUR_VS15H", "S4 S8 SK SA DQ DK C5 H3 H8 HA", new Bid(15, Trump.HEART));
      var hand = BiddingHand.from(CardParser.parseList("S4 S8 SK SA DQ DK C5 H3 H8 HA"));
      var evaluator = new BidExpectedScoreEvaluator();
      for (int points : List.of(15, 16)) {
        System.out.println("PAIRED_32 " + points + "S="
            + evaluator.expectedDeclarerScore(hand, new Bid(points, Trump.SPADE)));
      }
    } else if (args[0].equals("bids")) {
      bid("PASS", PASS, null);
      bid("TWO", TWO, null);
      bid("HELP", HELP, null);
      bid("QJ_A", "C2 C3 C4 CQ CJ SA H2 H3 H4 H5", null);
      bid("QJ_B", "C7 C8 C9 CQ CJ SA H6 H7 H8 H9", null);
    } else if (args[0].equals("rivals")) {
      bid("PASS_VS15H", PASS, new Bid(15, Trump.HEART));
      bid("TWO_VS15H", TWO, new Bid(15, Trump.HEART));
      bid("HELP_VS16C", HELP, new Bid(16, Trump.CLUB));
      bid("QJ_VS16H", "C2 C3 C4 CQ CJ SA H2 H3 H4 H5", new Bid(16, Trump.HEART));
    } else if (args[0].equals("prep")) {
      var seen = CardParser.parseList("S5 S10 SQ SK SA D3 D6 D8 D10 H6 H8 H9 HK");
      var award = new Bid(15, Trump.NO_TRUMP);
      System.out.println("PREP START " + seen);
      var preparation = new PreparationPlanner().choose(new DiscardView(new Seat(0), award, seen));
      System.out.println("PREP " + preparation);
      var kept = new ArrayList<>(seen); kept.removeAll(preparation.discarded());
      System.out.println("KEPT " + kept);
      System.out.println("ASSESS AFTER " + new BiddingHandEvaluator().assess(BiddingHand.from(kept), preparation.discarded()));
      System.out.println("SCORE " + new PreparedRoundEvaluator().evaluate(award, seen, preparation, List.of()));
    } else if (args[0].equals("detail")) {
      var hand = BiddingHand.from(CardParser.parseList(HELP));
      var support = mighty.ai.planning.distribution.NoTrumpDistributionEvaluator.bestSupport(hand, List.of());
      System.out.println("HELP SUPPORT " + support);
      System.out.println("HELP QUALIFIES " + mighty.ai.planning.distribution.NoTrumpDistributionEvaluator.qualifyingContract(hand, support));
      System.out.println("HELP SWEEP " + NoTrumpPlanner.jokerSweepSuit(hand));
      for (int points : List.of(15,16)) {
        var bid = new Bid(points, Trump.CLUB);
        System.out.println("TWO SCORE " + bid + " " + new BidExpectedScoreEvaluator().expectedDeclarerScore(
            BiddingHand.from(CardParser.parseList(TWO)), bid));
      }
      var bid = new Bid(15, Trump.HEART);
      System.out.println("TWO SCORE " + bid + " " + new BidExpectedScoreEvaluator().expectedDeclarerScore(
          BiddingHand.from(CardParser.parseList(TWO)), bid));
    } else if (args[0].equals("play") || args[0].equals("play-alternative")) {
      for (int seed : List.of(0,1,2)) replay(SEVENTEEN, "S2 S3 H2", new Bid(17, Trump.NO_TRUMP), seed,
          args[0].equals("play-alternative"));
    }
  }

  static void bid(String label, String codes, Bid prior) {
    var cards = CardParser.parseList(codes);
    System.out.println("CASE " + label + " HAND=" + cards + " PRIOR=" + prior);
    var hand = BiddingHand.from(cards);
    for (var a : new BiddingHandEvaluator().assess(hand)) System.out.println("ASSESS " + a);
    System.out.println("HIGH " + HighNoTrumpPlan.find(hand));
    System.out.println("NTPLAN " + NoTrumpPlanner.analyze(hand));
    var state = BiddingState.start(new DealParticipants(DealFormat.FIVE_PLAYER_OPENING, SEATS), new Seat(0));
    if (prior != null) state = state.submit(new PublicBid(new Seat(0), new BidOffer(prior)));
    var self = state.currentSeat().orElseThrow();
    var choice = new RuleBiddingPolicy().choose(new BiddingView(self, cards, 0, state));
    System.out.println("CHOICE " + label + " " + choice.getClass().getSimpleName() + " " + choice);
  }

  static void replay(String codes, String buried, Bid bid, int seed, boolean alternative) {
    var own = CardParser.parseList(codes);
    var kitty = CardParser.parseList(buried);
    var pool = new ArrayList<>(DeckFactory.create()); pool.removeAll(own); pool.removeAll(kitty);
    Collections.shuffle(pool, new Random(seed));
    var round = PreparedRoundEvaluator.createRound(own, bid, kitty, pool);
    round.takeKitty(new Seat(0)); round.discard(new Seat(0), kitty);
    var friend = new RuleFriendPolicy().choose(round.friendView(new Seat(0)));
    round.chooseFriend(new Seat(0), friend);
    System.out.println("REPLAY seed=" + seed + " fixedKept=" + own + " syntheticBuried=" + kitty + " friend=" + friend);
    var policy = new RulePlayPolicy();
    while (round.phase() == GamePhase.PLAY) {
      var v = round.currentView(); var decision = policy.recommend(v);
      if (alternative && v.trickNumber().isFirst() && v.trick().isEmpty()) {
        decision = new PlayDecision(new mighty.game.action.StandardPlay(
            (StandardCard) CardParser.parse("HA"), false), "비교 대안: 초구만 사이드 HA로 변경");
      }
      if (!new PlayFacts(v).legalActions().contains(decision.action())) throw new AssertionError("illegal");
      System.out.println("PLAY t=" + v.trickNumber().value() + " seat=" + v.self().index()
          + " role=" + v.role() + " action=" + decision.action() + " reason=" + decision.reason());
      round.play(new PublicPlay(v.self(), decision.action()));
    }
    System.out.println("RESULT " + round.result().orElseThrow());
    System.out.println("NOMINAL " + RoundSettlement.nominal(round.result().orElseThrow(), false));
  }
}
