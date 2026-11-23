package mighty.ai.play;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.Function;
import mighty.game.GamePhase;
import mighty.game.Seat;
import mighty.game.action.Action;
import mighty.game.bidding.BidOffer;
import mighty.game.bidding.BiddingResult;
import mighty.game.friend.SpecialFriend;
import mighty.game.friend.RevealedFriend;
import mighty.game.observation.DeclarerInfo;
import mighty.game.observation.View;
import mighty.game.round.RoundSettlement;
import mighty.game.round.RoundState;
import mighty.game.trick.PublicPlay;
import mighty.model.Card;
import mighty.model.StandardCard;

final class HonorGambleTrial {

  private static final int SCREEN_DEALS = 24;
  private static final int CONFIRMATION_DEALS = 400;
  private static final int SPREAD_DEALS = 32;
  private static final int RUFF_DEALS = 32;

  private record Deal(Map<Seat, List<Card>> hands, List<Card> buried) {}
  private record Outcome(boolean success, int settlement) {}

  static final class Results {
    int samples;
    int usualWins;
    int gambleWins;
    int gain;
    double squaredGain;

    void add(boolean usualSuccess, boolean gambleSuccess, int difference) {
      samples++;
      usualWins += usualSuccess ? 1 : 0;
      gambleWins += gambleSuccess ? 1 : 0;
      gain += difference;
      squaredGain += (double) difference * difference;
    }

    boolean confirms() {
      if (samples != CONFIRMATION_DEALS || 2 * usualWins >= samples || gambleWins <= usualWins) {
        return false;
      }
      // 같은 배분에서 얻은 정산점 차이의 단측 95% 근사 하한이 양수일 때만 확정 수를 포기합니다.
      double variance = Math.max(0, (squaredGain - (double) gain * gain / samples) / (samples - 1));
      return gain > 1.645 * Math.sqrt(variance * samples);
    }
  }

  private HonorGambleTrial() {}

  static PlayDecision compareRuffLeads(View view, PlayFacts facts, PlayDecision usual,
      List<Action> sideLeads, RulePlayPolicy policy) {
    var friend = ((RevealedFriend) view.context().friendStatus()).seat();
    var past = facts.cards().publicHistory().completedPlays();
    var pool = new ArrayList<>(facts.cards().outsideCards());
    var random = new Random(view.hand().stream().map(Card::toCode).sorted().toList().hashCode());
    int[] sideWins = new int[sideLeads.size()];
    int[] rescuedByFriend = new int[sideLeads.size()];
    int samples = 0;
    int usualWins = 0;
    for (int attempt = 0; samples < RUFF_DEALS && attempt < RUFF_DEALS * 24; attempt++) {
      var deal = draw(view, pool, past, random);
      var control = position(view, deal, past);
      if (control.isEmpty()) {
        continue;
      }
      boolean drawn = finish(control.get(), view, usual.action(), policy::usualDecision).success();
      samples++;
      if (drawn && ++usualWins * 2 >= RUFF_DEALS) {
        return usual;
      }
      for (int index = 0; index < sideLeads.size(); index++) {
        var round = position(view, deal, past).orElseThrow();
        if (finish(round, view, sideLeads.get(index), policy::usualDecision).success()) {
          sideWins[index]++;
          var trick = round.history().get(view.context().history().size());
          // 기존 수로는 실패한 같은 배분에서 프렌드가 첫 부기루에 실제로 간을 쳤는지 확인합니다.
          if (!drawn && trick.winner().equals(friend) && trick.cards().stream().anyMatch(play ->
              play.seat().equals(friend) && play.action().card() instanceof StandardCard card
                  && view.contract().trump().matches(card.suit())
                  && !card.equals(mighty.game.rule.Rules.mighty(view.contract().trump())))) {
            rescuedByFriend[index]++;
          }
        }
      }
    }
    if (samples < RUFF_DEALS) {
      return usual;
    }
    int best = -1;
    for (int index = 0; index < sideLeads.size(); index++) {
      if (rescuedByFriend[index] >= 2 && sideWins[index] >= usualWins + 2
          && (best < 0 || sideWins[index] > sideWins[best])) {
        best = index;
      }
    }
    return best < 0 ? usual : new PlayDecision(sideLeads.get(best), String.format(
        "기루를 뽑으면 %d/%d, 부기루를 먼저 내면 %d/%d 성공했습니다. 그중 %d회는 프렌드의 간으로 공약을 살렸습니다.",
        usualWins, samples, sideWins[best], samples, rescuedByFriend[best]));
  }

