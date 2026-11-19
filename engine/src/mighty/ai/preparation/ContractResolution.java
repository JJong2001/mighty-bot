package mighty.ai.preparation;

import java.util.List;
import mighty.game.bidding.Bid;

/**
 * 최종 공약과 이를 공개하기 위해 순서대로 선언할 공약입니다.
 */
public record ContractResolution(Bid contract, List<Bid> declarations) {

  public ContractResolution {
    declarations = List.copyOf(declarations);
  }

}
