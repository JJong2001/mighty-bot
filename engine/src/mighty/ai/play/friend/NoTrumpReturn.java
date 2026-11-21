package mighty.ai.play.friend;

import java.util.*;
import mighty.ai.play.PlayDecision;
import mighty.ai.play.PlayFacts;
import mighty.ai.play.PlaySupport;
import mighty.game.action.Action;
import mighty.game.bidding.*;
import mighty.game.friend.CardFriend;
import mighty.game.observation.View;
import mighty.model.*;

/** 프렌드에게 보인 공약과 트릭만으로 주공의 반환 요청을 읽습니다. */
final class NoTrumpReturn {

  private NoTrumpReturn() {
  }

  static Optional<PlayDecision> choose(View view, PlayFacts facts, List<Action> ordinary) {
    var history = view.context().history();
    int points = view.contract().points();
    if (view.contract().trump() != Trump.NO_TRUMP || history.isEmpty()
        || (points != 16 && points != 17 && points != 20)) {
      return Optional.empty();
    }
    var declarer = view.context().declarer();
    var last = history.get(history.size() - 1);
    var requested = new LinkedHashSet<Suit>();
    var first = history.get(0).cards().get(0).action().card();
    if (points == 20 && first instanceof StandardCard lead && lead.points() == 0
        && view.friendSelection() instanceof CardFriend friend
        && friend.card() instanceof StandardCard called && called.suit() == lead.suit()) {
      // 두 문양을 모두 알렸다면 초구 문양을 먼저, 그 문양이 없으면 다른 선언을 반환합니다.
      var full = view.context().contractHistory().stream().filter(bid -> bid.points() == 20).toList();
      var declarations = PublicContractHistory.suitsMostRecentFirst(full);
      if (declarations.contains(lead.suit())) {
        requested.add(lead.suit());
      }
      requested.addAll(declarations);
    }
    if (points == 17) {
      view.context().contractHistory().stream().filter(bid -> bid.points() == 16)
          .flatMap(bid -> PublicContractHistory.suitOf(bid.trump()).stream())
          .forEach(requested::add);
    }
    for (int i = view.context().auction().size() - 1; i >= 0; i--) {
      var entry = view.context().auction().get(i);
      if (entry.seat().equals(declarer) && entry.action() instanceof BidOffer offer
          && offer.bid().points() == 16) {
        PublicContractHistory.suitOf(offer.bid().trump()).ifPresent(requested::add);
        break;
      }
    }
    if (view.friendSelection() instanceof CardFriend friend
        && friend.card() instanceof StandardCard called && called.rank() == Rank.ACE
        && called.suit() != Suit.SPADE && first instanceof StandardCard probe
        && probe.suit() == called.suit() && probe.points() == 0
        && view.context().auction().stream().anyMatch(entry -> !entry.seat().equals(declarer)
            && entry.action() instanceof BidOffer offer && offer.bid().points() >= 16
            && offer.bid().trump().matches(called.suit()))) {
      if (last.leader().equals(view.self())) {
        var lead = last.cards().get(0).action().card();
        for (var play : last.cards()) {
          if (play.seat().equals(declarer) && play.action().card() instanceof StandardCard signal
              && lead instanceof StandardCard led && signal.suit() != led.suit()
              && signal.points() == 0) {
            var answer = returning(ordinary, signal.suit());
            if (answer.isPresent()) {
              return answer;
            }
          }
        }
      }
      var winners = ordinary.stream().filter(a -> ((StandardCard) a.card()).suit() != Suit.SPADE)
          .filter(a -> facts.isSuitTop(a.card())).toList();
      if (!winners.isEmpty()) {
        return Optional.of(new PlayDecision(PlaySupport.highest(winners),
            "추가 탑으로 한 구를 도와 주공이 반환 신호를 낼 기회를 줍니다."));
      }
      if (requested.isEmpty()) {
        var suits = ordinary.stream().map(a -> ((StandardCard) a.card()).suit()).distinct()
            .filter(s -> s != called.suit() && s != Suit.SPADE).sorted().toList();
        if (!suits.isEmpty()) {
          // 자기 패와 공개 차례로 재현 가능한 추첨을 하며 문양별 카드 장수로 가중하지 않습니다.
          var seed = view.hand().stream().map(Card::toCode).sorted().toList().hashCode();
          requested.add(suits.get(new Random(31L * seed + history.size()).nextInt(suits.size())));
        }
      }
    }
    for (var suit : requested) {
      var answer = returning(ordinary, suit);
      if (answer.isPresent()) {
        return answer;
      }
    }
    return Optional.empty();
  }

  private static Optional<PlayDecision> returning(List<Action> legal, Suit suit) {
    var cards = legal.stream().filter(a -> ((StandardCard) a.card()).suit() == suit).toList();
    return cards.isEmpty() ? Optional.empty() : Optional.of(new PlayDecision(
        PlaySupport.highest(cards), "주공이 공개적으로 요청한 문양을 반환합니다."));
  }
}
