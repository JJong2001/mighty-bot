package mighty.ai.play;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import mighty.game.Role;
import mighty.game.Seat;
import mighty.game.friend.NoFriend;
import mighty.game.action.Action;
import mighty.game.action.StandardPlay;
import mighty.game.observation.View;
import mighty.model.Rank;
import mighty.model.StandardCard;
import mighty.model.Suit;

/**
 * 공개 카운팅으로 남은 기루와 선을 되찾을 수단을 함께 평가합니다.
 */
public final class TrumpControl {

  private final View view;
  private final PlayFacts facts;

  public TrumpControl(View view, PlayFacts facts) {
    this.view = view;
    this.facts = facts;
  }

  public List<StandardCard> own() {
    return facts.cards().trumps().own();
  }

  public List<StandardCard> outside() {
    return facts.cards().trumps().outside();
  }

  /**
   * 주공 외의 후보 자리가 모두 공개 무기루일 때만 마지막 기루 위치를 좁힙니다.
   */
  public boolean canStripDeclarer(Action action) {
    if (view.trickNumber().value() <= 3 || !(action.card() instanceof StandardCard card)
        || !view.contract().trump().matches(card.suit()) || outside().size() != 1
        || facts.cards().isVoid(view.context().declarer(), card.suit()) || !facts.isMasterLead(
        action)) {
      return false;
    }
    return view.context().playOrder().seats().stream()
        .filter(seat -> !seat.equals(view.self()) && !seat.equals(view.context().declarer()))
        .allMatch(seat -> facts.cards().isVoid(seat, card.suit()));
  }

  /**
   * 마지막 자기 기루는 사이드 전개 가치와 정리 후 선 유지가 모두 있을 때만 소모합니다.
   */
  public boolean shouldPreserveLastTrump() {
    List<StandardCard> own = own();
    List<StandardCard> outside = outside();
    if (own.size() != 1 || outside.isEmpty()) {
      return false;
    }
    StandardCard last = own.get(0);
    if (outside.size() == 1 && friendMayHoldLastTrump(last.suit())) {
      return true;
    }
    if (outside.stream().anyMatch(card -> card.rank().value() > last.rank().value())
        || !facts.isMasterLead(new StandardPlay(last, false))) {
      return true;
    }
    return !sideHasPressure();
  }

  /**
   * 프렌드의 기루일 수 있으면 양쪽 회수 수단을 한 구에 소모하지 않습니다.
   */
  private boolean friendMayHoldLastTrump(Suit suit) {
    if (view.role() != Role.DECLARER || view.context().friendStatus() instanceof NoFriend) {
      return false;
    }
    var friend = facts.teams().friendSeat();
    if (friend.isPresent()) {
      return !facts.cards().isVoid(friend.get(), suit);
    }
    return view.context().playOrder().seats().stream()
        .filter(seat -> !seat.equals(view.self()))
        .anyMatch(seat -> !facts.cards().isVoid(seat, suit));
  }

  private boolean sideHasPressure() {
    List<StandardCard> sides = view.hand().stream()
        .filter(card -> card instanceof StandardCard standard
            && !view.contract().trump().matches(standard.suit())).map(StandardCard.class::cast)
        .toList();
    if (sides.isEmpty()) {
      return false;
    }
    return sides.stream().allMatch(facts::isSuitTop)
        || Arrays.stream(Suit.values()).filter(suit -> !view.contract().trump().matches(suit))
        .map(suit -> SuitDevelopment.analyze(view, facts, suit)).anyMatch(plan ->
            !plan.held().isEmpty() && plan.blockers().isEmpty()
                && ((plan.held().get(0).rank() == Rank.ACE
                && plan.held().stream().anyMatch(card -> card.rank() == Rank.KING))
                || (plan.held().get(0).rank() != Rank.ACE
                && plan.held().get(0).rank().value() >= Rank.TEN.value())))
        || Arrays.stream(Suit.values()).filter(suit -> !view.contract().trump().matches(suit))
        .map(suit -> SuitDevelopment.analyze(view, facts, suit))
        .anyMatch(SuitDevelopment::canDevelop);
  }

  public Optional<Action> aceProbe(List<Action> legal) {
    return legal.stream().filter(action -> action instanceof StandardPlay play && !play.call()
            && view.contract().trump().matches(play.card().suit()) && play.card().rank() == Rank.ACE)
        .findFirst();
  }

}
