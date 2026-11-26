package mighty.ai.play;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import mighty.game.Role;
import mighty.game.Seat;
import mighty.game.friend.NoFriend;
import mighty.game.friend.CardFriend;
import mighty.game.rule.Rules;
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
   * 흩어진 야당 기루를 더 빼도 간칠 장수가 남는지, 프렌드의 마이티 지원이 있는지 봅니다.
   */
  public Optional<Action> pressureLead(List<Action> legal) {
    if (own().isEmpty() || outside().isEmpty() || shouldPreserveLastTrump()) {
      return Optional.empty();
    }
    var suit = own().get(0).suit();
    long enemySeats = view.context().playOrder().seats().stream()
        .filter(seat -> !facts.teams().isAlly(seat))
        .filter(seat -> !facts.cards().isVoid(seat, suit))
        .filter(seat -> !facts.cards().trumps().likelyShort(seat)).count();
    var mightyCard = Rules.mighty(view.contract().trump());
    boolean friendSupport = view.friendSelection() instanceof CardFriend friend
        && friend.card().equals(mightyCard) && facts.cards().outsideCards().contains(mightyCard);
    // 한 번 확인하고도 두 장은 남겨야 선 회수와 다음 선택을 함께 유지할 수 있습니다.
    // 외부 상위의 장수는 동시에 따라 나올 수 있으므로 순차 패배 횟수로 세지 않습니다.
    long possibleEnemyHolders = Math.min(enemySeats, outside().size());
    if (!friendSupport && (possibleEnemyHolders < 2 || own().size() <= 2)) {
      return Optional.empty();
    }
    var candidates = PlaySupport.ordinary(legal, view).stream()
        .filter(action -> view.contract().trump().matches(((StandardCard) action.card()).suit()))
        .toList();
    var water = candidates.stream().filter(action -> action.card().points() == 0).toList();
    return candidates.isEmpty() ? Optional.empty() : Optional.of(water.isEmpty()
        ? PlaySupport.cheapest(candidates, view) : PlaySupport.highest(water));
  }

  /**
   * 공개 카운팅상 한 장의 상위를 물패로 빼고도 점수와 회수 기루를 남깁니다.
   */
  public Optional<Action> singleBlockerProbe(List<Action> legal) {
    if (own().size() < 3 || own().stream().noneMatch(c -> c.points() > 0) || outside().size() != 1
        || facts.cards().outsideCards().contains(Rules.mighty(view.contract().trump()))
        || facts.cards().outsideCards().contains(mighty.model.Joker.INSTANCE)
        || !facts.cards().trumps().enemiesMayHold(facts)) {
      return Optional.empty();
    }
    int blocker = outside().get(0).rank().value();
    if (own().stream().anyMatch(c -> c.rank().value() > blocker)) {
      return Optional.empty();
    }
    return PlaySupport.ordinary(legal, view).stream()
        .filter(a -> view.contract().trump().matches(((StandardCard) a.card()).suit())
            && a.card().points() == 0)
        .max(java.util.Comparator.comparingInt(a -> ((StandardCard) a.card()).rank().value()));
  }

  /**
   * 승리하는 조커 한 바퀴와 자력 탑카로 최악의 단독 분포까지 소모하고 낮은 기루를 살릴 수 있습니다.
   */
  public boolean canEstablishTailAfterSweep() {
    if (outside().isEmpty() || own().isEmpty()) {
      return false;
    }
    int outsideTop = outside().stream().mapToInt(card -> card.rank().value()).max().orElseThrow();
    long tops = own().stream().filter(card -> card.rank().value() > outsideTop).count();
    return own().size() > tops && outside().size() <= tops + 1;
  }

  /**
   * 주공 외의 자리가 모두 공개 무기루이고 연속 상위로 전부 이길 때만 탈기시킵니다.
   */
  public boolean canStripDeclarer(Action action) {
    if (!(action.card() instanceof StandardCard card)
        || !view.contract().trump().matches(card.suit()) || outside().isEmpty()
        || facts.cards().isVoid(view.context().declarer(), card.suit()) || !facts.isMasterLead(
        action)) {
      return false;
    }
    int outsideTop = outside().stream().mapToInt(c -> c.rank().value()).max().orElseThrow();
    long winningDraws = own().stream().filter(c -> c.rank().value() > outsideTop).count();
    if (winningDraws < outside().size()) {
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

  /**
   * 주공이 점수 기루로 요청한 상위를 이미 확인했다면 추가 특수카드 지원은 남깁니다.
   */
  public boolean needsSpecialSweep() {
    if (!facts.cards().trumps().enemiesMayHold(facts)) {
      return false;
    }
    var history = view.context().history();
    for (int i = history.size() - 1; i >= 0; i--) {
      var lead = history.get(i).cards().get(0);
      if (lead.seat().equals(view.context().declarer()) && lead.action()
          .card() instanceof StandardCard card
          && view.contract().trump().matches(card.suit()) && card.points() > 0) {
        return outside().stream().anyMatch(c -> c.rank().value() > card.rank().value());
      }
    }
    return true;
  }

}
