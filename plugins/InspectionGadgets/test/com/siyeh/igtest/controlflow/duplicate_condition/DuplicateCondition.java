package com.siyeh.igtest.controlflow.duplicate_condition;

public class DuplicateCondition {

  void x(boolean b) {
    if (<warning descr="Duplicate condition 'b'">b</warning> || <warning descr="Duplicate condition 'b'">b</warning> || <warning descr="Duplicate condition 'b'">b</warning> ) {

    } else if (<warning descr="Duplicate condition 'b'">b</warning>) {

    } else if (<warning descr="Duplicate condition 'b'">b</warning>) {}
  }

  void x2(boolean b, boolean c) {
    if(<warning descr="Duplicate condition 'b'">b</warning> || c) {
      System.out.println("ok");
      return;
    }
    if(<warning descr="Duplicate condition 'b'">b</warning>) {
      System.out.println("ok");
    } else {
      if(<warning descr="Duplicate condition 'b'">b</warning>) {
        System.out.println("ok");
      }
    }
  }

  public void test(int x, int y) {
    if(<warning descr="Duplicate condition 'x < y'">x < y</warning>) {
      System.out.println("first");
      return;
    }
    if(<warning descr="Duplicate condition 'y > x'">y > x</warning>) {
      System.out.println("second");
      return;
    }
  }

  public void foo()
  {
    if(<warning descr="Duplicate condition 'bar()'">bar()</warning>||<warning descr="Duplicate condition 'bar()'">bar()</warning>)
    {
      System.out.println("1");
    }else if(<warning descr="Duplicate condition 'bar()'">bar()</warning>|| true)
    {
      System.out.println("2");
    }
  }

  public boolean bar()
  {
    return true;
  }

  void incompleteCode(String s) {
    if (s.contains(<error descr="Cannot resolve symbol 'A'">A</error>)) {

    } else if (s.contains(<error descr="Cannot resolve symbol 'B'">B</error>)) {

    }
  }
}