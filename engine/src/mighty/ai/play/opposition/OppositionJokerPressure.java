package mighty.ai.play.opposition;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import mighty.ai.play.PlayDecision;
import mighty.ai.play.PlayFacts;
import mighty.ai.play.PlaySupport;
import mighty.ai.play.TrumpSurvival;
import mighty.game.action.Action;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.Joker;
import mighty.model.StandardCard;

/**
 * 공개된 기루 서열로 조커 공격에 실을 점수와 남길 수비를 판단합니다.
 */
final class OppositionJokerPressure {

  private OppositionJokerPressure() {
  }

  static Optional<PlayDecision> choose(View view, PlayFacts facts, List<Action> legal) {
    var winner = facts.currentWinner().orElseThrow();
    boolean trumpLead = view.contract().trump().matches(facts.conditions().leadSuit());
    var alliedBehind = new java.util.HashSet<>(facts.remainingSeats().stream()
        .filter(facts.teams()::isLikelyAlly).toList());
    boolean inferredJokerBehind = facts.joker().likelyAt(alliedBehind);
    if ((!trumpLead && !inferredJokerBehind)
        || !(winner.action().card() instanceof StandardCard top) || !facts.isSuitTop(top)) {
      return Optional.empty();
    }
    var mighty = Rules.mighty(view.contract().trump());
    boolean mightyOutside = facts.cards().outsideCards().contains(mighty);
    boolean allyBehind = facts.remainingSeats().stream().anyMatch(facts.teams()::isAlly);
    if (!facts.allyWinning() && !mightyOutside) {
      var joker = facts.winningActions().stream().filter(a -> a.card() instanceof Joker)
          .findFirst();
      int points = facts.trickPoints();
      if (joker.isPresent() && (points >= 2 || (points == 1 && allyBehind))
          && PlaySupport.ordinary(facts.winningActions(), view).isEmpty()) {
        return Optional.of(new PlayDecision(joker.get(),
            "조커로 여당의 최고 기루를 잡아 앞의 점수를 확보하고 뒤 야당의 지원을 받습니다."));
      }
    }
    if (!winner.seat().equals(view.context().declarer()) || mightyOutside
        || !allyBehind || !PlaySupport.ordinary(facts.winningActions(), view).isEmpty()
        || facts.cards().isDesignatedFriendCard(Joker.INSTANCE)
        || !facts.cards().outsideCards().contains(Joker.INSTANCE)
        || Rules.strength(Joker.INSTANCE, facts.conditions()) <= Rules.strength(top,
        facts.conditions())
        || (!inferredJokerBehind && jokerLikelyOnDeclarerTeam(view, facts))) {
      return Optional.empty();
    }
    var ownTrump = facts.cards().trumps().own();
    var survival = new TrumpSurvival(view, facts);
    var pressure = new DeclarerPressure(view, facts);
    long pointsInHand = view.hand().stream().mapToInt(card -> card.points()).sum();
    return PlaySupport.ordinary(legal, view).stream().filter(a -> a.card().points() > 0)
        .filter(a -> {
          var card = (StandardCard) a.card();
          if (view.contract().trump().matches(card.suit())) {
            return ownTrump.size() <= 2 && !facts.isSuitTop(card) && !survival.survives(card);
          }
          return ownTrump.isEmpty() && pointsInHand >= 3 && !pressure.defense(card);
        })
        .filter(a -> !facts.winningActions().contains(a))
        .min(Comparator.comparingInt(a -> ((StandardCard) a.card()).rank().value()))
        .map(a -> new PlayDecision(a, "뒤 야당이 조커로 받을 수 있어 보존 가치가 낮은 점수 한 장을 지원합니다."));
  }

  static boolean jokerLikelyOnDeclarerTeam(View view, PlayFacts facts) {
    var seats = new java.util.HashSet<mighty.game.Seat>();
    seats.add(view.context().declarer());
    facts.teams().friendSeat().ifPresent(seats::add);
    seats.addAll(facts.behavior().likelyFriends());
    return facts.joker().likelyAt(seats);
  }
}
