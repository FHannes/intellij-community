package ppp;
import qqq.*;

public class A {
  private C delegate;

  public A(C delegate) {
    this.delegate = delegate;
  }

  void foo() {
    B.util(delegate);
  }
}
