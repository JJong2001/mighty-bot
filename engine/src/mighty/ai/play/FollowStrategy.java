package mighty.ai.play;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import mighty.ai.play.friend.FriendWinningSupport;
import mighty.game.Role;
import mighty.game.action.Action;
import mighty.game.friend.CardFriend;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.Joker;
import mighty.model.Rank;
import mighty.model.StandardCard;
import mighty.model.Trump;

/**
 * 주공과 프렌드가 함께 사용하는 탑카 보호 및 지원·회수 규칙입니다.
 */
public final class FollowStrategy {

  private FollowStrategy() {
  }

  public static PlayDecision choose(View view, PlayFacts facts, List<Action> legal) {
    List<Action> ordinary = PlaySupport.ordinary(legal, view);
    boolean ally = facts.allyWinning();
    boolean secured = facts.isSecured(facts.currentWinner().orElseThrow());
    var protectedAlly = protectAlly(view, facts, legal);
    if (protectedAlly.isPresent()) {
      return protectedAlly.get();
    }
    boolean declarerLeading = view.trick().get(0).seat().equals(view.context().declarer());
    if (view.role() == Role.FRIEND && declarerLeading
        && view.trick().get(0).action().card() instanceof StandardCard lead
        && view.contract().trump().matches(lead.suit())) {
      if (lead.rank().value() < Rank.KING.value() && !secured && ally) {
        if (view.friendSelection() instanceof CardFriend friend
            && friend.card().equals(Rules.mighty(view.contract().trump()))) {
          var mighty = legal.stream().filter(action -> action.card().equals(friend.card()))
              .findFirst();
          if (mighty.isPresent()) {
            return new PlayDecision(mighty.get(), "낮은 기루의 마이티 프렌드 호출에 응답합니다.");
          }
        }
        List<Action> winningTrumps = facts.winningActions().stream()
            .filter(action -> action.card() instanceof StandardCard card
                && view.contract().trump().matches(card.suit())).toList();
        var jokerSupport = facts.winningActions().stream().filter(a -> a.card() instanceof Joker)
            .findFirst();
        if (jokerSupport.isPresent() && winningTrumps.stream()
            .noneMatch(a -> facts.isSuitTop(a.card()))) {
          return new PlayDecision(jokerSupport.get(), "낮은 기루 호출을 불확실한 Q로 대체하지 않고 조커로 확보합니다.");
        }
        if (!winningTrumps.isEmpty()) {
          return new PlayDecision(PlaySupport.highest(winningTrumps),
              "낮은 기루의 지원 요청에는 A 등 높은 기루로 확실히 응답합니다.");
        }
      }
    }
    // 주공이 잠시 선두라는 이유만으로 프렌드의 승리 지원을 차단하지 않습니다.
    var friendSupport = FriendWinningSupport.choose(view, facts, legal);
    if (friendSupport.isPresent()) {
      return friendSupport.get();
    }
    List<Action> winning = facts.winningActions();
    if (!ally && !winning.isEmpty()) {
      var specialOrder = SpecialCardPlan.mightyBeforeJoker(winning, view);
      if (specialOrder.isPresent()) {
        return new PlayDecision(specialOrder.get(), "마이티를 먼저 사용하고 조커의 문양 선택권을 남깁니다.");
      }
      List<Action> normalWinners = PlaySupport.ordinary(winning, view);
      if (!normalWinners.isEmpty()) {
        Action chosen = RuffPlan.choose(view, facts, normalWinners)
            .orElseGet(() -> PlaySupport.cheapest(normalWinners, view));
        return new PlayDecision(chosen, "현재 야당 또는 상대 선두를 가장 낮은 일반 승리 카드로 잡습니다.");
      }
      int points = facts.trickPoints();
      if (points > 0 || view.role() == Role.FRIEND || (view.trickNumber().value() == 9
          && !FriendSupportIntent.preserveJoker(view,
          facts))) {
        return new PlayDecision(PlaySupport.cheapest(winning, view),
            "상대가 실제로 이기고 있어 점수와 선을 특수카드로 회수합니다.");
      }
    }
    return support(view, facts, ordinary.isEmpty() ? legal : ordinary,
        "역전 가치가 불확실하면 회수 카드와 수비 문양을 보존합니다.");
  }

