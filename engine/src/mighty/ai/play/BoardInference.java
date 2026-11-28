package mighty.ai.play;

import mighty.game.observation.View;

/** 한 자리의 관측으로 만든 추론을 해당 턴의 모든 판단에서 재사용합니다. */
public final class BoardInference {

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
