package mighty.ai.preparation;

import java.util.*;
import mighty.ai.bidding.*;
import mighty.ai.planning.HighNoTrumpPlan;
import mighty.ai.planning.BidderSupportPlan;
import mighty.game.*;
import mighty.game.bidding.*;
import mighty.game.observation.*;
import mighty.model.*;

/**
 * 합법 공약별로 남길 패를 탐색하고, 실제 지불할 공약 안에서만 변경을 제안합니다.
 */
public final class PreparationPlanner {

  private static final Bid FIFTEEN_NO_TRUMP = new Bid(15, Trump.NO_TRUMP);

  private final RuleDiscardPolicy discard = new RuleDiscardPolicy();
  private final RuleFriendPolicy friend = new RuleFriendPolicy();
  private final BiddingHandEvaluator bids = new BiddingHandEvaluator();
  private final BidExpectedScoreEvaluator expectedScores = new BidExpectedScoreEvaluator();

  /**
   * 바닥을 들기 전에 자기 열 장으로 가능한 합법 전환을 점수 기준으로 비교합니다.
   */
  public Bid beforeKittyContract(List<Card> hand, Bid awarded, Bid current) {
    var cards = BiddingHand.from(hand);
    var high = highBeforeKitty(cards, awarded, current);
    if (high.isPresent()) {
      return high.get();
    }
    var assessments = bids.assess(cards);
    if (FIFTEEN_NO_TRUMP.equals(awarded) && FIFTEEN_NO_TRUMP.equals(current)) {
      return beforeKittyAfterFifteenNoTrump(assessments, awarded);
    }
    Bid best = current;
    double bestScore = expectedScores.expectedDeclarerScore(cards, current);
    for (var assessment : assessments) {
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
    if (isFifteenNoTrump(context)) {
      return compareAfterFifteenNoTrump(context, view.hand(), Optional.empty());
    }
    var support = BidderSupportPlan.forContract(BiddingHand.from(view.hand()), view.self(),
        view.auction(), view.contract());
    if (support.isPresent() && !view.hand().contains(support.get().called())) {
      // 묻은 뒤에도 연결 물패와 내 회수 자원이 남는 버리기 중 기존 평가가 가장 좋은 것을 택합니다.
      for (var buried : discard.ranked(view, 286)) {
        var kept = BiddingHand.from(retained(view.hand(), buried));
        var preserved = BidderSupportPlan.forContract(kept, view.self(), view.auction(),
            view.contract());
        if (preserved.equals(support)) {
          if (HighNoTrumpPlan.find(kept).filter(p -> p.canRun(kept)).isPresent()) {
            break;
          }
          return plan(view.contract(), buried, new mighty.game.friend.CardFriend(support.get().called()));
        }
      }
    }
    var high = highPreparation(view, awarded, history);
    if (high.isPresent()) {
      return high.get();
    }
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
    if (isFifteenNoTrump(context)) {
      List<Card> seen = new ArrayList<>(view.hand());
      seen.addAll(view.discardedCards());
      return compareAfterFifteenNoTrump(context, seen, Optional.of(view.discardedCards()))
          .contract();
    }
    if (FIFTEEN_NO_TRUMP.equals(awarded) && context.current().points() == 17
        && context.current().trump() != Trump.NO_TRUMP) {
      return context.current();
    }
    var kept = BiddingHand.from(view.hand());
    var support = BidderSupportPlan.forContract(kept, view.self(), view.auction(), view.contract());
    if (support.isPresent() && !view.discardedCards().contains(support.get().called())
        && HighNoTrumpPlan.find(kept).filter(p -> p.canRun(kept)).isEmpty()) {
      return view.contract();
    }
    var route = highRoute(kept, awarded);
    if (route.isPresent()) {
      return retainedContract(route.get(), kept, awarded, view.contract(), GamePhase.FRIEND);
    }
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

  public static Optional<Bid> beforePublicBid(BiddingHand hand, Bid awarded, Bid current,
      Seat self, List<PublicBid> auction) {
    if (BidderSupportPlan.forContract(hand, self, auction, current).isPresent()) {
      return Optional.of(current);
    }
    return highBeforeKitty(hand, awarded, current);
  }

  public static Optional<Bid> highBeforeKitty(BiddingHand hand, Bid awarded, Bid current) {
    return highRoute(hand, awarded)
        .filter(plan -> plan.competitiveShape(hand) || plan.hasTwoSources(hand))
        .map(plan -> current.equals(new Bid(17, Trump.NO_TRUMP)) && !plan.mighty() && plan.joker()
            ? new Bid(18, plan.suited()) : current);
  }

  private static Optional<HighNoTrumpPlan> highRoute(BiddingHand hand,
      Bid awarded) {
    if (awarded.points() != 16 && awarded.points() != 17) {
      return Optional.empty();
    }
    return HighNoTrumpPlan.find(hand)
        .filter(plan -> awarded.trump() == Trump.NO_TRUMP
            || (awarded.points() == 16 && awarded.trump() == plan.suited()
            && plan.hasTwoSources(hand)));
  }

  private static Bid retainedContract(HighNoTrumpPlan plan, BiddingHand hand,
      Bid awarded, Bid current, GamePhase phase) {
    if (current.trump() != Trump.NO_TRUMP && current.points() > 16 && current.points() < 20) {
      return current;
    }
    int points = plan.canRun(hand) ? 20 : current.trump() == Trump.NO_TRUMP
        ? current.points() : 17;
    var proposed = new Bid(points, Trump.NO_TRUMP);
    return ContractChangeRules.canChange(awarded, current, proposed, phase) ? proposed : current;
  }

  private Optional<PreparationPlan> highPreparation(DiscardView view, Bid awarded,
      List<Bid> history) {
    var seen = BiddingHand.from(view.hand());
    var route = highRoute(seen, awarded);
    if (route.isEmpty()) {
      return Optional.empty();
    }
    var line = route.get();
    List<Card> buried;
    if (view.contract().trump() == Trump.NO_TRUMP || view.contract().points() == 16) {
      // 두 주력 문양을 살리고 같은 옆 문양을 비웁니다. 점수패는 묻어 회수합니다.
      var outside = view.hand().stream().filter(StandardCard.class::isInstance)
          .map(StandardCard.class::cast)
          .filter(c -> c.suit() != Suit.SPADE && c.suit() != line.primary())
          .sorted(Comparator.<StandardCard>comparingInt(c -> seen.holding(c.suit()).size())
              .reversed().thenComparing(Comparator.comparingInt(StandardCard::points).reversed())
              .thenComparingInt(c -> c.rank().value())).limit(3).toList();
      if (line.hasTwoSources(seen) && outside.size() == 3) {
        buried = List.copyOf(outside);
      } else {
        var preserved = discard.ranked(view, 286).stream().filter(choice ->
            highRoute(BiddingHand.from(retained(view.hand(), choice)), awarded).isPresent())
            .findFirst();
        if (preserved.isEmpty()) {
          return Optional.empty();
        }
        buried = preserved.get();
      }
    } else {
      buried = discard.choose(view);
    }
    var kept = retained(view.hand(), buried);
    var hand = BiddingHand.from(kept);
    var retained = highRoute(hand, awarded);
    if (retained.isEmpty()) {
      return Optional.empty();
    }
    var bid = retainedContract(retained.get(), hand, awarded, view.contract(), GamePhase.DISCARD);
    var called = friend.choose(new FriendView(view.self(), bid, kept, buried, history,
        view.auction()));
    return Optional.of(plan(bid, buried, called));
  }

  private static Bid beforeKittyAfterFifteenNoTrump(List<BidAssessment> assessments, Bid awarded) {
    if (assessments.stream().anyMatch(assessment -> assessment.trump() == Trump.NO_TRUMP
        && supports(assessment, FIFTEEN_NO_TRUMP))) {
      return FIFTEEN_NO_TRUMP;
    }
    var suited = assessments.stream()
        .filter(assessment -> assessment.trump() != Trump.NO_TRUMP)
        .filter(assessment -> assessment.canCompete() && assessment.ceilingPoints() >= 16)
        .map(assessment -> new Bid(16, assessment.trump()))
        .filter(bid -> ContractChangeRules.canChange(awarded, FIFTEEN_NO_TRUMP, bid,
            GamePhase.BEFORE_KITTY))
        .toList();
    // 두 문양 이상이 가능해도 실제 바닥을 보기 전 주력으로 확정하지 않습니다.
    return suited.size() == 1 ? suited.get(0) : FIFTEEN_NO_TRUMP;
  }

  private static boolean supports(BidAssessment assessment, Bid bid) {
    return assessment.trump() == bid.trump() && assessment.canCompete()
        && assessment.ceilingPoints() >= bid.points();
  }

  private static boolean isFifteenNoTrump(ContractContext context) {
    return FIFTEEN_NO_TRUMP.equals(context.awarded()) && FIFTEEN_NO_TRUMP.equals(context.current());
  }

  private PreparationPlan compareAfterFifteenNoTrump(ContractContext context, List<Card> seen,
      Optional<List<Card>> fixedDiscards) {
    var noTrumpHands = handsFor(context, seen, fixedDiscards, FIFTEEN_NO_TRUMP);
    var full = new Bid(Bid.MAX_POINTS, Trump.NO_TRUMP);
    for (var hand : noTrumpHands) {
      var assessment = hand.assessment(Trump.NO_TRUMP);
      // 실제 바닥으로 풀노 운영이 완성된 경우의 기존 준비 경로는 별도로 유지합니다.
      if (supports(assessment, full) && assessment.openingPoints() == Bid.MAX_POINTS
          && ContractChangeRules.canChange(context.awarded(), context.current(), full,
              context.phase())) {
        return prepare(context, hand, full);
      }
    }
    var noTrump = bestRetained(noTrumpHands, FIFTEEN_NO_TRUMP);
    PreparationPlan best = prepare(context, noTrump, FIFTEEN_NO_TRUMP);
    double bestScore = Double.NEGATIVE_INFINITY;
    for (Trump trump : Trump.values()) {
      if (trump == Trump.NO_TRUMP) {
        continue;
      }
      var bid = new Bid(17, trump);
      if (!ContractChangeRules.canChange(context.awarded(), context.current(), bid,
          context.phase())) {
        continue;
      }
      var suited = handsFor(context, seen, fixedDiscards, bid).get(0);
      if (!supports(suited.assessment(trump), bid)) {
        continue;
      }
      if (bestScore == Double.NEGATIVE_INFINITY) {
        bestScore = expectedScores.expectedPreparedScore(context.current(), seen, best);
      }
      var candidate = prepare(context, suited, bid);
      double score = expectedScores.expectedPreparedScore(context.current(), seen, candidate);
      if (score > bestScore) {
        best = candidate;
        bestScore = score;
      }
    }
    return best;
  }

  private List<EvaluatedHand> handsFor(ContractContext context, List<Card> seen,
      Optional<List<Card>> fixedDiscards, Bid bid) {
    var discardChoices = fixedDiscards.map(List::of).orElseGet(() -> discard.ranked(
        new DiscardView(context.self(), bid, seen), bid.trump() == Trump.NO_TRUMP ? 8 : 1));
    return discardChoices.stream().map(buried -> evaluate(retained(seen, buried), buried)).toList();
  }

  private EvaluatedHand bestRetained(List<EvaluatedHand> hands, Bid bid) {
    return hands.stream().max(Comparator.comparingDouble(hand ->
        value(hand, hand.assessment(bid.trump()), bid, bid))).orElseThrow();
  }

  private PreparationPlan prepare(ContractContext context, EvaluatedHand hand, Bid bid) {
    var selection = friend.choose(new FriendView(context.self(), bid, hand.kept(), hand.buried(),
        context.history()));
    return plan(bid, hand.buried(), selection);
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

  /** 실제 판과 가상 대국 모두 프렌드가 읽을 수 있는 같은 순서로 공약을 공개합니다. */
  public static List<Bid> discardDeclarations(DiscardView view, Bid awarded, List<Bid> history,
      PreparationPlan plan) {
    if (plan.contract().equals(new Bid(20, Trump.NO_TRUMP))
        && (awarded.points() == 16 || awarded.points() == 17)) {
      var kept = retained(view.hand(), plan.discarded());
      var hand = BiddingHand.from(kept);
      if (highRoute(hand, awarded).filter(route -> route.canRun(hand)).isPresent()) {
        var exposed = new FriendView(view.self(), view.contract(), kept, plan.discarded(), history,
            view.auction());
        return declarationSequence(exposed, awarded, history, plan.contract());
      }
    }
    return plan.contract().equals(view.contract()) ? List.of() : List.of(plan.contract());
  }

  private static List<Bid> declarationSequence(FriendView view, Bid awarded, List<Bid> history,
      Bid selected) {
    if (selected.points() == Bid.MAX_POINTS
        && selected.trump() == Trump.NO_TRUMP) {
      BiddingHand hand = BiddingHand.from(view.hand());
      var route = highRoute(hand, awarded).filter(plan -> plan.canRun(hand));
      if (route.isPresent()) {
        var signals = new ArrayList<Bid>();
        if (hand.holding(Suit.SPADE).size() >= 5) {
          signals.add(new Bid(20, Trump.SPADE));
        }
        signals.add(new Bid(20, route.get().suited()));
        signals.add(selected);
        if (history.size() >= signals.size() && history.subList(history.size() - signals.size(),
            history.size()).equals(signals)) {
          return List.of();
        }
        return List.copyOf(signals);
      }
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
