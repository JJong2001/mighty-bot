package mighty.ai.play;

import java.util.*;
import mighty.game.Role;
import mighty.game.action.*;
import mighty.game.friend.CardFriend;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.model.*;

/**
 * 공개된 주력 신호와 지원 요청을 읽습니다. 주공의 비공개 패는 사용하지 않습니다.
 */
public final class FriendSupportIntent {

  private FriendSupportIntent() {
  }

  public static Optional<Suit> disclosedPrimary(View view) {
    if (view.contract().trump() != Trump.NO_TRUMP) {
      return mighty.game.bidding.PublicContractHistory.suitOf(view.contract().trump());
    }
    var declared = mighty.game.bidding.PublicContractHistory.latestSuit(
        view.context().contractHistory());
    if (declared.isPresent()) {
      return declared;
    }
    Map<Suit, Long> shown = new EnumMap<>(Suit.class);
    view.context().history().stream().filter(t -> t.leader().equals(view.context().declarer()))
        .filter(t -> t.cards().get(0).action() instanceof StandardPlay p && !p.call())
        .forEach(t -> shown.merge(((StandardCard) t.cards().get(0).action().card()).suit(), 1L,
            Long::sum));
    return shown.entrySet().stream().filter(e -> e.getValue() >= 2)
        .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey);
  }

  /**
   * 특수카드 지원 또는 초구 이후의 낮은 요청을 실제로 이겨 준 문양입니다.
   */
  public static Optional<Suit> supportedSuit(View view) {
    Optional<Suit> result = Optional.empty();
    for (var trick : view.context().history()) {
      var lead = trick.cards().get(0);
      if (!lead.seat().equals(view.context().declarer()) || !trick.winner().equals(view.self())) {
        continue;
      }
      boolean special = trick.cards().stream().anyMatch(p -> p.seat().equals(view.self())
          && (p.action().card() instanceof Joker || p.action().card()
          .equals(Rules.mighty(view.contract().trump()))));
      boolean ordinaryRequest = !trick.number().isFirst()
          && view.friendSelection() instanceof CardFriend friend
          && (friend.card() instanceof Joker || friend.card()
          .equals(Rules.mighty(view.contract().trump())))
          && lead.action() instanceof StandardPlay play && !play.call()
          && play.card().points() == 0;
      if (special || ordinaryRequest) {
        result = Optional.of(
            Rules.conditionsFor(view.contract().trump(), trick.number(), lead.action()).leadSuit());
      }
    }
    return result;
  }

  /**
   * 공개 주력과 지원 목적을 먼저 보고, 공약에 문양이 없으면 실제 지원 문양을 반환합니다.
   */
  public static Optional<Suit> returnSuit(View view) {
    var recovery = jokerRecoverySuit(view);
    if (recovery.isPresent()) {
      return recovery;
    }
    var side = sideRequest(view);
    if (side.isPresent()) {
      return side;
    }
    var primary = disclosedPrimary(view);
    if (primary.isPresent()) {
      return primary;
    }
    var supported = supportedSuit(view);
    if (supported.isPresent()) {
      return supported;
    }
    // 노기루의 일반 카드 지정은 초구의 우연한 승리 문양보다 앞선 반환 근거입니다.
    if (view.contract().trump() == Trump.NO_TRUMP
        && view.friendSelection() instanceof CardFriend friend
        && friend.card() instanceof StandardCard card && !card.equals(
        Rules.mighty(Trump.NO_TRUMP))) {
      return Optional.of(card.suit());
    }
    return firstTrickReturnSuit(view);
  }

  /**
   * 노기루 조커프에서 주공이 자기 주력으로 외부 마이티를 소모시킨 공개 경로입니다.
   */
  public static boolean mightyRemovalRoute(View view) {
    return mightyRemovalTrick(view).isPresent();
  }

  private static Optional<mighty.game.trick.PublicTrick> mightyRemovalTrick(View view) {
    if (view.contract().trump() != Trump.NO_TRUMP
        || !(view.friendSelection() instanceof CardFriend friend)
        || !(friend.card() instanceof Joker)) {
      return Optional.empty();
    }
    var mighty = Rules.mighty(view.contract().trump());
    return view.context().history().stream().filter(trick ->
        trick.leader().equals(view.context().declarer())
            && Rules.conditionsFor(view.contract().trump(), trick.number(),
                trick.cards().get(0).action())
            .leadSuit() == mighty.suit()
            && trick.cards().stream()
            .anyMatch(play -> !play.seat().equals(view.context().declarer())
                && play.action().card().equals(mighty))).findFirst();
  }

  /**
   * 조커 승리에 주공이 자유롭게 버린 낮은 문양을 회수 요청으로 읽습니다. 강제 따라내기는 제외합니다.
   */
  private static Optional<Suit> jokerRecoverySuit(View view) {
    var removal = mightyRemovalTrick(view);
    if (removal.isEmpty()) {
      return Optional.empty();
    }
    Optional<Suit> recovery = Optional.empty();
    for (var trick : view.context().history()) {
      if (trick.number().value() <= removal.get().number().value()) {
        continue;
      }
      if (trick.winner().equals(view.context().declarer())) {
        recovery = Optional.empty();
      }
      boolean supported =
          trick.winner().equals(view.self()) && trick.cards().stream().anyMatch(play ->
              play.seat().equals(view.self()) && play.action().card() instanceof Joker);
      if (!supported) {
        continue;
      }
      var leadSuit = Rules.conditionsFor(view.contract().trump(), trick.number(),
          trick.cards().get(0).action()).leadSuit();
      var discarded = trick.cards().stream()
          .filter(play -> play.seat().equals(view.context().declarer()))
          .map(play -> play.action().card())
          .filter(card -> card instanceof StandardCard standard && standard.points() == 0
              && standard.suit() != leadSuit)
          .map(card -> ((StandardCard) card).suit()).findFirst();
      if (discarded.isPresent()) {
        recovery = discarded;
      }
    }
    return recovery;
  }

  /**
   * 노기루 초구프는 공개 기루를 우선하고, 공개 기루가 없으면 초구 문양을 반환합니다.
   */
  public static Optional<Suit> firstTrickReturnSuit(View view) {
    if (view.contract().trump() != Trump.NO_TRUMP
        || view.friendSelection() != mighty.game.friend.SpecialFriend.FIRST_TRICK) {
      return Optional.empty();
    }
    return view.context().history().stream()
        .filter(trick -> trick.number().isFirst() && trick.winner().equals(view.self())
            && trick.leader().equals(view.context().declarer()))
        .findFirst()
        .map(trick -> mighty.game.bidding.PublicContractHistory.latestSuit(
                view.context().contractHistory())
            .orElseGet(() -> Rules.conditionsFor(Trump.NO_TRUMP, trick.number(),
                trick.cards().get(0).action()).leadSuit()));
  }

  public static boolean strongPrimaryShown(View view) {
    var primary = disclosedPrimary(view);
    if (primary.isEmpty()) {
      return false;
    }
    return view.context().history().stream().anyMatch(t ->
        t.leader().equals(view.context().declarer()) && t.winner().equals(view.context().declarer())
            && t.cards().get(0).action() instanceof StandardPlay lead && !lead.call()
            && lead.card().suit() == primary.get() && lead.card().points() > 0
            && !lead.card().equals(Rules.mighty(view.contract().trump())));
  }

  /**
   * 주력이 충분함을 보인 뒤 사이드에서 찾았으면 조커를 그 사이드에 사용합니다.
   */
  public static Optional<Suit> sideRequest(View view) {
    var primary = disclosedPrimary(view);
    if (primary.isEmpty() || !strongPrimaryShown(view)) {
      return Optional.empty();
    }
    List<mighty.game.trick.PublicPlay> leads = new ArrayList<>();
    view.context().history().forEach(t -> leads.add(t.cards().get(0)));
    if (!view.trick().isEmpty()) {
      leads.add(view.trick().get(0));
    }
    return leads.stream().filter(p -> p.seat().equals(view.context().declarer()))
        .filter(p -> p.action() instanceof StandardPlay a && !a.call()
            && a.card().suit() != primary.get() && a.card().points() == 0
            && !a.card().equals(Rules.mighty(view.contract().trump())))
        .map(p -> ((StandardCard) p.action().card()).suit()).reduce((first, last) -> last);
  }

  /**
   * 주공이 공개한 주력 순회를 낮은 숫자라는 이유만으로 새 호출로 오해하지 않습니다.
   */
  public static boolean primaryRunContinuing(View view, PlayFacts facts) {
    if (view.role() != Role.FRIEND || view.contract().trump() != Trump.NO_TRUMP
        || view.trick().isEmpty() || !facts.allyWinning()) {
      return false;
    }
    var lead = view.trick().get(0);
    if (!lead.seat().equals(view.context().declarer()) || !(lead.action() instanceof StandardPlay a)
        || a.call()) {
      return false;
    }
    // 마이티 제거 뒤의 같은 문양 순회는 새 조커 호출로 읽지 않습니다.
    if (mightyRemovalRoute(view) && a.card().suit() == Rules.mighty(view.contract().trump())
        .suit()) {
      return true;
    }
    if (!strongPrimaryShown(view) || !disclosedPrimary(view).filter(a.card().suit()::equals)
        .isPresent()) {
      return false;
    }
    long higher = facts.cards().outsideCards().stream().filter(c -> c instanceof StandardCard card
        && card.suit() == a.card().suit() && !c.equals(Rules.mighty(view.contract().trump()))
        && card.rank().value() > a.card().rank().value()).count();
    long shown = view.context().history().stream()
        .filter(t -> t.leader().equals(view.context().declarer())
            && t.winner().equals(view.context().declarer()) && t.cards().get(0)
            .action() instanceof StandardPlay p
            && p.card().suit() == a.card().suit() && p.card().points() > 0).count();
    // 버림패는 모르므로 확정 탑카로 바꾸지 않습니다. 공개한 연속 상위 순회의 의도만 읽습니다.
    return higher == 0 || shown >= 2 && higher < shown;
  }

  /**
   * 일반 탑카로 요청을 이미 해결했다면 조커가 주공의 선을 다시 빼앗지 않습니다.
   */
  public static boolean preserveJoker(View view, PlayFacts facts) {
    if (primaryRunContinuing(view, facts)) {
      return true;
    }
    if (view.role() != Role.FRIEND || !(view.friendSelection() instanceof CardFriend friend)
        || !(friend.card() instanceof Joker) || !strongPrimaryShown(view)) {
      return false;
    }
    Optional<Suit> requested = sideRequest(view);
    if (requested.isEmpty()) {
      return false;
    }
    boolean helped = false;
    for (var trick : view.context().history()) {
      var lead = trick.cards().get(0);
      if (lead.seat().equals(view.context().declarer()) && lead.action() instanceof StandardPlay a
          && !a.call() && a.card().points() == 0 && a.card().suit() == requested.get()) {
        helped = trick.winner().equals(view.self()) && trick.cards().stream().anyMatch(p ->
            p.seat().equals(view.self()) && p.action().card() instanceof StandardCard c
                && !c.equals(Rules.mighty(view.contract().trump())));
      }
    }
    if (!helped) {
      return false;
    }
    if (!view.trick().isEmpty()) {
      var lead = view.trick().get(0);
      if (lead.seat().equals(view.context().declarer()) && lead.action()
          .card() instanceof StandardCard c
          && c.points() == 0 && c.suit() != disclosedPrimary(view).orElseThrow()
          && !facts.isSuitTop(c)) {
        return false;
      }
    }
    return true;
  }

  /**
   * 주공의 요청을 지정 카드와 자기 특수카드의 조합으로 해석합니다.
   */
  public static Optional<Action> requestedSpecial(View view, PlayFacts facts) {
    if (view.role() != Role.FRIEND || view.contract().trump() != Trump.NO_TRUMP || view.trick()
        .isEmpty()
        || !(view.friendSelection() instanceof CardFriend friend)
        || !(friend.card() instanceof Joker || friend.card()
        .equals(Rules.mighty(view.contract().trump())))) {
      return Optional.empty();
    }
    var lead = view.trick().get(0);
    var mighty = Rules.mighty(view.contract().trump());
    // 마이티를 제거한 뒤 다른 문양에서 조커에게 회수를 맡기는 주공의 운영입니다.
    if (friend.card() instanceof Joker && view.hand().contains(Joker.INSTANCE)
        && view.hand().contains(mighty) && lead.seat().equals(view.context().declarer())
        && lead.action() instanceof StandardPlay play && play.card().suit() == mighty.suit()) {
      return facts.winningActions().stream().filter(action -> action.card().equals(mighty))
          .findFirst();
    }
    if (primaryRunContinuing(view, facts)) {
      return Optional.empty();
    }
    if (friend.card() instanceof Joker && strongPrimaryShown(view)
        && PlaySupport.ordinary(facts.winningActions(), view).stream()
        .anyMatch(a -> facts.isSuitTop(a.card()))) {
      return Optional.empty();
    }
    if (!lead.seat().equals(view.context().declarer())
        || !(lead.action() instanceof StandardPlay play)
        || facts.isSecured(facts.currentWinner().orElseThrow()) || facts.isSuitTop(play.card())) {
      return Optional.empty();
    }
    return facts.winningActions().stream().filter(a -> a.card().equals(friend.card())).findFirst();
  }

}
