package mighty.ai.planning;

import java.util.*;
import mighty.ai.analysis.HoldingControl;
import mighty.ai.bidding.BiddingHand;
import mighty.game.deal.DeckFactory;
import mighty.game.rule.Rules;
import mighty.model.*;

/**
 * 자기 패와 알려진 제거 카드로 자력 소진과 지원 뒤 선 복귀를 구분합니다.
 */
public record NoTrumpControlPlan(Suit primary, HoldingControl holding,
                                 double residualThreat, boolean runBeforeSupport,
                                 Set<Suit> returnEntries) {

  public NoTrumpControlPlan {
    returnEntries = Set.copyOf(returnEntries);
  }

  public static NoTrumpControlPlan analyze(List<Card> own, Collection<? extends Card> removed) {
    return analyze(own, removed, NoTrumpPlanner.signallingSuit(BiddingHand.from(own)));
  }

  public static NoTrumpControlPlan analyze(List<Card> own, Collection<? extends Card> removed,
      Suit primary) {
    var control = HoldingControl.analyze(own, List.copyOf(removed), Trump.NO_TRUMP, primary);
    var mighty = Rules.mighty(Trump.NO_TRUMP);
    var outside = DeckFactory.create().stream()
        .filter(c -> !own.contains(c) && !removed.contains(c) && !c.equals(mighty)).toList();
    int external = (int) outside.stream().filter(c -> c instanceof StandardCard s
        && s.suit() == primary).count();
    int rounds = control.topRun() + (own.contains(Joker.INSTANCE) ? 1 : 0);
    // 프렌드의 자리도 배분 공간에 포함합니다. 세 야당의 잔존 장수 기대값 합은
    // 가장 긴 야당 문양의 잔존 위협을 낙관하지 않는 상계입니다.
    double residual = 0;
    int capacity = Math.min(10, outside.size());
    for (int count = rounds + 1; count <= Math.min(external, capacity); count++) {
      residual += 3 * (count - rounds) * choose(external, count)
          * choose(outside.size() - external, capacity - count) / choose(outside.size(), capacity);
    }
    Set<Suit> entries = new HashSet<>();
    boolean sideLoss = false;
    for (Suit suit : Suit.values()) {
      if (suit == primary) {
        continue;
      }
      var side = HoldingControl.analyze(own, List.copyOf(removed), Trump.NO_TRUMP, suit);
      if (side.topRun() > 0) {
        entries.add(suit);
      }
      sideLoss |= side.length() > side.topRun();
    }
    // 지원 한 번으로 사이드 패배 하나를 지우는 가치와 주력의 잔존 위협을 비교합니다.
    boolean run = primary != mighty.suit() && control.topRun() >= 2
        && own.contains(Joker.INSTANCE) && sideLoss && residual < 1;
    return new NoTrumpControlPlan(primary, control, residual, run, entries);
  }

  /**
   * 총으로 선을 넘긴 뒤 독립된 일반 탑카로 회수할 수 있어야 합니다.
   */
  public boolean hasIndependentReturn() {
    return !returnEntries.isEmpty();
  }

  private static double choose(int n, int k) {
    if (k < 0 || k > n) {
      return 0;
    }
    double result = 1;
    for (int i = 1; i <= Math.min(k, n - k); i++) {
      result = result * (n - i + 1) / i;
    }
    return result;
  }
}
