package mighty.ai.play.declarer;

import java.util.*;
import mighty.ai.bidding.BiddingHand;
import mighty.ai.planning.*;
import mighty.ai.play.PlaySupport;
import mighty.ai.play.SpecialCardPlan;
import mighty.game.action.*;
import mighty.game.bidding.Bid;
import mighty.game.friend.CardFriend;
import mighty.game.observation.DeclarerInfo;
import mighty.game.rule.Rules;
import mighty.game.trick.PublicTrick;
import mighty.model.*;

/**
 * 여러 문양의 탑을 먼저 사용하는 노기루 운영의 선·따라내기를 담당합니다.
 */
final class SpreadNoTrumpPlay {

  private SpreadNoTrumpPlay() {
  }

  static Optional<mighty.ai.play.PlayDecision> cashSideTopsBeforeFriend(DeclarerPosition position) {
    var view = position.view();
    if (!allowsSpreadTopPlay(view.contract())
        || !position.needsFriendCall() || !(view.friendSelection() instanceof CardFriend friend)
        || !(friend.card() instanceof StandardCard target)) {
      return Optional.empty();
    }
    var hand = BiddingHand.from(NoTrumpOperations.initialCards(position));
    if (!SpreadNoTrumpPlan.available(hand) || !view.hand().contains(Joker.INSTANCE)) {
      return Optional.empty();
    }
    // 프렌드에게 넘길 물패와 반환을 받을 상위패를 한 문양에 함께 남깁니다.
    var connection = position.inSuit(target.suit());
    var request = connection.stream().filter(a -> a.card().points() == 0
            && ((StandardCard) a.card()).rank().value() < target.rank().value())
        .min(Comparator.comparingInt(a -> ((StandardCard) a.card()).rank().value()));
    if (request.isEmpty() || connection.stream().noneMatch(a -> position.facts().isSuitTop(a.card())
        && ((StandardCard) a.card()).rank().value() > target.rank().value())) {
      return Optional.empty();
    }
    // 연속 탑이 긴 사이드부터 소진합니다. 공개된 카드로 새로 탑이 된 점수 카드도 포함합니다.
    var top = position.ordinary().stream().filter(a -> a.card().points() > 0
            && ((StandardCard) a.card()).suit() != target.suit() && position.facts()
            .isSuitTop(a.card()))
        .max(Comparator.<Action>comparingInt(a -> hand.ordinaryTopRunLength(
                ((StandardCard) a.card()).suit(), Trump.NO_TRUMP))
            .thenComparingInt(a -> ((StandardCard) a.card()).rank().value()));
    return Optional.of(top.map(a -> new mighty.ai.play.PlayDecision(a,
            "회수할 문양과 조커를 남기고 다른 문양의 탑부터 돌립니다."))
        .orElseGet(() -> new mighty.ai.play.PlayDecision(request.get(),
            "사이드 탑을 소진해 남겨 둔 물패로 프렌드를 찾습니다.")));
  }

  static Optional<mighty.ai.play.PlayDecision> followAfterSideTops(DeclarerPosition position) {
    var view = position.view();
    var facts = position.facts();
    if (view.contract().trump() != Trump.NO_TRUMP || view.contract().points() != 15
        || !(view.context().friendStatus() instanceof mighty.game.friend.RevealedFriend)
        || !(view.friendSelection() instanceof CardFriend friend)
        || !(friend.card() instanceof StandardCard target) || !view.hand().contains(Joker.INSTANCE)
        || !SpreadNoTrumpPlan.available(
        BiddingHand.from(NoTrumpOperations.initialCards(position)))) {
      return Optional.empty();
    }
    // 지정 문양의 회수 카드와 실제 탑은 물패로 세지 않습니다.
    var water = view.hand().stream().filter(c -> c instanceof StandardCard card
        && card.suit() != target.suit() && card.points() == 0 && !facts.isSuitTop(card)).toList();
    var shed = position.ordinary().stream().filter(a -> water.contains(a.card()))
        .min(Comparator.comparingInt(a -> ((StandardCard) a.card()).rank().value()));
    var winner = facts.currentWinner().orElseThrow();
    if (facts.allyWinning() && facts.isSecured(winner)) {
      return shed.map(a -> new mighty.ai.play.PlayDecision(a, "확보된 프렌드 승리에 사이드 물패를 소진합니다."));
    }
    if (position.ordinary().stream().anyMatch(a -> facts.winningActions().contains(a)
        && facts.isSecured(new mighty.game.trick.PublicPlay(view.self(), a)))) {
      return Optional.empty();
    }
    // 남은 물패에도 실점 여유를 남기며, 뒷자리가 한 점씩 더 넣을 수 있다고 봅니다.
    int risk = facts.trickPoints() + facts.remainingSeats().size();
    if (shed.isPresent() && risk <= new DeclarerPlan(position).lossAllowance() / water.size()) {
      return shed.map(a -> new mighty.ai.play.PlayDecision(a, "실점 여유 안에서 물패를 내고 조커 회수를 남깁니다."));
    }
    return facts.winningActions().stream().filter(position.legal()::contains)
        .filter(a -> a.card() instanceof Joker
            && facts.isSecured(new mighty.game.trick.PublicPlay(view.self(), a)))
        .findFirst().map(a -> new mighty.ai.play.PlayDecision(a, "조커로 선을 회수해 남은 탑을 돌립니다."));
  }

  private static boolean allowsSpreadTopPlay(Bid contract) {
    return contract.trump() == Trump.NO_TRUMP && contract.points() >= 15 && contract.points() <= 17;
  }

}
