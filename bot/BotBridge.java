package mighty.bridge;

import java.util.*;
import mighty.ai.AiPolicies;
import mighty.ai.bidding.RuleBiddingPolicy;
import mighty.ai.play.RulePlayPolicy;
import mighty.ai.preparation.*;
import mighty.data.CardParser;
import mighty.game.*;
import mighty.game.action.*;
import mighty.game.bidding.*;
import mighty.game.deal.*;
import mighty.game.friend.*;
import mighty.game.observation.*;
import mighty.game.trick.*;
import mighty.model.*;

/**
 * 로컬 테탑의 자기 관측을 현재 엔진 정책에 연결한다. 전체 패나 전체 진행 객체는 받지 않는다.
 */
public final class BotBridge {

  private static final Set<String> FIELDS = Set.of("requestId", "phase", "self", "hand", "score",
      "order",
      "active", "auction", "opening", "format", "contract", "declarer", "friend", "publicFriend",
      "friendResolved",
      "history", "trick", "discarded", "excluded", "pot", "contractHistory", "roundId");
  private static final AiPolicies POLICIES = AiPolicies.current();
  private static final RuleBiddingPolicy BID = POLICIES.bidding();
  private static final RulePlayPolicy PLAY = POLICIES.play();
  private static final RuleFriendPolicy FRIEND = POLICIES.friend();

  private static Map<String, Object> obj(Object x) {
    return mighty.data.Json.object(x);
  }

  private static List<?> list(Object x) {
    if (x instanceof List<?> v) {
      return v;
    }
    return List.of();
  }

  private static List<String> strings(Object x) {
    return list(x).stream().map(Object::toString).toList();
  }

  private static String str(Map<String, Object> m, String k) {
    return Objects.toString(m.get(k), "");
  }

  private static int num(Map<String, Object> m, String k) {
    return mighty.data.Json.integer(m, k, 0);
  }

  private static List<Card> cards(Object x) {
    return CardParser.parseList(String.join(" ", strings(x)));
  }

  private static Trump trump(String s) {
    return switch (s) {
      case "S" -> Trump.SPADE;
      case "D" -> Trump.DIAMOND;
      case "H" -> Trump.HEART;
      case "C" -> Trump.CLUB;
      case "N" -> Trump.NO_TRUMP;
      default -> throw new IllegalArgumentException("Unknown trump");
    };
  }

  private static String trump(Trump t) {
    return switch (t) {
      case SPADE -> "S";
      case DIAMOND -> "D";
      case HEART -> "H";
      case CLUB -> "C";
      case NO_TRUMP -> "N";
    };
  }

  private static Bid contract(Object x) {
    var m = obj(x);
    return new Bid(num(m, "points"), trump(str(m, "trump")));
  }

  private static Seat seat(String c, List<String> order) {
    int i = order.indexOf(c);
    if (i < 0) {
      throw new IllegalArgumentException("Unknown seat");
    }
    return new Seat(i);
  }

  private static FriendSelection selection(String s) {
    return switch (s) {
      case "FIRST" -> SpecialFriend.FIRST_TRICK;
      case "NONE" -> SpecialFriend.NONE;
      default -> new CardFriend(CardParser.parse(s));
    };
  }

  private static Action action(Map<String, Object> p, boolean opening) {
    Card card = CardParser.parse(str(p, "card"));
    if (card instanceof Joker) {
      return opening ? new JokerLead(Suit.parse(str(p, "jokerSuit").charAt(0))) : new JokerFollow();
    }
    return new StandardPlay((StandardCard) card, Boolean.TRUE.equals(p.get("jokerCall")));
  }

  private static List<PublicPlay> plays(Object x, List<String> order) {
    List<PublicPlay> out = new ArrayList<>();
    for (Object raw : list(x)) {
      var p = obj(raw);
      out.add(new PublicPlay(seat(str(p, "color"), order), action(p, out.isEmpty())));
    }
    return List.copyOf(out);
  }

