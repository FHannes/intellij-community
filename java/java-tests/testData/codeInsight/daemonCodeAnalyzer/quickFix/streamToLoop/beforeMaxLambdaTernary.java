// "Replace Stream API chain with loop" "true"

import java.util.*;

public class Main {
  private static Optional<String> max(Map<String, List<String>> dependencies, String fruits, Map<String, String> weights) {
    return dependencies.get(fruits).stream().m<caret>ax((o1, o2) -> o1.compareTo(o2) < 0 ? -1 : o1.compareTo(o2) > 0 ? 1 : 0);
  }
}
