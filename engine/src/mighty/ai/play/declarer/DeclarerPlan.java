package mighty.ai.play.declarer;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import mighty.ai.play.PlayDecision;
import mighty.ai.play.PlaySupport;
import mighty.ai.play.SuitDevelopment;
import mighty.ai.play.SpecialCardPlan;
import mighty.game.friend.NoFriend;
import mighty.game.trick.PublicPlay;
import mighty.game.action.Action;
import mighty.game.action.StandardPlay;
import mighty.game.rule.Rules;
import mighty.model.Card;
import mighty.model.Joker;
import mighty.model.StandardCard;
import mighty.model.Trump;
import mighty.model.Suit;

/**
 * 매 차례 자기 관측으로 재구성하는 부기루 육성·손실 예산·간 회수 계획입니다.
 */
final class DeclarerPlan {

  private final DeclarerPosition position;

  DeclarerPlan(DeclarerPosition position) {
    this.position = position;
  }

  private List<StandardCard> heldTrumps() {
    return position.view().hand().stream().filter(card -> card instanceof StandardCard standard
            && position.view().contract().trump().matches(standard.suit()))
        .map(StandardCard.class::cast).toList();
  }

  List<SuitDevelopment> developments() {
    return Arrays.stream(Suit.values())
        .filter(suit -> !position.view().contract().trump().matches(suit))
        .map(suit -> SuitDevelopment.analyze(position.view(), position.facts(), suit))
        .filter(plan -> plan.canDevelop() || SideOpeningPlan.continuing(position, plan))
        .sorted(Comparator.comparingInt((SuitDevelopment plan) -> plan.held().size()).reversed()
            .thenComparingInt(plan -> plan.blockers().size())).toList();
  }

  /**
   * 낮은 카드도 부기루의 상위를 빼는 데 쓰이면 물패로 버리지 않습니다.
   */
  List<StandardCard> water() {
    return position.view().hand().stream().filter(card -> card instanceof StandardCard standard
            && !position.view().contract().trump().matches(standard.suit())
            && !card.equals(Rules.mighty(position.view().contract().trump()))
            && !(position.view().contract().trump() == Trump.NO_TRUMP
            && standard.suit() == position.primarySuit())
            && !position.facts().isSuitTop(card)
            && !SuitDevelopment.analyze(position.view(), position.facts(), standard.suit()).canDevelop()
            && !SideOpeningPlan.continuing(position,
            SuitDevelopment.analyze(position.view(), position.facts(), standard.suit())))
        .map(StandardCard.class::cast).toList();
  }

  private boolean outsideJoker() {
    return position.view().trickNumber().value() < 10
        && position.facts().cards().outsideCards().contains(Joker.INSTANCE);
  }

  private boolean outsideMighty() {
    return position.facts().cards().outsideCards()
        .contains(Rules.mighty(position.view().contract().trump()));
  }

  /**
   * 미공개 프렌드의 과거 득점은 임의로 아군 점수로 바꾸지 않아 손실 여유를 보수적으로 계산합니다.
   */
  int lossAllowance() {
    int possibleOppositionPoints = position.view().context().history().stream()
        .filter(trick -> !position.facts().teams().isAlly(trick.winner()))
        .mapToInt(trick -> trick.points()).sum();
    return 20 - position.view().contract().points() - possibleOppositionPoints;
  }

  boolean remainingMastersAfter(Card dropped) {
    return position.view().hand().stream().filter(card -> !card.equals(dropped)).allMatch(card ->
        card instanceof StandardCard standard
            && position.facts().isMasterLead(new StandardPlay(standard, false)));
  }

