package mighty.ai.play;

import java.util.*;
import mighty.game.Role;
import mighty.game.Seat;
import mighty.game.observation.View;
import mighty.model.Joker;

/**
 * 공개된 사용 기회와 자기 조커 보유 여부를 결합합니다. 상대의 실제 손패는 조회하지 않습니다.
 */
public final class JokerBelief {

  private static final double SUPPORT_ODDS = 1.5;
  private final View view;
  private final TeamKnowledge teams;
  private final PublicBehaviorInference behavior;
  private final Map<Seat, Double> weights;

  public JokerBelief(View view, TeamKnowledge teams, PublicBehaviorInference behavior) {
    this(view, teams, behavior, new CardLocationInference(view, new CardKnowledge(view), behavior));
  }

  JokerBelief(View view, TeamKnowledge teams, PublicBehaviorInference behavior,
      CardLocationInference locations) {
    this.view = view;
    this.teams = teams;
    this.behavior = behavior;
    Map<Seat, Double> values = new HashMap<>();
    boolean buried = view.privateInfo() instanceof mighty.game.observation.DeclarerInfo info
        && info.discardedCards().contains(Joker.INSTANCE);
    if (!buried && !view.publicCardHistory().hasBeenPlayed(Joker.INSTANCE)) {
      if (view.hand().contains(Joker.INSTANCE)) {
        values.put(view.self(), 1.0);
      } else if (locations.suggestedHolders(Joker.INSTANCE).size() == 1) {
        // 총을 선출한 주공은 조커 보유로 운영합니다. 실제 공개·자기 패가 우선입니다.
        values.put(locations.suggestedHolders(Joker.INSTANCE).iterator().next(), 1.0);
      } else {
        for (var seat : view.context().playOrder().seats()) {
          if (!locations.possibleHolders(Joker.INSTANCE).contains(seat)) {
            continue;
          }
          int remaining = 10 - view.context().history().size()
              - (int) view.trick().stream().filter(p -> p.seat().equals(seat)).count();
          if (remaining > 0) {
            int declined = behavior.missedJokerCaptures().getOrDefault(seat, 0);
            values.put(seat, remaining * Math.pow(0.25, Math.min(3, declined)));
          }
        }
      }
    }
    weights = Map.copyOf(values);
  }

  /**
   * 보정된 빈도나 확률이 아닌, 공개 증거에 따른 상대적인 보유 가능성 가중치입니다.
   */
  public Map<Seat, Double> relativeWeights() {
    return weights;
  }

  public boolean likelyAt(Set<Seat> seats) {
    if (view.hand().contains(Joker.INSTANCE)) {
      return seats.contains(view.self());
    }
    if (weights.isEmpty() || behavior.missedJokerCaptures().isEmpty()
        && behavior.holdingHints().stream().noneMatch(h -> h.card() instanceof Joker)) {
      return false;
    }
    double inside = weights.entrySet().stream().filter(e -> seats.contains(e.getKey()))
        .mapToDouble(Map.Entry::getValue).sum();
    double outside = weights.entrySet().stream().filter(e -> !seats.contains(e.getKey()))
        .mapToDouble(Map.Entry::getValue).sum();
    return inside > 0 && inside >= SUPPORT_ODDS * outside;
  }

  public boolean likelyHeldByOpposition() {
    Set<Seat> opposition = new HashSet<>();
    var friend = teams.friendSeat();
    if (view.role() == Role.FRIEND) {
      friend = Optional.of(view.self());
    }
    if (friend.isEmpty() && behavior.likelyFriends().size() == 1) {
      friend = behavior.likelyFriends().stream().findFirst();
    }
    if (friend.isEmpty() && !(view.context()
        .friendStatus() instanceof mighty.game.friend.NoFriend)) {
      return false;
    }
    for (var seat : view.context().playOrder().seats()) {
      if (!seat.equals(view.context().declarer()) && !friend.filter(seat::equals).isPresent()) {
        opposition.add(seat);
      }
    }
    return likelyAt(opposition);
  }
}
