package mighty.ai.preparation;

import java.util.*;
import mighty.ai.bidding.*;
import mighty.game.*;
import mighty.game.bidding.*;
import mighty.game.observation.*;
import mighty.model.*;

/**
 * 합법 공약별로 남길 패를 탐색하고, 실제 지불할 공약 안에서만 변경을 제안합니다.
 */
public final class PreparationPlanner {

  private final RuleDiscardPolicy discard = new RuleDiscardPolicy();
  private final RuleFriendPolicy friend = new RuleFriendPolicy();
  private final BiddingHandEvaluator bids = new BiddingHandEvaluator();
  private final BidExpectedScoreEvaluator expectedScores = new BidExpectedScoreEvaluator();

  /**
   * 바닥을 들기 전에 자기 열 장으로 가능한 합법 전환을 점수 기준으로 비교합니다.
   */
  public Bid beforeKittyContract(List<Card> hand, Bid awarded, Bid current) {
    var cards = BiddingHand.from(hand);
    Bid best = current;
    double bestScore = expectedScores.expectedDeclarerScore(cards, current);
    for (var assessment : bids.assess(cards)) {
      if (!assessment.canCompete()) {
        continue;
      }
      int start = current.points();
      for (int points = start; points <= assessment.ceilingPoints(); points++) {
        Bid candidate = new Bid(points, assessment.trump());
        if (candidate.equals(current) || !ContractChangeRules.canChange(awarded, current,
            candidate, GamePhase.BEFORE_KITTY)) {
          continue;
        }
        double score = expectedScores.expectedDeclarerScore(cards, candidate);
        if (score > bestScore) {
          best = candidate;
          bestScore = score;
        }
      }
    }
    return best;
  }

  public PreparationPlan choose(DiscardView view) {
    return choose(view, view.contract());
  }

  /**
   * 최초 낙찰이 풀인지 여부도 보존하여 준비 중 인상을 풀 낙찰과 혼동하지 않습니다.
   */
  public PreparationPlan choose(DiscardView view, Bid awarded) {
    return choose(view, awarded, List.of(awarded, view.contract()));
  }

  public PreparationPlan choose(DiscardView view, Bid awarded, List<Bid> history) {
    var context = new ContractContext(view.self(), awarded, view.contract(), history,
        GamePhase.DISCARD);
    var initialBuried = discard.choose(view);
    var initial = evaluate(retained(view.hand(), initialBuried), initialBuried);
    PreparationPlan best = plan(context.current(), initialBuried, friend.choose(
        new FriendView(context.self(), context.current(), initial.kept(), initialBuried,
            context.history())));
    double bestValue = value(initial, initial.assessment(context.current().trump()),
        context.current(), context.current());
    for (Trump trump : Trump.values()) {
      var draft = new DiscardView(view.self(), new Bid(view.contract().points(), trump),
          view.hand());
      boolean noTrumpOpening =
          trump == Trump.NO_TRUMP && PublicContractHistory.latestSuit(history).isEmpty();
      for (var buried : discard.ranked(draft, noTrumpOpening ? 8 : 1)) {
        var hand = buried.equals(initialBuried) ? initial
            : evaluate(retained(view.hand(), buried), buried);
        var candidate = candidate(context, hand, hand.assessment(trump));
        if (candidate.isPresent() && candidate.get().value() > bestValue) {
          var selected = candidate.get();
          best = plan(selected.bid(), buried, selected.friend());
          bestValue = selected.value();
        }
      }
    }
    return best;
  }

  /**
   * 버리기 뒤에는 확정된 열 장을 한 번 평가해 공약 후보를 비교합니다.
   */
  public Bid finalContract(FriendView view, Bid awarded) {
    return finalContract(view, awarded, List.of(awarded, view.contract()));
  }

  public Bid finalContract(FriendView view, Bid awarded, List<Bid> history) {
    var context = new ContractContext(view.self(), awarded, view.contract(), history,
        GamePhase.FRIEND);
    var hand = evaluate(view.hand(), view.discardedCards());
    Bid best = view.contract();
    double bestValue = value(hand, hand.assessment(best.trump()), best, best);
    for (var assessment : hand.assessments()) {
      var candidate = candidate(context, hand, assessment);
      if (candidate.isPresent() && candidate.get().value() > bestValue) {
        best = candidate.get().bid();
        bestValue = candidate.get().value();
      }
    }
    return best;
  }

  private Optional<Candidate> candidate(ContractContext context, EvaluatedHand hand,
      BidAssessment assessment) {
    if (!assessment.canCompete()) {
      return Optional.empty();
    }
    Trump trump = assessment.trump();
    if (context.phase() == GamePhase.DISCARD && abandonsTrump(context.current().trump(), trump,
        hand)) {
      return Optional.empty();
    }
    int start = Math.max(context.current().points(), assessment.openingPoints());
    for (int points = start; points <= assessment.ceilingPoints(); points++) {
      var bid = new Bid(points, trump);
      if (!ContractChangeRules.canChange(context.awarded(), context.current(), bid,
          context.phase())) {
        continue;
      }
      var selection = friend.choose(
          new FriendView(context.self(), bid, hand.kept(), hand.buried(), context.history()));
      if (!returnMatches(context.current(), trump, selection, context.history())) {
        continue;
      }
      // 문양별로 조건을 충족한 첫 공약의 점수를 사용합니다.
      return Optional.of(
          new Candidate(bid, selection, value(hand, assessment, bid, context.current())));
    }
    return Optional.empty();
  }

