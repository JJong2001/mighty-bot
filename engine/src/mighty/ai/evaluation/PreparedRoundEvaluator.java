package mighty.ai.evaluation;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;
import mighty.ai.play.RulePlayPolicy;
import mighty.ai.preparation.*;
import mighty.application.round.RoundService;
import mighty.game.*;
import mighty.game.bidding.*;
import mighty.game.deal.DeckFactory;
import mighty.game.friend.*;
import mighty.game.round.*;
import mighty.model.*;

/**
 * 준비를 확정한 후보의 대국 평가를 공유합니다. 배분 순서·표본 수·정산 기준을 보존합니다.
 */
public final class PreparedRoundEvaluator {

  private static final int KNOWN_KITTY_SAMPLES = 32;
  private static final int CONFIRM_KNOWN_KITTY_SAMPLES = 128;
  private static final Map<PreparedEvaluationKey, Score> PREPARED_CACHE = new ConcurrentHashMap<>();
  private static final List<Seat> SEATS = IntStream.range(0, GameMode.PLAYING_PLAYER_COUNT)
      .mapToObj(Seat::new).toList();
  private static final PlayOrder ORDER = PlayOrder.from(
      new TableLayout(GameMode.FIVE_PLAYER, SEATS), new HashSet<>(SEATS));
  private static final Seat DECLARER = SEATS.get(0);
  // 준비는 호출자가 확정합니다. 여기서는 PLAY 단계만 실행하므로 준비 정책에 의존하지 않습니다.
  private static final RoundService ROUND_SERVICE = new RoundService(new BaselineDiscardPolicy(),
      new BaselineFriendPolicy(), new RulePlayPolicy());

  public record Score(double mean, int wins) {

  }

  public record Outcome(int settlement, boolean succeeded) {

  }

  private record PreparedEvaluationKey(Bid current, List<Card> kept, Bid selected,
                                       FriendSelection friend, Set<Card> discarded,
                                       List<PublicBid> auction, int samples) {

  }

  public Score evaluate(Bid current, List<Card> seen, PreparationPlan preparation,
      List<PublicBid> auction) {
    return preparedScore(current, seen, preparation, auction, KNOWN_KITTY_SAMPLES);
  }

  public double confirmedMean(Bid current, List<Card> seen, PreparationPlan preparation) {
    return preparedScore(current, seen, preparation, List.of(), CONFIRM_KNOWN_KITTY_SAMPLES).mean();
  }

  private Score preparedScore(Bid current, List<Card> seen, PreparationPlan preparation,
      List<PublicBid> auction, int samples) {
    List<Card> kept = DeckFactory.create().stream().filter(seen::contains)
        .filter(card -> !preparation.discarded().contains(card)).toList();
    var key = new PreparedEvaluationKey(current, kept, preparation.contract(), preparation.friend(),
        Set.copyOf(preparation.discarded()), List.copyOf(auction), samples);
    if (PREPARED_CACHE.size() > 512) {
      PREPARED_CACHE.clear();
    }
    return PREPARED_CACHE.computeIfAbsent(key, ignored -> {
      List<Card> unseen = DeckFactory.create().stream().filter(card -> !seen.contains(card))
          .toList();
      String seenKey = seen.stream().map(Card::toCode).sorted().toList().toString();
      Random random = new Random(seenKey.hashCode());
      int total = 0;
      int wins = 0;
      for (int sample = 0; sample < samples; sample++) {
        List<Card> opponents = new ArrayList<>(unseen);
        Collections.shuffle(opponents, random);
        RoundState round = createRound(kept, current, preparation.discarded(), opponents,
            key.auction());
        round.takeKitty(DECLARER);
        var outcome = playPrepared(round, preparation);
        total += outcome.settlement();
        wins += outcome.succeeded() ? 1 : 0;
      }
      return new Score((double) total / samples, wins);
    });
  }


  public static Outcome playPrepared(RoundState round, PreparationPlan preparation) {
    var history = round.contractHistory();
    for (var bid : PreparationDeclarations.discardDeclarations(round.discardView(DECLARER),
        history.get(0), history, preparation)) {
      round.reviseContract(DECLARER, bid);
    }
    round.discard(DECLARER, preparation.discarded());
    round.chooseFriend(DECLARER, preparation.friend());
    RoundResult result = ROUND_SERVICE.playToEnd(round);
    boolean declaredSolo = preparation.friend() == SpecialFriend.NONE;
    return new Outcome(RoundSettlement.nominal(result, declaredSolo).get(DECLARER),
        result.succeeded());
  }

  public static RoundState createRound(List<Card> own, Bid contract, List<Card> kitty,
      List<Card> opponentPool) {
    return createRound(own, contract, kitty, opponentPool, List.of());
  }

  public static RoundState createRound(List<Card> own, Bid contract, List<Card> kitty,
      List<Card> opponentPool, List<PublicBid> auction) {
    Map<Seat, List<Card>> hands = new HashMap<>();
    hands.put(DECLARER, own);
    for (int index = 1; index < SEATS.size(); index++) {
      int start = (index - 1) * 10;
      hands.put(SEATS.get(index), List.copyOf(opponentPool.subList(start, start + 10)));
    }
    return RoundState.start(ORDER, new BiddingResult(DECLARER, contract), hands, kitty, auction);
  }
}
