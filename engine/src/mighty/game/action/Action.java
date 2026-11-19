package mighty.game.action;

import mighty.model.Card;

public sealed interface Action
    permits StandardPlay, JokerLead, JokerFollow {

  Card card();
}
