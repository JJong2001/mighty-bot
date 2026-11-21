package mighty.ai.play.friend;

import java.util.*;
import mighty.ai.play.*;
import mighty.game.action.Action;
import mighty.game.observation.View;
import mighty.game.trick.PublicPlay;
import mighty.model.StandardCard;
import mighty.model.Joker;
import mighty.model.Trump;
import mighty.game.rule.Rules;

/**
 * 안전한 승리나 특수카드 회수가 없으면 불확실한 점수 역전을 겹쳐 내지 않습니다.
 */
final class FriendRiskSupport {

  /**
   * 야당의 선은 문양에 관계없이 주공의 요청이 아닙니다. 뒤 주공이 행동할 여지를 남깁니다.
   */
  static Optional<PlayDecision> beforeDeclarerResponse(View view, PlayFacts facts,
      List<Action> legal) {
    var lead = view.trick().get(0);
    var mighty = Rules.mighty(view.contract().trump());
    if (facts.teams().isAlly(lead.seat())
        || !facts.remainingSeats().contains(view.context().declarer())) {
      return Optional.empty();
    }
    var mightyAction = legal.stream().filter(action -> action.card().equals(mighty)).findFirst();
    if (mightyAction.isEmpty()) {
      return Optional.empty();
    }
    // 노기루의 공개 주력은 마이티를 소모할 문양입니다. 기존의 주력 지원 순서를 유지합니다.
    if (view.contract().trump() == Trump.NO_TRUMP
        && FriendSupportIntent.disclosedPrimary(view).filter(suit -> suit == facts.conditions()
        .leadSuit()).isPresent()) {
      return Optional.empty();
    }
    var ordinary = PlaySupport.ordinary(legal, view);
    var secured = ordinary.stream().filter(facts.winningActions()::contains)
        .filter(action -> facts.isSecured(new PublicPlay(view.self(), action))
            || ((StandardCard) action.card()).suit() == facts.conditions().leadSuit()
            && facts.isSuitTop(action.card())).toList();
    if (!secured.isEmpty()) {
      return Optional.of(new PlayDecision(PlaySupport.cheapest(secured, view),
          "야당의 리드를 일반 승리 카드로 잡고 주공 요청에 쓸 마이티를 남깁니다."));
    }
    var water = ordinary.stream().filter(action -> action.card().points() == 0).toList();
    if (!water.isEmpty()) {
      return Optional.of(new PlayDecision(PlaySupport.cheapest(water, view),
          "야당의 선에서는 뒤 주공의 의도를 확인할 수 있도록 물패로 숨습니다."));
    }
    return Optional.of(new PlayDecision(mightyAction.get(),
        "안전한 물패가 없어 점수를 헌납하지 않도록 마이티로 잡습니다."));
  }

  private FriendRiskSupport() {
  }

  /**
   * 주공이 상위를 확인하러 낮춰 낸 기루에는 임시 역전용 점수를 얹지 않습니다.
   */
  static Optional<PlayDecision> onLowTrumpProbe(View view, PlayFacts facts, List<Action> legal) {
    var lead = view.trick().get(0);
    var trump = view.contract().trump();
    if (!lead.seat().equals(view.context().declarer())
        || !(lead.action().card() instanceof StandardCard card)
        || card.points() != 0 || !trump.matches(card.suit())) {
      return Optional.empty();
    }
    var spent = facts.cards().publicHistory().cards();
    if (!spent.contains(Rules.mighty(trump)) || !spent.contains(Joker.INSTANCE)) {
      return Optional.empty();
    }
    boolean defenderBehind = facts.remainingSeats().stream().anyMatch(seat ->
        !facts.teams().isAlly(seat) && !facts.cards().isVoid(seat, card.suit()));
    if (!defenderBehind) {
      return Optional.empty();
    }
    var following = PlaySupport.ordinary(legal, view).stream()
        .filter(action -> ((StandardCard) action.card()).suit() == card.suit()).toList();
    var points = following.stream().filter(action -> action.card().points() > 0).toList();
    if (points.isEmpty() || points.stream().anyMatch(action -> facts.isSuitTop(action.card()))) {
      return Optional.empty();
    }
    var low = following.stream().filter(action -> action.card().points() == 0).toList();
    if (low.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(new PlayDecision(PlaySupport.highest(low),
        "주공의 낮은 기루에 뒤 야당 상위가 남을 수 있어 점수를 얹지 않습니다."));
  }

  /**
   * 조커콜 위험이 없으면 점수 없는 기루를 넘겨 더 높은 기루를 잡을 기회를 남깁니다.
   */
  static Optional<PlayDecision> reserveJokerForHigherTrump(View view, PlayFacts facts,
      List<Action> legal) {
    var trump = view.contract().trump();
    var lead = view.trick().get(0);
    var winner = facts.currentWinner().orElseThrow();
    if (!(view.friendSelection() instanceof mighty.game.friend.CardFriend friend)
        || !(friend.card() instanceof Joker) || !lead.seat().equals(view.context().declarer())
        || !(winner.action().card() instanceof StandardCard current) || !trump.matches(
        current.suit())
        || !trump.matches(facts.conditions().leadSuit()) || facts.allyWinning()
        || view.trick().stream().anyMatch(play -> play.action().card().points() > 0)
        || !facts.cards().trumps().own().isEmpty()
        || facts.winningActions().stream().noneMatch(action -> action.card() instanceof Joker)) {
      return Optional.empty();
    }
    var spent = facts.cards().publicHistory().cards();
    var gun = Rules.gun(trump);
    if ((!view.hand().contains(gun) && !spent.contains(gun)) || !spent.contains(Rules.mighty(trump))
        || facts.remainingSeats().stream()
        .anyMatch(seat -> !facts.cards().isVoid(seat, current.suit()))) {
      return Optional.empty();
    }
    if (facts.cards().trumps().outside().stream().noneMatch(card ->
        card.rank().value() > current.rank().value() && card.points() > 0)) {
      return Optional.empty();
    }
    var resources = new FriendResources(view, facts);
    return PlaySupport.ordinary(legal, view).stream().filter(action -> action.card().points() == 0)
        .min(Comparator.comparingInt(action -> resources.discardCost((StandardCard) action.card())))
        .map(action -> new PlayDecision(action, "빵이 안전하고 뒤가 무기루라 조커를 더 높은 기루에 남깁니다."));
  }

  static Optional<PlayDecision> choose(View view, PlayFacts facts, List<Action> legal) {
    if (facts.winningActions().stream().anyMatch(a -> !(a.card() instanceof StandardCard)
        || a.card().equals(mighty.game.rule.Rules.mighty(view.contract().trump()))
        || facts.isSuitTop(a.card()) || facts.isSecured(new PublicPlay(view.self(), a)))) {
      return Optional.empty();
    }
    var passing = PlaySupport.ordinary(legal, view).stream().filter(a -> a.card().points() == 0)
        .filter(a -> !facts.winningActions().contains(a)).toList();
    if (passing.isEmpty()) {
      return Optional.empty();
    }
    FriendResources resources = new FriendResources(view, facts);
    return passing.stream()
        .min(Comparator.comparingInt(a -> resources.discardCost((StandardCard) a.card())))
        .map(a -> new PlayDecision(a, "뒤 상위에 질 점수를 추가하지 않고 점수의 보호 물패와 주공 운영 문양을 구분해 따릅니다."));
  }

}
