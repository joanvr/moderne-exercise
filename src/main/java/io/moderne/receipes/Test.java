package io.moderne.receipes;

public class Test {

    interface I {
        int test();
    }
    class A {
        I i = new I() {
            public int test() {
                return 0;
            }
        };
    }
}