  Optional<PlayDecision> lead() {
    if (position.view().contract().trump() == Trump.NO_TRUMP
        || position.enemiesMayHaveTrump() || heldTrumps().isEmpty()) {
      return Optional.empty();
    }
    // 마이티 소모·외부 기루 없음·자기 손 전부 기루이면 외부 조커만 유일한 위협입니다.
    boolean onlyJokerThreat = outsideJoker() && !outsideMighty()
        && heldTrumps().size() == position.view().hand().size();
    if (onlyJokerThreat) {
      return jokerPressure();
    }
    if (!developments().isEmpty()) {
      SuitDevelopment development = developments().get(0);
      return Optional.of(new PlayDecision(new StandardPlay(development.probingCard(), false),
          "회수용 기루를 남기고 부기루 " + development.suit() + "를 직접 돌려 외부 상위 "
              + development.blockers().size() + "장을 소모시킬 경로를 만듭니다."));
    }
    List<StandardCard> water = water();
    if (water.isEmpty()) {
      return Optional.empty();
    }
    // 유일한 물패이고 나머지가 공개 카운팅상 모두 승리하면 마지막으로 미룹니다.
    if (water.size() == 1 && !outsideJoker() && !outsideMighty()
        && remainingMastersAfter(water.get(0)) && lossAllowance() > 0) {
      List<Action> masters = position.ordinary().stream()
          .filter(action -> !action.card().equals(water.get(0)))
          .filter(position.facts()::isMasterLead).toList();
      if (!masters.isEmpty()) {
        String reason = position.view().context().friendStatus() instanceof NoFriend
            ? "유일한 물패는 마지막에 남기고 나머지 확정 승리 카드를 먼저 사용합니다."
            : "유일한 물패는 마지막에 남기고 승리 순회로 프렌드의 점수 처리 기회를 만듭니다.";
        return Optional.of(new PlayDecision(PlaySupport.highest(masters), reason));
      }
    }
    List<Action> shedding = position.sides().stream()
        .filter(action -> water.contains(action.card())).toList();
    if (!shedding.isEmpty()) {
      return Optional.of(new PlayDecision(position.lowSide(shedding),
          "물패·외부 특수카드의 복수 손실에 대비해 기루를 남기고 일찍 작게 맞을 기회를 만듭니다."));
    }
    return Optional.empty();
  }

  private Optional<PlayDecision> jokerPressure() {
    List<Action> points = position.trumps().stream().filter(action -> action.card().points() > 0)
        .toList();
    List<Action> low = position.trumps().stream().filter(action -> action.card().points() == 0)
        .toList();
    boolean alreadyInvited = position.view().context().history().stream().anyMatch(trick ->
        trick.leader().equals(position.view().self()) && trick.winner()
            .equals(position.view().self())
            && trick.cards().get(0).action().card() instanceof StandardCard lead
            && position.view().contract().trump().matches(lead.suit()) && lead.points() > 0
            && trick.cards().stream().skip(1)
            .noneMatch(play -> play.action().card() instanceof StandardCard card
                && position.view().contract().trump().matches(card.suit())));
    if (!points.isEmpty() && !alreadyInvited && position.view().trickNumber().value() < 9) {
      return Optional.of(new PlayDecision(PlaySupport.highest(points),
          "유일한 외부 위협인 조커를 점수 기루 한 장으로 일찍 유도합니다. 아끼면 이 순회도 이깁니다."));
    }
    if (!low.isEmpty()) {
      return Optional.of(new PlayDecision(PlaySupport.cheapest(low, position.view()),
          "조커를 아껴도 다른 기루로 이기며 마지막 점수 기루는 조커가 무효인 10구에 남깁니다."));
    }
    return points.isEmpty() ? Optional.empty()
        : Optional.of(new PlayDecision(PlaySupport.highest(points),
            "비점수 기루가 없어 점수 기루로 진행하며 외부 조커의 사용을 유도합니다."));
  }

  Optional<PlayDecision> follow() {
    var view = position.view();
    var facts = position.facts();
    if (view.contract().trump() == Trump.NO_TRUMP || facts.allyWinning()) {
      return Optional.empty();
    }
    List<Action> ruffs = PlaySupport.ordinary(facts.winningActions(), view).stream()
        .filter(action -> view.contract().trump().matches(((StandardCard) action.card()).suit()))
        .toList();
    if (ruffs.isEmpty() || view.contract().trump().matches(facts.conditions().leadSuit())) {
      return Optional.empty();
    }
    var special = SpecialCardPlan.mightyBeforeJoker(facts.winningActions(), view);
    if (special.isPresent()) {
      return Optional.of(new PlayDecision(special.get(), "마이티로 선을 회수하고 조커의 문양 선택권을 남깁니다."));
    }
    if (!position.enemiesMayHaveTrump()) {
      var joker = facts.winningActions().stream().filter(a -> a.card() instanceof Joker)
          .findFirst();
      if (joker.isPresent()) {
        return Optional.of(new PlayDecision(joker.get(),
            "외부 야당 기루가 소진되어 조커로 사이드를 잡고 활성 사용 기회를 살립니다."));
      }
    }
    Action chosen = mighty.ai.play.RuffPlan.choose(view, facts, ruffs).orElseThrow();
    return Optional.of(new PlayDecision(chosen, developments().isEmpty()
        ? "작게 흘릴 조건이 아니므로 간쳐 점수와 회수 경로를 확보합니다."
        : "부기루 전개 카드를 버리지 않고 간으로 선을 확보해 상위를 빼는 계획을 이어갑니다."));
  }

}
