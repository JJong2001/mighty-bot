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
    var extra = extraWinnerBeforeReturn(view, facts, ordinary);
    if (extra.isPresent()) {
      return extra;
    }
    if (view.contract().trump() == Trump.NO_TRUMP && points >= 17 && points < 20
        && !history.isEmpty() && view.friendSelection() instanceof CardFriend selection
        && selection.card() instanceof StandardCard called) {
      var last = history.get(history.size() - 1);
      // 선입찰자가 실제 프렌드가 되어 주공의 물패를 받았을 때만 독립 승리를 이어갑니다.
      boolean handedOver = history.stream().anyMatch(trick ->
          trick.leader().equals(view.context().declarer()) && trick.winner().equals(view.self())
          && trick.cards().get(0).action().card() instanceof StandardCard lead && lead.points() == 0
          && view.context().auction().stream().anyMatch(entry -> entry.seat().equals(view.self())
              && entry.action() instanceof BidOffer offer && offer.bid().points() >= 17
              && offer.bid().trump().matches(lead.suit())
              && (called.equals(mighty.game.rule.Rules.mighty(Trump.NO_TRUMP))
              || called.suit() == lead.suit() && called.rank() == Rank.ACE)));
      if (handedOver && last.winner().equals(view.self())) {
        var tops = ordinary.stream().filter(a -> facts.isSuitTop(a.card())).toList();
        if (!tops.isEmpty()) {
          return Optional.of(new PlayDecision(PlaySupport.highest(tops),
              "주공에게 받은 선에서 남은 탑으로 추가 승리를 확보합니다."));
        }
      }
    }
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

  private static Optional<PlayDecision> extraWinnerBeforeReturn(View view, PlayFacts facts,
      List<Action> ordinary) {
    var history = view.context().history();
    if (view.contract().trump() != Trump.NO_TRUMP || view.contract().points() < 17
        || history.isEmpty() || !(view.friendSelection() instanceof CardFriend friend)) {
      return Optional.empty();
    }
    var declarer = view.context().declarer();
    var requested = PlaySupport.announcedReturnSuit(view);
    if (requested.isEmpty()) {
      return Optional.empty();
    }
    // 주공이 먼저 탑을 사용하고 지정 카드로 선을 건넨 직후의 한 구만 연장합니다.
    int handoff = history.size() - 1;
    while (handoff > 0 && history.get(handoff).leader().equals(view.self())
        && history.get(handoff).winner().equals(view.self())) {
      handoff--;
    }
    var transfer = history.get(handoff);
    if (!transfer.leader().equals(declarer) || !transfer.winner().equals(view.self())
        || transfer.cards().stream().noneMatch(p -> p.seat().equals(view.self())
            && p.action().card().equals(friend.card()))) {
      return Optional.empty();
    }
    var prepared = EnumSet.noneOf(Suit.class);
    for (int i = handoff - 1; i >= 0; i--) {
      var trick = history.get(i);
      if (!trick.leader().equals(declarer) || !trick.winner().equals(declarer)
          || !(trick.cards().get(0).action().card() instanceof StandardCard card)
          || card.equals(mighty.game.rule.Rules.mighty(Trump.NO_TRUMP))) {
        break;
      }
      prepared.add(card.suit());
    }
    // 초구부터 물패로 마이티를 찾았다면 이미 가진 독립 탑으로도 같은 지원이 가능합니다.
    boolean directMighty = friend.card().equals(mighty.game.rule.Rules.mighty(Trump.NO_TRUMP))
        && transfer.cards().get(0).action().card().points() == 0;
    if ((prepared.isEmpty() || (prepared.size() == 1 && prepared.contains(requested.get())))
        && !directMighty) {
      return Optional.empty();
    }
    if (handoff == history.size() - 1) {
      var tops = ordinary.stream().filter(a -> {
        var suit = ((StandardCard) a.card()).suit();
        return suit != requested.get() && facts.isSuitTop(a.card())
            && (!facts.cards().outsideCards().contains(Joker.INSTANCE)
                || view.context().playOrder().seats().stream().filter(s -> !facts.teams().isAlly(s))
                    .noneMatch(s -> facts.cards().isVoid(s, suit)));
      }).toList();
      if (!tops.isEmpty()) {
        var preferred = tops.stream().filter(a -> {
          var suit = ((StandardCard) a.card()).suit();
          return prepared.contains(suit) || facts.cards().isVoid(declarer, suit);
        }).toList();
        return Optional.of(new PlayDecision(PlaySupport.highest(preferred.isEmpty() ? tops : preferred),
            "승격된 탑이나 독립 탑으로 한 구 더 받아 주공이 약한 패를 버릴 기회를 줍니다."));
      }
    }
    return returning(ordinary, requested.get());
  }

  private static Optional<PlayDecision> returning(List<Action> legal, Suit suit) {
    var cards = legal.stream().filter(a -> ((StandardCard) a.card()).suit() == suit).toList();
    return cards.isEmpty() ? Optional.empty() : Optional.of(new PlayDecision(
        PlaySupport.highest(cards), "주공이 공개적으로 요청한 문양을 반환합니다."));
  }
}
