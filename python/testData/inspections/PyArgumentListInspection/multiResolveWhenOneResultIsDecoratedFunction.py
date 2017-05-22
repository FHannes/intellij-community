class C1:
    def foo(self, x):
        return self


class C2:
    @decorated
    def foo(self, x, y):
        return self


def f():
    """
    :rtype: C1 | C2
    """
    pass


f().foo()
f().foo(1)
f().foo(1, 2)
f().foo(1, 2, 3)
