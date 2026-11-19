package mighty.game.observation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.*;
import mighty.data.CardParser;
import mighty.game.*;
import mighty.game.action.StandardPlay;
import mighty.game.trick.*;
import mighty.model.*;
import org.junit.jupiter.api.Test;

class PublicCardHistoryTest {

  private static Card c(String code) {
    return CardParser.parse(code);
  }

  private static PublicPlay p(int seat, String code) {
    return new PublicPlay(new Seat(seat), new StandardPlay((StandardCard) c(code), false));
  }

  private static List<PublicTrick> completed() {
    return List.of(
        new PublicTrick(new TrickNumber(1),
            List.of(p(0, "C2"), p(1, "CA"), p(2, "C3"), p(3, "C4"), p(4, "C5")),
            new Seat(1)),
        new PublicTrick(new TrickNumber(2),
            List.of(p(1, "H2"), p(2, "HA"), p(3, "H3"), p(4, "H4"), p(0, "H5")),
            new Seat(2)));
  }

  private static PublicCardHistory history() {
    return new PublicCardHistory(completed(), List.of(p(2, "D2"), p(3, "D3")));
  }

  @Test
  void includesCurrentTrickWithoutLosingCompletedOrder() {
    var h = history();
    assertEquals(10, h.completedPlays().size());
    assertEquals(List.of(p(2, "D2"), p(3, "D3")), h.currentPlays());
    assertEquals(12, h.submittedPlays().size());
    assertEquals(p(0, "C2"), h.submittedPlays().get(0));
    assertEquals(p(3, "D3"), h.submittedPlays().get(11));
    assertTrue(h.hasBeenPlayed(c("D2")));
    assertFalse(h.hasBeenPlayed(c("SK")));
  }

  @Test
  void preservesWhoActuallySubmittedCard() {
    assertTrue(history().wasPlayedBy(new Seat(1), c("CA")));
    assertFalse(history().wasPlayedBy(new Seat(0), c("CA")));
    assertTrue(history().wasPlayedBy(new Seat(3), c("D3")));
  }

  @Test
  void pastSnapshotDoesNotIncludeLaterCards() {
    var through = history().completedCardsThrough(new TrickNumber(1));
    assertEquals(5, through.size());
    assertTrue(through.contains(c("CA")));
    assertFalse(through.contains(c("HA")));
    assertFalse(through.contains(c("D2")));
  }

  @Test
  void returnedCollectionsCannotBeChanged() {
    var h = history();
    assertThrows(UnsupportedOperationException.class, () -> h.cards().add(c("SK")));
    assertThrows(UnsupportedOperationException.class, () -> h.submittedPlays().clear());
    assertThrows(UnsupportedOperationException.class,
        () -> h.completedCardsThrough(new TrickNumber(1)).clear());
  }

  @Test
  void laterMutationOfInputsDoesNotAlterObservation() {
    var tricks = new ArrayList<>(completed());
    var current = new ArrayList<>(List.of(p(2, "D2")));
    var h = new PublicCardHistory(tricks, current);
    tricks.clear();
    current.clear();
    assertEquals(11, h.cards().size());
    assertEquals(10, h.completedPlays().size());
    assertEquals(1, h.currentPlays().size());
  }

}
