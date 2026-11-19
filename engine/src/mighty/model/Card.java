package mighty.model;

public sealed interface Card permits StandardCard, Joker {

  int points();

  String displayName();

  String toCode();
}