  static PlayDecision compareSpreadLead(View view, PlayFacts facts, PlayDecision usual,
      PlayDecision top, RulePlayPolicy policy) {
    var past = new ArrayList<PublicPlay>();
    view.context().history().forEach(trick -> past.addAll(trick.cards()));
    long seed = view.hand().stream().map(Card::toCode).sorted().toList().hashCode();
    var pool = new ArrayList<>(facts.cards().outsideCards());
    var random = new Random(seed);
    int samples = 0;
    int usualWins = 0;
    int topWins = 0;
    // 같은 배분에서 기존 운영과 탑카 우선 운영을 끝까지 비교합니다.
    for (int attempt = 0; samples < SPREAD_DEALS && attempt < SPREAD_DEALS * 8; attempt++) {
      var deal = draw(view, pool, past, random);
      var control = position(view, deal, past);
      if (control.isEmpty()) {
        continue;
      }
      var alternative = position(view, deal, past).orElseThrow();
      usualWins += finish(control.get(), view, usual.action(), policy::usualDecision).success()
          ? 1 : 0;
      topWins += finish(alternative, view, top.action(), policy::spreadTopContinuation).success()
          ? 1 : 0;
      samples++;
    }
    if (samples < SCREEN_DEALS || topWins <= usualWins) {
      return usual;
    }
    return new PlayDecision(top.action(), String.format(
        "%s 같은 가상 배분 %d개에서 이 운영 %d회, 기존 운영 %d회 공약 성공.",
        top.reason(), samples, topWins, usualWins));
  }

  static PlayDecision compare(View view, PlayFacts facts, PlayDecision usual, Action gamble,
      RulePlayPolicy policy) {
    if (usual.action().equals(gamble)) {
      return usual;
    }
    var past = new ArrayList<PublicPlay>();
    view.context().history().forEach(t -> past.addAll(t.cards()));
    past.addAll(view.trick());
    long seed = view.hand().stream().map(Card::toCode).sorted().toList().hashCode();
    Results results = run(view, facts, past, usual.action(), gamble, policy, seed, SCREEN_DEALS);
    if (!HonorGamble.improvesHardContract(results.samples, results.usualWins,
        results.gambleWins, results.gain)) {
      return usual;
    }

    // 물기루로 흘릴 때와 달리, 지금 선을 보장하는 조커나 마이티가 있으면 그 수와도 비교합니다.
    var mightyCard = mighty.game.rule.Rules.mighty(view.contract().trump());
    var secureSpecials = facts.winningActions().stream().filter(a -> a.card().equals(mightyCard)
        || a.card() instanceof mighty.model.Joker)
        .filter(a -> facts.isSecured(new PublicPlay(view.self(), a))).toList();
    for (var safe : secureSpecials) {
      results = run(view, facts, past, safe, gamble, policy, seed ^ 0xFD27E1B648A9L,
          CONFIRMATION_DEALS);
      if (!results.confirms()) {
        return usual;
      }
    }
    String reason = String.format("같은 가상 배분 %d개에서 비교 수 성공 %d회, %s 성공 %d회, 평균 정산점 차이 %+.2f점으로 선을 노립니다.",
        results.samples, results.usualWins, gamble.card().displayName(), results.gambleWins,
        (double) results.gain / results.samples);
    return new PlayDecision(gamble, reason);
  }