  /**
   * 실제로 공개된 낙찰 공약과 주공의 변경만 전달합니다. 숨은 주력은 만들지 않습니다.
   */
  private static List<Bid> contractHistory(Map<String, Object> req) {
    List<Bid> bids = list(req.get("contractHistory")).stream().map(BotBridge::contract).toList();
    return bids.isEmpty() ? List.of(contract(req.get("contract"))) : bids;
  }

  private static List<PublicBid> auction(Map<String, Object> req, List<String> order) {
    return list(req.get("auction")).stream().map(BotBridge::obj).map(entry -> {
      BiddingAction action = "pass".equals(str(entry, "type")) ? BidPass.INSTANCE
          : new BidOffer(contract(entry));
      return new PublicBid(seat(str(entry, "color"), order), action);
    }).toList();
  }

  public static Map<String, Object> choose(Map<String, Object> req) {
    for (String field : req.keySet()) {
      if (!FIELDS.contains(field)) {
        throw new IllegalArgumentException("Unexpected input field: " + field);
      }
    }
    var order = strings(req.get("order"));
    if (order.size() < 5 || order.size() > 6 || new HashSet<>(order).size() != order.size()) {
      throw new IllegalArgumentException("Invalid table order");
    }
    String actor = str(req, "self");
    Seat self = seat(actor, order);
    List<Card> hand = cards(req.get("hand"));
    if (hand.size() > 13) {
      throw new IllegalArgumentException("Too many cards");
    }
    String phase = str(req, "phase");
    Map<String, Object> out = new LinkedHashMap<>();
    out.put("requestId", str(req, "requestId"));
    switch (phase) {
      case "bidding" -> {
        var active = strings(req.get("active"));
        var participants = active.stream().map(c -> seat(c, order)).toList();
        DealFormat format = "six-opening".equals(str(req, "format")) ? DealFormat.SIX_PLAYER_OPENING
            : DealFormat.FIVE_PLAYER_OPENING;
        BiddingState b = BiddingState.start(new DealParticipants(format, participants),
            seat(str(req, "opening"),
                order));
        for (var entry : auction(req, order)) {
          b = b.submit(entry);
        }
        out.put("bidCeilings", new mighty.ai.bidding.BiddingHandEvaluator()
            .assess(mighty.ai.bidding.BiddingHand.from(hand)).stream()
            .filter(mighty.ai.bidding.BidAssessment::canCompete)
            .map(a -> Map.of("trump", trump(a.trump()), "points", a.ceilingPoints())).toList());
        BiddingAction a = BID.choose(new BiddingView(self, hand, num(req, "score"), b,
            order.size() == 6 ? GameMode.SIX_PLAYER : GameMode.FIVE_PLAYER));
        if (a instanceof BidOffer offer) {
          out.put("type", "bid");
          out.put("points", offer.bid().points());
          out.put("trump", trump(offer.bid().trump()));
        } else {
          out.put("type", a instanceof DealMiss ? "dealMiss" : "pass");
        }
      }
      case "before" -> {
        if (hand.size() != 10 || new HashSet<>(hand).size() != 10) {
          throw new IllegalArgumentException("Expected 10 distinct cards before taking the kitty");
        }
        Bid current = contract(req.get("contract"));
        List<Bid> declarations = contractHistory(req);
        Bid award = declarations.get(0);
        Bid fifteenNoTrump = new Bid(15, Trump.NO_TRUMP);
        Bid selected = award.equals(fifteenNoTrump) && current.equals(fifteenNoTrump)
            ? POLICIES.preparation().beforeKittyContract(hand, award, current)
            : PreparationPlanner.beforePublicBid(mighty.ai.bidding.BiddingHand.from(hand),
                award, current, self, auction(req, order)).orElse(current);
        out.put("type", "contract");
        out.put("points", selected.points());
        out.put("trump", trump(selected.trump()));
      }
      case "discard" -> {
        List<Bid> declarations = contractHistory(req);
        out.put("type", "discard");
        out.put("cards", POLICIES.preparation()
            .choose(new DiscardView(self, contract(req.get("contract")), hand, auction(req, order)),
                declarations.get(0), declarations).discarded().stream().map(Card::toCode).toList());
      }
      case "after" -> {
        Bid current = contract(req.get("contract"));
        List<Bid> declarations = contractHistory(req);
        var resolution = POLICIES.preparation().resolveContract(
            new FriendView(self, current, hand, cards(req.get("discarded")), declarations, auction(req, order)),
            declarations.get(0),
            declarations);
        Bid selected = resolution.contract();
        out.put("declarations", resolution.declarations().stream()
            .map(b -> Map.of("points", b.points(), "trump", trump(b.trump()))).toList());
        out.put("type", "contract");
        out.put("points", selected.points());
        out.put("trump", trump(selected.trump()));
      }
      case "friend" -> {
        FriendSelection f = FRIEND.choose(
            new FriendView(self, contract(req.get("contract")), hand, cards(req.get("discarded")),
                contractHistory(req), auction(req, order)));
        out.put("type", "friend");
        out.put("friend", f instanceof CardFriend cf ? cf.card().toCode()
            : f == SpecialFriend.NONE ? "NONE" : "FIRST");
      }
      case "play" -> {
        var active = strings(req.get("active"));
        var table = new TableLayout(order.size() == 6 ? GameMode.SIX_PLAYER : GameMode.FIVE_PLAYER,
            order.stream().map(c -> seat(c,
                order)).toList());
        PlayOrder po = PlayOrder.from(table,
            new HashSet<>(active.stream().map(c -> seat(c, order)).toList()));
        List<PublicTrick> history = new ArrayList<>();
        for (Object raw : list(req.get("history"))) {
          var p = obj(raw);
          history.add(
              new PublicTrick(new TrickNumber(num(p, "number")), plays(p.get("plays"), order),
                  seat(str(p,
                      "winner"), order)));
        }
        FriendStatus status =
            !str(req, "publicFriend").isBlank() ? new RevealedFriend(seat(str(req, "publicFriend"),
                order)) : Boolean.TRUE.equals(req.get("friendResolved")) ? NoFriend.INSTANCE
                : UnrevealedFriend.INSTANCE;
        Seat declarer = seat(str(req, "declarer"), order);
        PrivateInfo info =
            self.equals(declarer) ? new DeclarerInfo(hand, cards(req.get("discarded")))
                : new NonDeclarerInfo(hand);
        if (!self.equals(declarer) && !list(req.get("discarded")).isEmpty()) {
          throw new IllegalArgumentException("Discarded cards are private to declarer");
        }
        View view = ObservationFactory.create(self, info, contract(req.get("contract")),
            selection(str(req, "friend")),
            new Context(po, declarer, status, history, contractHistory(req), auction(req, order)),
            plays(req.get("trick"), order));
        if (mighty.game.setting.SettingProof.canDeclare(view)) {
          out.put("type", "setting");
          break;
        }
        var decision = PLAY.recommend(view);
        Action a = decision.action();
        out.put("reason", decision.reason());
        out.put("type", "play");
        out.put("card", a.card().toCode());
        if (a instanceof JokerLead jl) {
          out.put("jokerSuit", String.valueOf(jl.declaredSuit().code()));
        }
        out.put("jokerCall", a instanceof StandardPlay sp && sp.call());
      }
      case "kill" -> {
        Set<Card> excluded = new HashSet<>(hand);
        excluded.addAll(cards(req.get("excluded")));
        Card chosen = DeckFactory.create().stream().filter(c -> !excluded.contains(c)).findFirst()
            .orElseThrow();
        out.put("type", "kill");
        out.put("card", chosen.toCode());
      }
      case "bonus" -> {
        out.put("type", "bonus");
        out.put("amount", num(req, "pot"));
      }
      default -> throw new IllegalArgumentException("Unsupported phase");
    }
    return out;
  }

  public static void main(String[] args) throws Exception {
    BridgeHttpServer.start(args.length == 0 ? 18743 : Integer.parseInt(args[0]));
  }

}