  /**
   * 역할별 회수 계획보다 먼저 동료 마이티·확보된 승리·문양 탑카를 보호합니다.
   */
  public static Optional<PlayDecision> protectAlly(View view, PlayFacts facts, List<Action> legal) {
    var winner = facts.currentWinner().orElseThrow();
    if (!facts.allyWinning()) {
      return Optional.empty();
    }
    boolean allyRuff = facts.allyRuffWinning();
    boolean protect = allyRuff || winner.action().card() instanceof Joker || facts.isSecured(winner)
        || winner.action().card().equals(Rules.mighty(view.contract().trump()))
        || facts.isSuitTop(winner.action().card());
    if (!protect) {
      return Optional.empty();
    }
    List<Action> ordinary = PlaySupport.ordinary(legal, view);
    List<Action> passing = facts.passingActions(ordinary);
    // 마이티보다 약한 기루도 승리 수에서는 '지는 카드'입니다.
    // 다른 문양을 버릴 수 있다면 그 기루를 점수 지원으로 소모하지 않습니다.
    if (!view.contract().trump().matches(facts.conditions().leadSuit())) {
      var sideDiscards = passing.stream()
          .filter(action -> action.card() instanceof StandardCard card
              && !view.contract().trump().matches(card.suit())).toList();
      if (!sideDiscards.isEmpty()) {
        passing = sideDiscards;
      }
    }
    // 보호는 특수카드뿐 아니라 불필요한 일반 기루 간도 막아야 합니다.
    List<Action> choices = !passing.isEmpty() ? passing : ordinary.isEmpty() ? legal : ordinary;
    return Optional.of(support(view, facts, choices,
        "동료의 탑카를 덮지 않고 점수·물패를 처리합니다. 강제 승리 제출은 허용합니다."));
  }

  /**
   * 안전한 점수 지원, 프렌드 역순 신호, NT 수비 문양 보존을 처리합니다.
   */
  private static PlayDecision support(View view, PlayFacts facts, List<Action> actions,
      String reason) {
    var decisive = ScoreSupport.decisive(view, facts, actions);
    if (decisive.isPresent()) {
      return new PlayDecision(decisive.get(), reason + " 성패를 결정하는 점수를 지원합니다.");
    }
    if (facts.allyWinning() && (facts.isSecured(facts.currentWinner().orElseThrow())
        || facts.isSuitTop(facts.currentWinner().orElseThrow().action().card()))) {
      CardResources resources = new CardResources(view, facts);
      List<Action> points = actions.stream().filter(action -> action.card().points() > 0
          && !resources.reserved(action.card())).toList();
      if (!points.isEmpty()) {
        return new PlayDecision(points.stream().min(Comparator.<Action>comparingInt(action ->
            supportPriority(action, view, facts))).orElseThrow(),
            reason + " 살리기 어려운 짧은 문양 점수부터 처리합니다.");
      }
    }
    CardResources resources = new CardResources(view, facts);
    int priority = actions.stream().mapToInt(action -> resources.discardTier(action.card()))
        .min().orElseThrow();
    List<Action> candidates = actions.stream()
        .filter(action -> resources.discardTier(action.card()) == priority).toList();
    List<Action> low = candidates.stream().filter(action -> action.card().points() == 0
        && action.card() instanceof StandardCard).toList();
    if (!low.isEmpty()) {
      return new PlayDecision(low.stream().min(Comparator.<Action>comparingInt(action ->
              resources.retentionPriority(action.card()))
          .thenComparingInt(action -> discardPriority(action, view, facts))).orElseThrow(), reason);
    }
    return new PlayDecision(candidates.stream().min(Comparator.comparingInt(action ->
        resources.preservationCost(action.card()))).orElseThrow(),
        reason + " 강제 제출에서는 회수 자원 손실을 최소화합니다.");
  }

  /**
   * 일반 탑카를 보존하고 짧아서 살아남기 어려운 점수부터 안전하게 지원합니다.
   */
  private static int supportPriority(Action action, View view, PlayFacts facts) {
    StandardCard card = (StandardCard) action.card();
    long length = view.hand().stream()
        .filter(held -> held instanceof StandardCard standard && standard.suit() == card.suit())
        .count();
    return (facts.isSuitTop(card) ? 100 : 0) + (int) length * 10 + card.rank().value();
  }

  /**
   * 노기루 수비에서는 긴 문양을 보존하고 짧은 비점수 문양부터 비웁니다.
   */
  private static int discardPriority(Action action, View view, PlayFacts facts) {
    StandardCard card = (StandardCard) action.card();
    long length = view.hand().stream()
        .filter(held -> held instanceof StandardCard standard && standard.suit() == card.suit())
        .count();
    int gun = card.equals(Rules.gun(view.contract().trump())) ? 15 : 0;
    int guard = view.role() == Role.OPPOSITION
        ? mighty.ai.play.opposition.OppositionGuards.retention(view, facts, card.suit()) : 0;
    int coverage = mighty.ai.play.friend.FriendReturnReserve.protects(view, card) ? 80 : 0;
    return guard + coverage + (int) length * 20 + card.rank().value() + gun
        + (view.contract().trump() != Trump.NO_TRUMP && view.contract().trump().matches(card.suit())
        ? 50 : 0);
  }

}