  private static Results run(View view, PlayFacts facts, List<PublicPlay> past, Action control,
      Action gamble, RulePlayPolicy policy, long seed, int target) {
    var pool = new ArrayList<>(facts.cards().outsideCards());
    var random = new Random(seed);
    var results = new Results();
    for (int attempt = 0; results.samples < target && attempt < target * 8; attempt++) {
      Deal deal = draw(view, pool, past, random);
      var round = position(view, deal, past);
      if (round.isEmpty()) {
        continue;
      }
      var first = finish(round.get(), view, control, policy::usualDecision);
      var second = finish(position(view, deal, past).orElseThrow(), view, gamble,
          policy::usualDecision);
      results.add(first.success(), second.success(), second.settlement() - first.settlement());
      if (2 * results.usualWins >= target) {
        break;
      }
    }
    return results;
  }
  private static Deal draw(View view, List<Card> pool, List<PublicPlay> past, Random random) {
    // 공개 입찰과 최종 기루에 어울리는 패에 가중치를 주되, 특정 카드 소유자는 확정하지 않습니다.
    Deal selected = null;
    double total = 0;
    for (int i = 0; i < 4; i++) {
      Collections.shuffle(pool, random);
      Map<Seat, List<Card>> hands = new HashMap<>();
      int offset = 0;
      for (var seat : view.context().playOrder().seats()) {
        List<Card> hand = new ArrayList<>();
        past.stream().filter(p -> p.seat().equals(seat)).forEach(p -> hand.add(p.action().card()));
        if (seat.equals(view.self())) {
          hand.addAll(view.hand());
        } else {
          int end = offset + 10 - hand.size();
          hand.addAll(pool.subList(offset, end));
          offset = end;
        }
        hands.put(seat, hand);
      }
      var buried = view.privateInfo() instanceof DeclarerInfo info ? info.discardedCards()
          : List.copyOf(pool.subList(offset, offset + 3));
      double weight = suitSupport(hands.get(view.context().declarer()), view.contract().trump());
      // 테탑 관측에는 낙찰 후 선언 이력만 담길 수도 있습니다. 같은 노기루 신호는 한 번만 셉니다.
      boolean noTrumpAward = view.context().contractHistory().stream().anyMatch(b ->
          b.points() >= 16 && b.trump() == mighty.model.Trump.NO_TRUMP);
      boolean noTrumpBid = view.context().auction().stream().anyMatch(b ->
          b.seat().equals(view.context().declarer()) && b.action() instanceof BidOffer offer
              && offer.bid().points() >= 16 && offer.bid().trump() == mighty.model.Trump.NO_TRUMP);
      if (noTrumpAward && !noTrumpBid && view.contract().trump() != mighty.model.Trump.NO_TRUMP) {
        weight *= suitSupport(hands.get(view.context().declarer()), mighty.model.Trump.NO_TRUMP);
      }
      for (var entry : view.context().auction()) {
        if (entry.action() instanceof BidOffer offer && offer.bid().points() >= 16
            && hands.containsKey(entry.seat())) {
          weight *= suitSupport(hands.get(entry.seat()), offer.bid().trump());
        }
      }
      total += weight;
      if (random.nextDouble() * total < weight) {
        selected = new Deal(hands, buried);
      }
    }
    return selected;
  }

  private static double suitSupport(List<Card> hand, mighty.model.Trump trump) {
    double strength = 1;
    for (Card card : hand) {
      if (card instanceof StandardCard s && trump.matches(s.suit())) {
        strength += 1 + Math.max(0, s.rank().value() - 10);
      } else if (trump == mighty.model.Trump.NO_TRUMP && card.points() > 0) {
        strength += 1;
      }
    }
    return strength;
  }

  private static Optional<RoundState> position(View view, Deal deal, List<PublicPlay> past) {
    var ctx = view.context();
    var round = RoundState.start(ctx.playOrder(), new BiddingResult(ctx.declarer(), view.contract()),
        deal.hands(), deal.buried(), ctx.auction());
    round.takeKitty(ctx.declarer());
    round.discard(ctx.declarer(), deal.buried());
    round.chooseFriend(ctx.declarer(), view.friendSelection());
    try {
      for (var play : past) {
        round.play(play);
      }
    } catch (IllegalArgumentException incompatibleHistory) {
      // 이미 팔로우하지 못했던 자리 등 공개 행동과 양립하지 않는 배분은 양쪽에서 제외합니다.
      return Optional.empty();
    }
    return round.currentView().role() == view.role() ? Optional.of(round) : Optional.empty();
  }

  private static Outcome finish(RoundState round, View view, Action action,
      Function<View, PlayDecision> policy) {
    round.play(new PublicPlay(view.self(), action));
    while (round.phase() == GamePhase.PLAY) {
      var actor = round.currentView();
      var ctx = actor.context();
      var observed = new mighty.game.observation.Context(ctx.playOrder(), ctx.declarer(),
          ctx.friendStatus(), ctx.history(), view.context().contractHistory(), ctx.auction());
      var turn = new View(actor.self(), actor.privateInfo(), actor.role(), actor.contract(),
          actor.friendSelection(), observed, actor.trick());
      round.play(new PublicPlay(actor.self(), policy.apply(turn).action()));
    }
    var result = round.result().orElseThrow();
    return new Outcome(result.succeeded(), RoundSettlement.nominal(result,
        view.friendSelection() == SpecialFriend.NONE).get(view.self()));
  }
}
