package mighty.ai.play;

import java.util.*;
import mighty.game.Seat;
import mighty.game.TrickNumber;
import mighty.game.friend.*;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.game.trick.PublicPlay;
import mighty.model.*;

/**
 * 자기 패·버림패·역할을 읽지 않고 모든 관측자가 공유할 공개 행동 증거만 구성합니다.
 */
public final class PublicBehaviorInference {

  public enum SupportEvidence {
    ANSWERED_REQUEST, RETURNED_TRUMP, CONTINUED_WITH_TOP
  }

  private final Set<Seat> likelyFriends;
  private final Map<Seat, Set<SupportEvidence>> support;
  private final Map<Seat, Integer> missedJokerCaptures;

  public PublicBehaviorInference(View view) {
    Map<Seat, EnumSet<SupportEvidence>> observed = new HashMap<>();
    var attacks = OppositionSignals.openingAttackers(view);
    var declarer = view.context().declarer();
    var mighty = Rules.mighty(view.contract().trump());
    Set<Card> spent = new HashSet<>();
    for (var trick : view.context().history()) {
      var lead = trick.cards().get(0).action().card();
      if (trick.leader().equals(declarer) && lead instanceof StandardCard card
          && !card.equals(mighty) && !trick.winner().equals(declarer)
          && (card.points() == 0 || !publicTop(card, view, spent))) {
        add(observed, trick.winner(), SupportEvidence.ANSWERED_REQUEST);
      }
      observeLead(view, trick.cards(), trick.number(), spent, observed);
      trick.cards().forEach(play -> spent.add(play.action().card()));
    }
    observeLead(view, view.trick(), view.trickNumber(), spent, observed);
    Map<Seat, Set<SupportEvidence>> copy = new HashMap<>();
    observed.forEach((seat, evidence) -> copy.put(seat, Set.copyOf(evidence)));
    support = Map.copyOf(copy);
    Set<Seat> candidates = new HashSet<>();
    if (!(view.context().friendStatus() instanceof NoFriend)) {
      int best = 0;
      for (var entry : observed.entrySet()) {
        int score = entry.getValue().size()
            + (entry.getValue().contains(SupportEvidence.RETURNED_TRUMP) ? 2 : 0)
            - (attacks.contains(entry.getKey()) ? 3 : 0);
        if (score > best) {
          candidates.clear();
          best = score;
        }
        if (score == best && score >= 2) {
          candidates.add(entry.getKey());
        }
      }
    }
    likelyFriends = Set.copyOf(candidates);
    Map<Seat, Integer> missed = new HashMap<>();
    boolean mightySpent = false;
    var knownFriend = publicFriend(view);
    if (view.publicCardHistory().hasBeenPlayed(mighty)
        && !view.publicCardHistory().hasBeenPlayed(Joker.INSTANCE)) {
      for (var trick : view.context().history()) {
        mightySpent = observeJokerOpportunities(view, trick.cards(), trick.number(), mightySpent,
            knownFriend, attacks, missed);
      }
      observeJokerOpportunities(view, view.trick(), view.trickNumber(), mightySpent,
          knownFriend, attacks, missed);
    }
    missedJokerCaptures = Map.copyOf(missed);
  }

  public Set<Seat> likelyFriends() {
    return likelyFriends;
  }

  public Map<Seat, Set<SupportEvidence>> supportEvidence() {
    return support;
  }

  public Map<Seat, Integer> missedJokerCaptures() {
    return missedJokerCaptures;
  }

  private static Optional<Seat> publicFriend(View view) {
    if (view.context().friendStatus() instanceof RevealedFriend friend) {
      return Optional.of(friend.seat());
    }
    if (view.friendSelection() instanceof CardFriend friend) {
      return java.util.stream.Stream.concat(view.context().history().stream()
              .flatMap(t -> t.cards().stream()), view.trick().stream())
          .filter(p -> !p.seat().equals(view.context().declarer())
              && p.action().card().equals(friend.card())).map(PublicPlay::seat).findFirst();
    }
    return Optional.empty();
  }

