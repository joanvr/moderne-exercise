package io.moderne.recepies;

import io.moderne.receipes.PrivateFinalMethodsNoInstanceDataStaticReceipe;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class PrivateFinalMethodsNoInstanceDataStaticReceipeTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new PrivateFinalMethodsNoInstanceDataStaticReceipe());
    }

    @Test
    void doesNotModifyPublicVisibilityMethods() {
        rewriteRun(java("""
                class A {
                    public void test() {}      
                }      
                """));
    }

    @Test
    void doesNotModifyPackageVisibilityMethods() {
        rewriteRun(java("""
                class A {
                    void test() {}      
                }      
                """));
    }

    @Test
    void doesNotModifyProtectedVisibilityMethods() {
        rewriteRun(java("""
                class A {
                    protected void test() {}      
                }      
                """));
    }

    @Test
    void doesNotModifyConstructors() {
        rewriteRun(java("""
                class A {
                    private A() {}      
                }      
                """));
    }

    @Test
    void doesNotModifyPrivateVisibilityMethodsIfInstanceAccess() {
        rewriteRun(java("""
                class A {
                    int a = 0;
                    private int test() {
                        return a;
                    }      
                }      
                """));
    }

    @Test
    void doesNotModifyFinalVisibilityMethodsIfInstanceAccess() {
        rewriteRun(java("""
                class A {
                    int a = 0;
                    final int test() {
                        return a;
                    }      
                }      
                """));
    }

    @Test
    void doesNotModifyIfThisAccess() {
        rewriteRun(java("""
                class A {
                    private A test() {
                        return this;
                    }      
                }      
                """));
    }

    @Test
    void doesNotModifyIfSuperAccess() {
        rewriteRun(java("""
                class A {
                    private int test() {
                        return super.hashCode();
                    }      
                }      
                """));
    }

    @Test
    void doesNotModifyIfInstaceMethodAccess() {
        rewriteRun(java("""
                class A {
                    void method() {}
                    private void test() {
                        method();
                    }
                }   
                """));
    }

    @Test
    void doesNotModifyIfInstaceMethodThroughThisAccess() { // TODO with static too
        rewriteRun(java("""
                class A {
                    void method() {}
                    private void test() {
                        this.method();
                    }
                }   
                """));
    }


    @Test
    void doesNotModifyIfInnerClassAccess() {
        rewriteRun(java("""
                class A {
                    class B {}
                    private B test() {
                        return new B();
                    }
                }
                """));
    }

    @Test
    void doesNotModifyIfInnerClassAccessWithThis() {
        rewriteRun(java("""
                class A {
                    class B {}
                    private B test() {
                        return this.new B();
                    }
                }
                """));
    }

    @Test
    void doesModifyNoInstanceAccess() {
        rewriteRun(java("""
                class A {
                    private int test() {
                        return 0;
                    }
                }
                """, """
                class A {
                    private static int test() {
                        return 0;
                    }
                }
                """));
    }

    @Test
    void doesModifyParametersOnlyAccess() {
        rewriteRun(java("""
                class A {
                    private int test(int a) {
                        return a;
                    }
                }
                """, """
                class A {
                    private static int test(int a) {
                        return a;
                    }
                }
                """));
    }

    @Test
    void doesModifyParameterShadowInstanceVariableAccess() {
        rewriteRun(java("""
                class A {
                    int a = 0;
                    
                    private int test(int a) {
                        return a;
                    }      
                }      
                """, """
                class A {
                    int a = 0;
                    
                    private static int test(int a) {
                        return a;
                    }      
                }      
                """));
    }


    @Test
    void doesModifyNewInnerClassOfOtherInstanceByParameterSameClass() {
        rewriteRun(java("""
                class A {
                    class B {}
                                    
                    private B test(A a) {
                        return a.new B();
                    }
                }
                """, """
                class A {
                    class B {}
                                    
                    private static B test(A a) {
                        return a.new B();
                    }
                }
                """));
    }

    @Test
    void doesModifyInstanceAccessOfParameterSameClass() {
        rewriteRun(java("""
                class A {
                    int field = 0;
                                    
                    private int test(A a) {
                        return a.field;
                    }
                }
                """, """
                class A {
                    int field = 0;
                                    
                    private static int test(A a) {
                        return a.field;
                    }
                }
                """));
    }

    @Test
    void doesModifyStaticNestedClassAccess() {
        rewriteRun(java("""
                class A {
                    static class B {}
                    
                    private B test() {
                        return new B();
                    }
                }
                """, """
                class A {
                    static class B {}
                    
                    private static B test() {
                        return new B();
                    }
                }
                """));
    }

    @Test
    void doesModifyOtherClassAccess() {
        rewriteRun(java("""
                class B {}
                class A {
                    private B test() {
                        return new B();
                    }
                }
                """, """
                class B {}
                class A {
                    private static B test() {
                        return new B();
                    }
                }
                """));
    }

    @Test
    void doesModifyOtherClassInstanceAccess() {
        rewriteRun(java("""
                class B {
                    int field = 0;
                }
                class A {
                    private int test(B b) {
                        return b.field;
                    }
                }
                """, """
                class B {
                    int field = 0;
                }
                class A {
                    private static int test(B b) {
                        return b.field;
                    }
                }
                """));
    }

    @Test
    void doesNotModifyPublicMethodInAnonymousClass() {
        rewriteRun(java("""
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
                """));
    }

    @Test
    void doesModifyPrivateMethodInAnonymousClass() {
        rewriteRun(java("""
                interface I {}
                class A {
                    I i = new I() {
                        private int test() {
                            return 0;
                        }
                    };
                }
                """, """
                interface I {}
                class A {
                    I i = new I() {
                        private static int test() {
                            return 0;
                        }
                    };
                }
                """));
    }

    @Test
    void doesNotModifyPrivateMethodInAnonymousClassAnonymousInstanceAccess() {
        rewriteRun(java("""
                interface I {}
                class A {
                    I i = new I() {
                        int field = 0;
                        private int test() {
                            return field;
                        }
                    };
                }
                """));
    }

    @Test
    void doesNotModifyPrivateMethodInAnonymousClassInstanceAccessEnclosingClass() {
        rewriteRun(java("""
                interface I {}
                class A {
                    int field = 0;
                    I i = new I() {
                        private int test() {
                            return field;
                        }
                    };
                }
                """));
    }

    @Test
    void doesModifyPrivateMethodInAnonymousInsideMethodClass() {
        rewriteRun(java("""
                interface I {}
                class A {
                    void a() {
                        I i = new I() {
                            private int test() {
                                return 0;
                            }
                        };
                    }
                }
                """, """
                interface I {}
                class A {
                    void a() {
                        I i = new I() {
                            private static int test() {
                                return 0;
                            }
                        };
                    }
                }
                """));
    }

    @Test
    void doesModifyAnonymousInsideMethodWithAccessToParameter() {
        rewriteRun(java("""
                interface I {}
                class A {
                    void a(int parameter) {
                        I i = new I() {
                            private int test() {
                                return parameter;
                            }
                        };
                    }
                }
                """, """
                interface I {}
                class A {
                    void a(int parameter) {
                        I i = new I() {
                            private static int test() {
                                return parameter;
                            }
                        };
                    }
                }
                """));
    }

    @Test
    void doesNotModifyAnonymousInsideMethodWithAccessToInstanceDataClass() {
        rewriteRun(java("""
                interface I {}
                class A {
                    int field = 0;
                    void a() {
                        I i = new I() {
                            private int test() {
                                return field;
                            }
                        };
                    }
                }
                """));
    }

    @Test
    public void doesModifyMethodLocalInnerClassAccess() {
        rewriteRun(java("""
                class A {
                    private int method() {
                        class C {
                            int field = 0;
                        }
                        C c = new C();
                        return c.field;
                    }
                }
                """, """
                class A {
                    private static int method() {
                        class C {
                            int field = 0;
                        }
                        C c = new C();
                        return c.field;
                    }
                }
                """));
    }

    @Test
    void doesNotModifyAnonymousInsideMethodWithAccessToInstanceDataClassB() {
        rewriteRun(java("""
                interface I {}
                class A {
                    int field = 0;
                    private int test() {
                        I i = new I() {
                            private int method() {
                                return 0;
                            }
                        };
                        return i.method();
                    }
                }
                """));
    }

}
