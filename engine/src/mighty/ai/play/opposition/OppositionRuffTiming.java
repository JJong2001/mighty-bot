package mighty.ai.play.opposition;

import java.util.*;
import mighty.ai.play.*;
import mighty.game.action.Action;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.*;

/**
 * 낮은 사이드에서 뒤 야당의 일반 승리와 지금 간칠 가치를 비교합니다.
 */
final class OppositionRuffTiming {

  private OppositionRuffTiming() {
  }

  static Optional<PlayDecision> keepSecuredSide(View view, PlayFacts facts, List<Action> legal) {
    if (view.contract().trump().matches(facts.conditions().leadSuit()) || !facts.allyWinning()) {
      return Optional.empty();
    }
    var winner = facts.currentWinner().orElseThrow();
    var friend = facts.teams().friendSeat();
    boolean opponentsFinished = !facts.remainingSeats().contains(view.context().declarer())
        && (friend.map(seat -> !facts.remainingSeats().contains(seat)).orElse(
        view.context().friendStatus() instanceof mighty.game.friend.NoFriend));
    if (!opponentsFinished && !facts.isSecured(winner)) {
      return Optional.empty();
    }
    var passing = legal.stream().filter(action -> action.card() instanceof StandardCard card
        && !view.contract().trump().matches(card.suit())
        && !facts.winningActions().contains(action)).toList();
    if (passing.isEmpty()) {
      return Optional.empty();
    }
    boolean canRuff = facts.winningActions().stream().anyMatch(action ->
        action.card() instanceof StandardCard card && view.contract().trump().matches(card.suit()));
    if (!canRuff) {
      return Optional.empty();
    }
    var support = OppositionSupport.choose(view, facts, passing);
    return support.or(() -> Optional.of(new PlayDecision(PlaySupport.cheapest(passing, view),
        "여당이 뒤집을 수 없는 야당 승리에 간을 겹치지 않습니다.")));
  }