  private boolean observeJokerOpportunities(View view, List<PublicPlay> plays, TrickNumber number,
      boolean mightySpent, Optional<Seat> knownFriend, Set<Seat> attackers,
      Map<Seat, Integer> missed) {
    if (plays.isEmpty()) {
      return mightySpent;
    }
    var conditions = Rules.conditionsFor(view.contract().trump(), number, plays.get(0).action());
    var mighty = Rules.mighty(view.contract().trump());
    List<PublicPlay> prefix = new ArrayList<>();
    int points = 0;
    for (var play : plays) {
      var winner = Rules.currentWinner(view.contract().trump(), number, prefix);
      if (mightySpent && number.value() > 1 && number.value() < 10 && !conditions.jokerCalled()
          && points >= 2 && winner.isPresent() && !(play.action().card() instanceof Joker)
          && Rules.strength(Joker.INSTANCE, conditions)
          > Rules.strength(winner.get().action().card(), conditions)
          && Rules.strength(play.action().card(), conditions)
          <= Rules.strength(winner.get().action().card(), conditions)
          && opposed(view, play.seat(), winner.get().seat(), knownFriend, attackers)) {
        missed.merge(play.seat(), 1, Integer::sum);
      }
      prefix.add(play);
      points += play.action().card().points();
      mightySpent |= play.action().card().equals(mighty);
    }
    return mightySpent;
  }

  private boolean opposed(View view, Seat actor, Seat winner, Optional<Seat> knownFriend,
      Set<Seat> attackers) {
    var declarer = view.context().declarer();
    if (view.context().friendStatus() instanceof NoFriend) {
      return actor.equals(declarer) != winner.equals(declarer);
    }
    if (knownFriend.isPresent() || likelyFriends.size() == 1) {
      var friend = knownFriend.orElseGet(() -> likelyFriends.iterator().next());
      return (actor.equals(declarer) || actor.equals(friend))
          != (winner.equals(declarer) || winner.equals(friend));
    }
    return actor.equals(declarer) && !likelyFriends.contains(winner)
        || winner.equals(declarer) && attackers.contains(actor);
  }

  private static void observeLead(View view, List<PublicPlay> plays, TrickNumber number,
      Set<Card> spent, Map<Seat, EnumSet<SupportEvidence>> observed) {
    if (plays.isEmpty()) {
      return;
    }
    var lead = plays.get(0);
    if (lead.seat().equals(view.context().declarer())) {
      return;
    }
    if (number.value() <= 3 && view.contract().trump().matches(
        Rules.conditionsFor(view.contract().trump(), number, lead.action()).leadSuit())) {
      add(observed, lead.seat(), SupportEvidence.RETURNED_TRUMP);
    }
    if (lead.action().card() instanceof StandardCard card
        && observed.getOrDefault(lead.seat(), EnumSet.noneOf(SupportEvidence.class))
        .contains(SupportEvidence.ANSWERED_REQUEST)
        && !card.equals(Rules.mighty(view.contract().trump()))
        && publicTop(card, view, spent)) {
      add(observed, lead.seat(), SupportEvidence.CONTINUED_WITH_TOP);
    }
  }

  private static boolean publicTop(StandardCard card, View view, Set<Card> spent) {
    return Arrays.stream(Rank.values()).filter(rank -> rank.value() > card.rank().value())
        .map(rank -> new StandardCard(card.suit(), rank))
        .allMatch(higher -> higher.equals(Rules.mighty(view.contract().trump())) || spent.contains(
            higher));
  }

  private static void add(Map<Seat, EnumSet<SupportEvidence>> observed, Seat seat,
      SupportEvidence evidence) {
    observed.computeIfAbsent(seat, ignored -> EnumSet.noneOf(SupportEvidence.class)).add(evidence);
  }
}
