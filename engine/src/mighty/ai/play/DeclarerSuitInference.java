package mighty.ai.play;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import mighty.game.TrickNumber;
import mighty.game.action.StandardPlay;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.game.trick.PublicPlay;
import mighty.model.Rank;
import mighty.model.Suit;

/**
 * 공개된 운영 증거입니다. 주공의 실제 부기루 장수나 상위 보유를 확정하지 않습니다.
 */
public final class DeclarerSuitInference {

  public enum Evidence {
    DECLARED_SUIT, OPENING_POINT_SIGNAL, MIGHTY_SUPPORTED_OPENING, LATER_LEAD, REPEATED_LEAD
  }

  public record Assessment(Set<Evidence> evidence, int ordinaryLeads, boolean shownVoid,
                           boolean trump) {

    public Assessment {
      evidence = Set.copyOf(evidence);
    }

    public boolean operating() {
      return !shownVoid && !evidence.isEmpty();
    }

    /**
     * 기루 외 운영 후보. NT에서는 주력·부기루의 장수 순위를 단정하지 않습니다.
     */
    public boolean sideCandidate() {
      return !trump && operating();
    }

    /**
     * 반복·공개 공약·실제 지원으로 보강된 근거이지 손패 소유의 확정이 아닙니다.
     */
    public boolean reinforced() {
      return operating() && (evidence.contains(Evidence.DECLARED_SUIT)
          || evidence.contains(Evidence.REPEATED_LEAD)
          || evidence.contains(Evidence.MIGHTY_SUPPORTED_OPENING));
    }

    /**
     * 초구 점수 한 장만의 신호와 이후 실제 운영·공개 요청을 구분합니다.
     */
    public boolean beyondOpeningSignal() {
      return operating() && evidence.stream()
          .anyMatch(item -> item != Evidence.OPENING_POINT_SIGNAL);
    }
  }

  private final Map<Suit, Assessment> suits = new EnumMap<>(Suit.class);

  DeclarerSuitInference(View view, CardKnowledge cards) {
    Map<Suit, EnumSet<Evidence>> evidence = new EnumMap<>(Suit.class);
    Map<Suit, Integer> counts = new EnumMap<>(Suit.class);
    for (Suit suit : Suit.values()) {
      evidence.put(suit, EnumSet.noneOf(Evidence.class));
      counts.put(suit, 0);
      if (view.contract().trump().matches(suit)
          || view.context().contractHistory().stream().anyMatch(bid -> bid.trump().matches(suit))) {
        evidence.get(suit).add(Evidence.DECLARED_SUIT);
      }
    }
    for (var trick : view.context().history()) {
      observe(view, trick.number(), trick.cards(), evidence, counts);
    }
    observe(view, view.trickNumber(), view.trick(), evidence, counts);
    for (Suit suit : Suit.values()) {
      if (counts.get(suit) >= 2) {
        evidence.get(suit).add(Evidence.REPEATED_LEAD);
      }
      suits.put(suit, new Assessment(evidence.get(suit), counts.get(suit),
          cards.isVoid(view.context().declarer(), suit), view.contract().trump().matches(suit)));
    }
  }

  public Assessment assess(Suit suit) {
    return suits.get(suit);
  }

  private static void observe(View view, TrickNumber number, List<PublicPlay> plays,
      Map<Suit, EnumSet<Evidence>> evidence, Map<Suit, Integer> counts) {
    if (plays.isEmpty()) {
      return;
    }
    var first = plays.get(0);
    if (!first.seat().equals(view.context().declarer())
        || !(first.action() instanceof StandardPlay lead) || lead.call()
        || lead.card().equals(Rules.mighty(view.contract().trump()))) {
      return;
    }
    Suit suit = lead.card().suit();
    counts.merge(suit, 1, Integer::sum);
    if (!number.isFirst()) {
      evidence.get(suit).add(Evidence.LATER_LEAD);
    }
    if (number.isFirst() && DeclarerOpeningSignal.sidePointLead(view, first).isPresent()) {
      evidence.get(suit).add(Evidence.OPENING_POINT_SIGNAL);
    }
    int highest = Rules.mighty(view.contract().trump()).suit() == suit
        ? Rank.KING.value() : Rank.ACE.value();
    if (number.isFirst() && lead.card().points() > 0 && lead.card().rank().value() < highest
        && plays.stream().anyMatch(play -> !play.seat().equals(view.context().declarer())
        && play.action().card().equals(Rules.mighty(view.contract().trump())))) {
      evidence.get(suit).add(Evidence.MIGHTY_SUPPORTED_OPENING);
    }
  }
}