  private static boolean abandonsTrump(Trump current, Trump candidate, EvaluatedHand hand) {
    if (current == Trump.NO_TRUMP || current == candidate) {
      return false;
    }
    boolean allKnownTrumpBuried = hand.kept().stream()
        .noneMatch(card -> card instanceof StandardCard standard
            && current.matches(standard.suit()) && !standard.equals(
            mighty.game.rule.Rules.mighty(candidate)));
    return allKnownTrumpBuried && PublicContractHistory.suitOf(candidate)
        .map(suit -> hand.cards().ordinaryTopRunLength(suit, candidate) == 0).orElse(true);
  }

  /**
   * 풀노 확정 전에 합법적인 풀 문양 선언으로 반환받을 주력을 공개합니다.
   */
  public List<Bid> declarations(FriendView view, Bid awarded, List<Bid> history) {
    return resolveContract(view, awarded, history).declarations();
  }

  public ContractResolution resolveContract(FriendView view, Bid awarded, List<Bid> history) {
    Bid selected = finalContract(view, awarded, history);
    return new ContractResolution(selected, declarationSequence(view, awarded, history, selected));
  }

  private static List<Bid> declarationSequence(FriendView view, Bid awarded, List<Bid> history,
      Bid selected) {
    if (selected.points() == Bid.MAX_POINTS
        && selected.trump() == Trump.NO_TRUMP) {
      BiddingHand hand = BiddingHand.from(view.hand());
      var selection = new RuleFriendPolicy().choose(new FriendView(
          view.self(), selected, view.hand(), view.discardedCards(), history));
      var opening = mighty.ai.planning.NoTrumpOpeningPlanner.plan(hand, view.discardedCards(),
          selection, selected.points(), history);
      Suit primary = opening.returnSuit().orElseGet(
          () -> mighty.ai.planning.NoTrumpPlanner.signallingSuit(hand));
      Trump signal = Arrays.stream(Trump.values()).filter(t -> t.matches(primary)).findFirst()
          .orElseThrow();
      Bid signalBid = new Bid(Bid.MAX_POINTS, signal);
      if (!PublicContractHistory.latestSuit(history).filter(primary::equals).isPresent()
          && ContractChangeRules.canChange(awarded, view.contract(), signalBid, GamePhase.FRIEND)
          && ContractChangeRules.canChange(awarded, signalBid, selected, GamePhase.FRIEND)) {
        return List.of(signalBid, selected);
      }
    }
    return selected.equals(view.contract()) ? List.of() : List.of(selected);
  }

  /**
   * 앞서 공개한 기루와 지정 프렌드의 반환 문양이 어긋나는 변경 후보를 제외합니다.
   */
  private static boolean returnMatches(Bid current, Trump candidate,
      mighty.game.friend.FriendSelection selection,
      List<Bid> history) {
    if (current.trump() == Trump.NO_TRUMP || candidate != Trump.NO_TRUMP
        || !(selection instanceof mighty.game.friend.CardFriend called)
        || !(called.card() instanceof StandardCard card)
        || card.equals(mighty.game.rule.Rules.mighty(Trump.NO_TRUMP))) {
      return true;
    }
    return PublicContractHistory.latestSuit(history).map(card.suit()::equals)
        .orElseGet(() -> current.trump().matches(card.suit()));
  }

  private static PreparationPlan plan(Bid bid, List<Card> buried,
      mighty.game.friend.FriendSelection selection) {
    return new PreparationPlan(bid, buried, selection,
        "자기 패의 회수·상위 연결과 합법 변경 비용을 비교했습니다.");
  }

  private static List<Card> retained(List<Card> original, List<Card> buried) {
    var kept = new ArrayList<>(original);
    kept.removeAll(buried);
    return List.copyOf(kept);
  }

  private EvaluatedHand evaluate(List<Card> kept, List<Card> buried) {
    var cards = BiddingHand.from(kept);
    return new EvaluatedHand(kept, buried, cards, bids.assess(cards, buried));
  }

  private double value(EvaluatedHand hand, BidAssessment assessment, Bid proposed, Bid current) {
    if (!assessment.canCompete() || proposed.points() > assessment.ceilingPoints()) {
      return proposed.equals(current) ? -10000 : -100000;
    }
    // 경쟁 가능한 풀 상한을 기본 최종 공약으로 강제하지 않습니다.
    if (proposed.points() == 20 && current.points() < 20 && assessment.openingPoints() < 20) {
      return -100000;
    }
    // 풀과 노기루는 각각 두 배이며 풀노에서는 곱해집니다.
    int multiplier =
        (proposed.points() == 20 ? 2 : 1) * (proposed.trump() == Trump.NO_TRUMP ? 2 : 1);
    return 2 * (proposed.points() - 13) * multiplier * 10 + assessment.ceilingPoints() * 3
        - proposed.points()
        + new RetainedHandEvaluator().score(hand.kept(), hand.buried(), proposed.trump()) / 1000.0;
  }

  private record ContractContext(Seat self, Bid awarded, Bid current, List<Bid> history,
                                 GamePhase phase) {

  }

  private record EvaluatedHand(List<Card> kept, List<Card> buried, BiddingHand cards,
                               List<BidAssessment> assessments) {

    BidAssessment assessment(Trump trump) {
      return assessments.stream().filter(value -> value.trump() == trump).findFirst().orElseThrow();
    }
  }

  private record Candidate(Bid bid, mighty.game.friend.FriendSelection friend, double value) {

  }

}
