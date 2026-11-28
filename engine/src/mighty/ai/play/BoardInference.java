package mighty.ai.play;

import java.util.List;
import mighty.game.TrickNumber;
import mighty.game.friend.CardFriend;
import mighty.game.observation.View;
import mighty.game.rule.Rules;
import mighty.game.trick.PublicPlay;
import mighty.model.*;
import mighty.ai.play.PublicBehaviorInference.HoldingEvidence;
import mighty.ai.play.PublicBehaviorInference.HoldingHint;

/** 한 자리의 관측으로 만든 추론을 해당 턴의 모든 판단에서 재사용합니다. */
public final class BoardInference {

  /** 관측자가 공개 행동에서 읽는 보유 신호의 강도입니다. */
  public enum HoldingAssessment { HINT, CONFIRMED }

  static void observeLead(View view, List<PublicPlay> plays, TrickNumber number,
      List<HoldingHint> hints) {
    if (plays.isEmpty()) return;
    var lead = plays.get(0);
    if (!lead.seat().equals(view.context().declarer())
        || !(lead.action().card() instanceof StandardCard card)) return;
    if (number.isFirst()) {
      if (card.rank() == Rank.KING && view.friendSelection() instanceof CardFriend friend
          && friend.card() instanceof Joker) {
        hints.add(new HoldingHint(lead.seat(), new StandardCard(card.suit(), Rank.ACE),
            HoldingEvidence.INDEPENDENT_FRIEND_OPENING_HONOR, HoldingAssessment.CONFIRMED));
      } else {
        DeclarerOpeningSignal.retainedHonors(view, lead).forEach(higher ->
            hints.add(new HoldingHint(lead.seat(), higher,
                HoldingEvidence.INDEPENDENT_FRIEND_OPENING_HONOR, HoldingAssessment.HINT)));
      }
    }
    if (card.equals(Rules.gun(view.contract().trump()))) {
      // 초구도 보유 신호다. 마지막 카드를 제출한 자리에는 남은 조커가 없다.
      hints.add(new HoldingHint(lead.seat(), Joker.INSTANCE, HoldingEvidence.DECLARER_GUN_LEAD,
          number.value() == 10 ? HoldingAssessment.HINT : HoldingAssessment.CONFIRMED));
    }
  }

  private final CardKnowledge cards;
  private final PublicBehaviorInference behavior;
  private final CardLocationInference locations;
  private final TeamKnowledge teams;
  private final JokerBelief joker;
  private final DeclarerSuitInference declarerSuits;

  BoardInference(View view) {
    cards = new CardKnowledge(view);
    behavior = new PublicBehaviorInference(view);
    locations = new CardLocationInference(view, cards, behavior);
    teams = new TeamKnowledge(view, behavior);
    joker = new JokerBelief(view, teams, behavior, locations);
    declarerSuits = new DeclarerSuitInference(view, cards);
  }

  public CardKnowledge cards() {
    return cards;
  }

  public PublicBehaviorInference behavior() {
    return behavior;
  }

  public CardLocationInference locations() {
    return locations;
  }

  public TeamKnowledge teams() {
    return teams;
  }

  public JokerBelief joker() {
    return joker;
  }

  public DeclarerSuitInference declarerSuits() {
    return declarerSuits;
  }
}
