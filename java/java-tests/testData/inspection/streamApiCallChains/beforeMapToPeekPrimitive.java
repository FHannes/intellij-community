// "Replace with 'peek'" "true"

import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

public class Main {
  void test() {
    AtomicInteger counter = new AtomicInteger();
    int[] ints = IntStream.range(0, 100)
      .filter(x -> x % 3 == 0)
      .m<caret>ap((x -> {
        counter.incrementAndGet();
        return x;
      }))
      .toArray();
    System.out.println(counter.get());
  }
}
