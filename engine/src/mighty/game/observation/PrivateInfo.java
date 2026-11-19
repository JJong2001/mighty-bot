package mighty.game.observation;

import java.util.List;
import mighty.model.Card;

public sealed interface PrivateInfo
    permits DeclarerInfo, NonDeclarerInfo {

  List<Card> hand();
}
