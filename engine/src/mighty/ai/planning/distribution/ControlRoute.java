package mighty.ai.planning.distribution;

import java.util.*;
import mighty.game.Seat;
import mighty.game.rule.Rules;
import mighty.model.*;

/**
 * 가상 배분의 같은 문양을 함께 따라내며, 프렌드 상위는 실제 여당 승리로 전환합니다. 같은 상위를 중복된 패배로 더하지 않고 사용한 카드는 해당 배분에서 제거합니다. 전체
 * 대국의 최적 플레이 증명이 아니라 주력·사이드 통제 경로 평가입니다.
 */
final class ControlRoute {

  private ControlRoute() {
  }

  static int pointsLost(ConditionalDeal deal, Card called) {
    var own = deal.hands().get(new Seat(0));
    boolean ownMighty = own.contains(Rules.mighty(Trump.NO_TRUMP));
    boolean ownJoker = own.contains(Joker.INSTANCE);
    boolean friendJoker = called instanceof Joker && !ownJoker;
    boolean friendMighty = called.equals(Rules.mighty(Trump.NO_TRUMP)) && !ownMighty;
    boolean gun = own.contains(Rules.gun(Trump.NO_TRUMP));
    boolean jokerInOpposition = deal.hands().entrySet().stream().anyMatch(e ->
        e.getKey().index() != 0 && e.getKey().index() != deal.friendIndex() && e.getValue()
            .contains(Joker.INSTANCE));
    int jokerBreaks = jokerInOpposition && !gun ? 1 : 0;
    if (jokerBreaks > 0 && !ownMighty) {
      return 20;
    }
    boolean soloGun = gun && jokerInOpposition && deal.friendIndex() < 0;
    // 총에서 선을 내주는 배분은 마이티를 회수에 남겨야 합니다.
    int freeMighty = ownMighty && jokerBreaks == 0 && !soloGun ? 1 : 0;
    int freeJoker = ownJoker ? 1 : 0;
    var holdings = new DistributionHoldings(deal);
    int[][][] losses = new int[Suit.values().length][3][2];
    for (var suit : Suit.values()) {
      for (int sweeps = 0; sweeps <= freeMighty + freeJoker; sweeps++) {
        for (int support = 0; support <= (friendMighty || friendJoker ? 1 : 0); support++) {
          losses[suit.ordinal()][sweeps][support] = suitLosses(deal, suit, holdings.ranks(suit),
              sweeps, support == 1, gun && friendMighty && support == 1,
              soloGun && suit == Rules.gun(Trump.NO_TRUMP).suit());
        }
      }
    }
    int best = 20;
    for (Suit request : Suit.values()) {
      if (own.stream().noneMatch(c -> c instanceof StandardCard s && s.suit() == request
          && !c.equals(Rules.mighty(Trump.NO_TRUMP)))) {
        continue;
      }
      if (friendMighty && gun && request != Rules.gun(Trump.NO_TRUMP).suit()) {
        continue;
      }
      for (int ma = -1; ma < 4; ma++) {
        if (freeMighty == 0 && ma != -1) {
          continue;
        }
        for (int jo = -1; jo < 4; jo++) {
          if (freeJoker == 0 && jo != -1) {
            continue;
          }
          int lost = 3 * jokerBreaks;
          for (Suit suit : Suit.values()) {
            int sweeps = (ma == suit.ordinal() ? 1 : 0) + (jo == suit.ordinal() ? 1 : 0);
            boolean requested = suit == request && (friendMighty || friendJoker);
            lost += losses[suit.ordinal()][sweeps][requested ? 1 : 0];
          }
          best = Math.min(best, lost);
        }
      }
    }
    return best;
  }

  /**
   * 초구와 반환을 같은 배분에서 계산하고 조커 한 장을 한 문양에만 사용합니다.
   */
  static int openingReturnLoss(ConditionalDeal deal, Suit main, Suit returned, int mainTops) {
    var holdings = new DistributionHoldings(deal);
    var side = holdings.ranks(returned);
    var primary = holdings.ranks(main);
    boolean canReturn = deal.friendIndex() >= 0 && !side.get(deal.friendIndex()).isEmpty();
    int sideRounds = canReturn ? 2 : 1;
    int sideThreat = remainingThreat(side, sideRounds, side.get(0).size() - 1);
    int mainThreat = remainingThreat(primary, mainTops, primary.get(0).size() - mainTops);

    // 일반 탑카를 모두 확인한 뒤 반환 문양에 상위가 남았을 때 그쪽에 조커를 씁니다.
    if (sideThreat > 0) {
      sideThreat = remainingThreat(side, sideRounds + 1, side.get(0).size() - 1);
    } else if (mainThreat > 0) {
      mainThreat = remainingThreat(primary, mainTops + 1, primary.get(0).size() - mainTops);
    }
    // 반환 없이 별도로 선을 회수해야 하는 경우에는 풀 성공으로 세지 않습니다.
    int unresolved = sideThreat + mainThreat + (canReturn ? 0 : 1);
    for (Suit suit : Suit.values()) {
      if (suit != main && suit != returned) {
        unresolved += holdings.ranks(suit).get(0).size();
      }
    }
    return Math.min(20, unresolved * 3);
  }

  private static int remainingThreat(List<List<Integer>> ranks, int rounds, int ownWater) {
    if (ownWater <= 0) {
      return 0;
    }
    int lowest = ranks.get(0).get(0);
    return Math.min(ownWater, ranks.subList(1, ranks.size()).stream()
        .mapToInt(holding -> (int) Math.min(Math.max(0, holding.size() - rounds),
            holding.stream().filter(rank -> rank > lowest).count()))
        .max().orElse(0));
  }