  static Optional<PlayDecision> choose(View view, PlayFacts facts, List<Action> legal) {
    if (view.contract().trump().matches(facts.conditions().leadSuit()) || facts.allyWinning()) {
      return Optional.empty();
    }
    int points = facts.trickPoints();
    if (points >= 3) {
      return Optional.empty();
    }
    boolean declarerOut = view.trick().stream()
        .anyMatch(p -> p.seat().equals(view.context().declarer())
            && p.action().card() instanceof StandardCard c && c.suit() != facts.conditions()
            .leadSuit()
            && !view.contract().trump().matches(c.suit()) && !c.equals(
            Rules.mighty(view.contract().trump())));
    boolean declarerWeakLead = view.trick().get(0).seat().equals(view.context().declarer())
        && !facts.isSuitTop(view.trick().get(0).action().card())
        && facts.teams().friendSeat().filter(friend -> view.trick().stream().anyMatch(p ->
        p.seat().equals(friend) && p.action().card() instanceof StandardCard card
            && card.suit() != facts.conditions().leadSuit()
            && !view.contract().trump().matches(card.suit())
            && !card.equals(Rules.mighty(view.contract().trump())))).isPresent();
    var lead = view.trick().get(0);
    boolean weakFriendLead = facts.teams().friendSeat().filter(lead.seat()::equals).isPresent()
        && lead.action().card() instanceof StandardCard card
        && card.points() == 0 && !facts.isSuitTop(card);
    boolean declarerLosingLead = lead.seat().equals(view.context().declarer())
        && lead.action().card() instanceof StandardCard card
        && !card.equals(Rules.mighty(view.contract().trump()))
        && !facts.isSuitTop(card);
    if (!declarerOut && !declarerWeakLead && !weakFriendLead && !declarerLosingLead) {
      return Optional.empty();
    }
    var winners = PlaySupport.ordinary(facts.winningActions(), view);
    var ruffs = winners.stream().filter(a ->
        view.contract().trump().matches(((StandardCard) a.card()).suit())).toList();
    if (ruffs.isEmpty()) {
      return Optional.empty();
    }
    var passing = PlaySupport.ordinary(legal, view).stream()
        .filter(a -> !view.contract().trump().matches(((StandardCard) a.card()).suit()))
        .filter(a -> !facts.winningActions().contains(a)).toList();
    if (passing.isEmpty()) {
      return Optional.empty();
    }
    var survival = new TrumpSurvival(view, facts);
    boolean onlyTopPointRuffWins = winners.size() == ruffs.size() && ruffs.stream().allMatch(a ->
        a.card().points() > 0 && survival.threatsAbove((StandardCard) a.card()) == 0);
    // 점수 유무와 별개로 카운팅상 가장 높은 기루는 다음 공격을 보장하는 자원입니다.
    if (declarerLosingLead && points < 2 && winners.size() == ruffs.size()
        && ruffs.stream().anyMatch(a -> survival.threatsAbove((StandardCard) a.card()) == 0)) {
      return Optional.of(new PlayDecision(PlaySupport.cheapest(passing, view),
          "주공의 낮은 리드에는 앞에 두 점이 쌓이기 전까지 탑 기루를 남깁니다."));
    }
    if (weakFriendLead && onlyTopPointRuffWins) {
      var declarer = view.context().declarer();
      var leadSuit = facts.conditions().leadSuit();
      boolean declarerBehind = facts.remainingSeats().contains(declarer);
      if (declarerBehind && facts.cards().isVoid(declarer, leadSuit)
          && declarerCanOvertakeByRuff(view, facts)) {
        return Optional.of(new PlayDecision(PlaySupport.cheapest(passing, view),
            "뒤 주공이 간을 칠 수 있어 자기 기루를 먼저 쓰게 하고 탑 기루를 남깁니다."));
      }
      // 이 기루의 점수는 제외합니다. 뒤 주공의 탑카 근거가 강하면 한 점에도 끊습니다.
      if (points == 1 && declarerBehind && declarerLikelyHoldsTop(view, facts)) {
        return Optional.empty();
      }
      if (points < 2) {
        return Optional.of(new PlayDecision(PlaySupport.cheapest(passing, view),
            "앞에 두 점이 쌓이지 않아 탑 기루를 다음 공격에 남깁니다."));
      }
    }
    if (facts.cards().outsideCount(facts.conditions().leadSuit()) < 2) {
      return Optional.empty();
    }
    boolean laterAlly = facts.remainingSeats().stream().anyMatch(seat -> facts.teams().isAlly(seat)
        && !facts.cards().isVoid(seat, facts.conditions().leadSuit()));
    if (!laterAlly) {
      return Optional.empty();
    }
    if (weakFriendLead && !declarerOut && ruffs.stream().anyMatch(a ->
        survival.threatsAbove((StandardCard) a.card()) == 0)) {
      return Optional.of(new PlayDecision(PlaySupport.cheapest(passing, view),
          "뒤 야당이 리드 문양을 잡을 수 있어 작은 트릭에 탑 기루를 쓰지 않습니다."));
    }
    if (!declarerOut && !declarerWeakLead) {
      return Optional.empty();
    }
    var pressure = new DeclarerPressure(view, facts);
    var support = passing.stream()
        .filter(a -> a.card().points() > 0 && !pressure.defense((StandardCard) a.card()))
        .min(Comparator.comparingInt(a -> ((StandardCard) a.card()).rank().value()));
    Action chosen = support.orElseGet(() -> PlaySupport.cheapest(passing, view));
    return Optional.of(
        new PlayDecision(chosen, "주공이 흘렸고 뒤 야당이 리드 문양으로 잡을 여지가 있어 점수를 지원하고 기루 회수를 남깁니다."));
  }

  private static boolean declarerCanOvertakeByRuff(View view, PlayFacts facts) {
    var declarer = view.context().declarer();
    int winningStrength = Rules.strength(facts.currentWinner().orElseThrow().action().card(),
        facts.conditions());
    return facts.cards().trumps().outside().stream().anyMatch(card ->
        !facts.cards().isVoid(declarer, card.suit())
            && Rules.strength(card, facts.conditions()) > winningStrength);
  }

  /**
   * 주공의 공개 운영 문양에서 선의 물패와 나머지 자리의 무문양을 함께 봅니다.
   */
  private static boolean declarerLikelyHoldsTop(View view, PlayFacts facts) {
    var declarer = view.context().declarer();
    var suit = facts.conditions().leadSuit();
    if (!DeclarerOperations.contains(view, facts, suit)) {
      return false;
    }
    int winningStrength = Rules.strength(facts.currentWinner().orElseThrow().action().card(),
        facts.conditions());
    boolean outsideTopCanWin = facts.cards().outsideCards().stream()
        .filter(StandardCard.class::isInstance).map(StandardCard.class::cast)
        .filter(card -> card.suit() == suit && facts.isSuitTop(card))
        .anyMatch(card -> Rules.strength(card, facts.conditions()) > winningStrength);
    if (!outsideTopCanWin) {
      return false;
    }
    return view.context().playOrder().seats().stream()
        .filter(seat -> !seat.equals(view.self()) && !seat.equals(declarer)
            && !seat.equals(view.context().leader()))
        .allMatch(seat -> facts.cards().trumps().remainingHand(seat) == 0 || facts.cards()
            .isVoid(seat, suit));
  }

}
