package mighty.ai.bidding;

import mighty.data.CardParser;
import mighty.game.bidding.Bid;
import mighty.model.Trump;

public class UserBidScoreDiagnostics {
  public static void main(String[] args) throws Exception {
    System.setOut(new java.io.PrintStream(java.nio.file.Files.newOutputStream(java.nio.file.Path.of(args[0])),
        true, java.nio.charset.StandardCharsets.UTF_8));
    var hand = BiddingHand.from(CardParser.parseList("D3 D4 C2 C6 C10 CA H2 H4 HK HA"));
    var evaluator = new BidExpectedScoreEvaluator();
    var heart = new Bid(15, Trump.HEART);
    var club = new Bid(15, Trump.CLUB);
    var hs = evaluator.initialBiddingSamples(hand, heart);
    var cs = evaluator.initialBiddingSamples(hand, club);
    System.out.println("100 HEART=" + hs.mean() + " CLUB=" + cs.mean()
        + " uncertain=" + hs.differenceIsUncertain(cs));
    if (hs.differenceIsUncertain(cs)) {
      System.out.println("1000 HEART=" + evaluator.expandedBiddingSamples(hand, heart).mean()
          + " CLUB=" + evaluator.expandedBiddingSamples(hand, club).mean());
    }
  }
}