  private static int suitLosses(ConditionalDeal deal, Suit suit, List<List<Integer>> ranks,
      int ownSweeps, boolean requested, boolean gun, boolean soloGun) {
    List<Integer> own = ranks.get(0);
    if (own.isEmpty()) {
      return 0;
    }
    // 총 전에 가능한 자기 탑카를 먼저 써도 되고, 지원부터 받아도 되는 두 순서를 비교합니다.
    int best = 20;
    for (boolean topsFirst : List.of(false, true)) {
      List<List<Integer>> state = ranks.stream().map(ArrayList::new).map(v -> (List<Integer>) v)
          .toList();
      if (topsFirst) {
        while (!state.get(0).isEmpty() && top(state.get(0)) > enemyTop(state, deal.friendIndex())
            && (!soloGun || top(state.get(0)) != Rules.gun(Trump.NO_TRUMP).rank().value())) {
          ordinaryRound(state, deal.friendIndex(), deal, suit);
        }
      }
      for (int i = 0; i < ownSweeps; i++) {
        forcedRound(state, deal.friendIndex(), false, false, deal);
      }
      if (requested && !state.get(0).isEmpty()) {
        state.get(0).remove(0);
        forcedRound(state, deal.friendIndex(), true, gun, deal);
      }
      int losses = soloGun ? soloCall(state, deal, suit) : 0;
      while (!state.get(0).isEmpty()) {
        losses += ordinaryRound(state, deal.friendIndex(), deal, suit);
      }
      best = Math.min(best, losses);
    }
    return best;
  }

  /**
   * 총을 쏘면 조커 자리는 리드슈트를 건너뛰고, 나머지는 실제로 낼 수 있는 카드만 계산합니다.
   */
  private static int soloCall(List<List<Integer>> state, ConditionalDeal deal, Suit suit) {
    int gunRank = Rules.gun(Trump.NO_TRUMP).rank().value();
    if (!state.get(0).remove(Integer.valueOf(gunRank))) {
      return 3;
    }
    int points = 0;
    boolean beaten = false;
    for (int seat = 1; seat < state.size(); seat++) {
      var hand = deal.hands().get(new Seat(seat));
      if (hand.contains(Joker.INSTANCE)) {
        continue;
      }
      var holding = state.get(seat);
      if (holding.isEmpty()) {
        if (hand.stream().anyMatch(card -> card instanceof StandardCard standard
            && standard.suit() != suit && card.points() > 0)) {
          points++;
        }
      } else {
        int played = holding.remove(holding.size() - 1);
        beaten |= played > gunRank;
        points += point(played);
      }
    }
    return beaten ? points : 0;
  }

  private static void forcedRound(List<List<Integer>> state, int friend, boolean friendPlaysSpecial,
      boolean gun, ConditionalDeal deal) {
    for (int seat = 1; seat < 5; seat++) {
      if (seat == friend && friendPlaysSpecial) {
        continue;
      }
      if (gun && deal.hands().get(new Seat(seat)).contains(Joker.INSTANCE)) {
        continue;
      }
      if (!state.get(seat).isEmpty()) {
        state.get(seat).remove(0);
      }
    }
  }

  private static int ordinaryRound(List<List<Integer>> state, int friend, ConditionalDeal deal,
      Suit suit) {
    List<Integer> own = state.get(0);
    int enemy = enemyTop(state, friend);
    List<Integer> ally = friend < 0 ? List.of() : state.get(friend);
    boolean friendCanWin = top(ally) > enemy;
    int ownCard = top(own) > enemy ? own.remove(own.size() - 1) : own.remove(0);
    int allyCard = 0;
    if (!ally.isEmpty()) {
      allyCard = friendCanWin && ownCard <= enemy ? ally.remove(ally.size() - 1) : ally.remove(0);
    }
    int team = Math.max(ownCard, allyCard);
    int attacking = -1, attack = 15;
    for (int seat = 1; seat < 5; seat++) {
      if (seat != friend) {
        for (int rank : state.get(seat)) {
          if (rank > team && rank < attack) {
            attack = rank;
            attacking = seat;
          }
        }
      }
    }
    int points = point(ownCard) + point(allyCard);
    for (int seat = 1; seat < 5; seat++) {
      if (seat != friend) {
        if (!state.get(seat).isEmpty()) {
          int played = seat == attacking ? attack : state.get(seat).get(0);
          state.get(seat).remove(Integer.valueOf(played));
          points += point(played);
        } else if (deal.hands().get(new Seat(seat)).stream().anyMatch(c ->
            c instanceof StandardCard card && card.suit() != suit && c.points() > 0)) {
          points++;
        }
      }
    }
    if (attacking < 0) {
      return 0;
    }
    int highestOrdinary =
        Rules.mighty(Trump.NO_TRUMP).suit() == suit ? Rank.KING.value() : Rank.ACE.value();
    boolean ownEntry = deal.hands().get(new Seat(0)).contains(new StandardCard(suit,
        Arrays.stream(Rank.values()).filter(r -> r.value() == highestOrdinary).findFirst()
            .orElseThrow()));
    // 회수·순서가 없는 사이드 패배를 단순히 0점 손실로 낙관하지 않습니다.
    return ownEntry ? points : Math.max(1, points);
  }

  private static int enemyTop(List<List<Integer>> state, int friend) {
    int highest = 0;
    for (int seat = 1; seat < 5; seat++) {
      if (seat != friend) {
        highest = Math.max(highest, top(state.get(seat)));
      }
    }
    return highest;
  }

  private static int point(int rank) {
    return rank >= 10 ? 1 : 0;
  }

  private static int top(List<Integer> ranks) {
    return ranks.isEmpty() ? 0 : ranks.get(ranks.size() - 1);
  }

}
