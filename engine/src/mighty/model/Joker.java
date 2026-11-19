package mighty.model;

public enum Joker implements Card {
  INSTANCE;

  @Override
  public int points() {
    return 0;
  }

  @Override
  public String displayName() {
    return "조커";
  }

  @Override
  public String toCode() {
    return "JK";
  }

  @Override
  public String toString() {
    return toCode();
  }

}
