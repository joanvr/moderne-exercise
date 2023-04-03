package io.moderne.recepies;

import io.moderne.receipes.NonOverridableMethodsNoInstanceDataToStaticRecipe;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.openrewrite.test.RecipeSpec;
import org.openrewrite.test.RewriteTest;

import static org.openrewrite.java.Assertions.java;

public class NonOverridableMethodsNoInstanceDataToStaticRecipeTest implements RewriteTest {
    @Override
    public void defaults(RecipeSpec spec) {
        spec.recipe(new NonOverridableMethodsNoInstanceDataToStaticRecipe());
    }


    @Nested
    class NotModify {

        @Nested
        class Visibility {
            @Test
            void publicVisibilityMethods() {
                rewriteRun(java("""
                        class A {
                            public void test() {}      
                        }      
                        """));
            }

            @Test
            void packageVisibilityMethods() {
                rewriteRun(java("""
                        class A {
                            void test() {}      
                        }      
                        """));
            }

            @Test
            void protectedVisibilityMethods() {
                rewriteRun(java("""
                        class A {
                            protected void test() {}      
                        }      
                        """));
            }
        }

        @Nested
        class Constructors {
            @Test
            void publicConstructors() {
                rewriteRun(java("""
                        class A {
                            public A() {}      
                        }      
                        """));
            }

            @Test
            void packageConstructors() {
                rewriteRun(java("""
                        class A {
                            A() {}      
                        }      
                        """));
            }

            @Test
            void protectedConstructors() {
                rewriteRun(java("""
                        class A {
                            protected A() {}      
                        }      
                        """));
            }

            @Test
            void privateConstructors() {
                rewriteRun(java("""
                        class A {
                            private A() {}      
                        }      
                        """));
            }
        }

        @Nested
        class InstanceAccess {
            @Test
            void privateMethodsInstanceAccess() {
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
            void finalMethodsInstanceAccess() {
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
            void thisAccess() {
                rewriteRun(java("""
                        class A {
                            private A test() {
                                return this;
                            }      
                        }      
                        """));
            }

            @Test
            void superAccess() {
                rewriteRun(java("""
                        class A {
                            private int test() {
                                return super.hashCode();
                            }      
                        }      
                        """));
            }

            @Test
            void instanceMethodAccess() {
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
            void instanceFieldExplicitThisAccess() {
                rewriteRun(java("""
                        class A {
                            int field = 0;
                            private int test() {
                                return this.a;
                            }
                        }   
                        """));
            }

            @Test
            void instanceMethodExplicitThisAccess() {
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
            void instanceAccessInVariableDeclaration() {
                rewriteRun(java("""
                        class A {
                            int a = 0;
                            final int test() {
                                int i = a;
                                return i;
                            }      
                        }      
                        """));
            }

            @Test
            void instanceMethodAccessInVariableDeclaration() {
                rewriteRun(java("""
                        class A {
                            int method() { 
                                return 0;
                            }
                            private int test() {
                                int i = method();
                                return i;
                            }      
                        }      
                        """));
            }

            @Test
            void instanceAccessInExpression() {
                rewriteRun(java("""
                        class A {
                            int a = 0;
                            final int test() {
                                return a + 42;
                            }      
                        }      
                        """));
            }

            @Test
            void instanceAccessWrite() {
                rewriteRun(java("""
                        class A {
                            int a = 0;
                            private void test() {
                                a = 42;
                            }      
                        }      
                        """));
            }

            @Test
            void instanceAccessThisWrite() {
                rewriteRun(java("""
                        class A {
                            int a = 0;
                            private void test() {
                                this.a = 42;
                            }      
                        }      
                        """));
            }

            @Test
            void instanceMethodAccessInExpression() {
                rewriteRun(java("""
                        class A {
                            int method() { 
                                return 0;
                            }
                            private int test() {
                                return method() + 42;
                            }      
                        }      
                        """));
            }

            @Test
            void innerClassInstantiation() {
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
            void innerClassAccessExplicitThis() {
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
            void methodLocalInnerClassInstanceDataAccessInField() {
                rewriteRun(java("""
                        class A {
                            int a = 0;
                            private int method() {
                                class C {
                                    int field = a;
                                }
                                C c = new C();
                                return c.field;
                            }
                        }
                        """));
            }

            @Test
            void methodLocalInnerClassInstanceMethodAccessInField() {
                rewriteRun(java("""
                        class A {
                            int method() {
                                return 0;
                            }
                            private int method() {
                                class C {
                                    int field = method();
                                }
                                C c = new C();
                                return c.field;
                            }
                        }
                        """));
            }

            @Test
            void methodLocalInnerClassInstanceDataAccessInMethod() {
                rewriteRun(java("""
                        class A {
                            int a = 0;
                            private int method() {
                                class C {
                                    int inner() {
                                        return a;
                                    }
                                }
                                C c = new C();
                                return c.inner();
                            }
                        }
                        """));
            }

        }

        @Nested
        class Inheritance {
            @Test
            void accessToParentField() {
                rewriteRun(java("""
                        class B {
                            protected int b = 0;
                        }
                        class A extends B {
                            private int test() {
                                return b;
                            }
                        }
                        """));
            }

            @Test
            void accessToParentMethod() {
                rewriteRun(java("""
                        class B {
                            protected void parent() {}
                        }
                        class A extends B {
                            private void test() {
                                parent();
                            }
                        }
                        """));
            }

        }

        @Nested
        class AnonymousClass {
            @Test
            void publicMethodInAnonymousClassFromInterface() {
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
            void privateMethodInAnonymousClassAnonymousInstanceAccess() {
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
            void privateMethodInAnonymousClassEnclosingClassInstanceAccess() {
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
            void privateMethodInAnonymousInsideMethodWithInstanceDataAccess() {
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
        }

        @Nested
        class SerializableException {
            @Test
            void methodsException() {
                rewriteRun(java("""
                        class A implements java.io.Serializable {
                            private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
                            }
                            
                            private void readObject(java.io.ObjectInputStream in) throws java.io.IOException, java.lang.ClassNotFoundException {
                            }
                            
                            private void readObjectNoData() throws java.io.ObjectStreamException {
                            }
                        }
                        """));
            }
        }
        @Nested
        class Limitations {
            @Test
            void selfRecursiveCall() {
                rewriteRun(java("""
                        class A {
                            private int test() {
                                return test();
                            }
                        }
                        """));
            }

            @Test
            void crossRecursiveCalls() {
                rewriteRun(java("""
                        class A {
                            private int test1() {
                                return test2();
                            }
                            private int test2() {
                                return test1();
                            }
                        }
                        """));
            }
        }
    }

    @Nested
    class Modify {

        @Nested
        class NoInstanceAccess {
            @Test
            void noInstanceAccess() {
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
            void staticFieldAccess() {
                rewriteRun(java("""
                        class A {
                            public static int field = 0;
                            
                            private int test() {
                                return field;
                            }
                        }
                        """, """
                        class A {
                            public static int field = 0;
                            
                            private static int test() {
                                return field;
                            }
                        }
                        """));
            }

            @Test
            void staticMethodAccess() {
                rewriteRun(java("""
                        class A {
                            public static void method() {}
                            
                            private void test() {
                                method();
                            }
                        }
                        """, """
                        class A {
                            public static void method() {}
                            
                            private static void test() {
                                method();
                            }
                        }
                        """));
            }

            @Test
            void parametersOnlyAccess() {
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
            void parameterShadowInstanceVariableAccess() {
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
        }

        @Nested
        class OtherInstance {
            @Test
            void instanceAccessOfOtherInstanceByParameter() {
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
            void methodInvocationOfOtherInstanceByParameter() {
                rewriteRun(java("""
                        class A {
                            void method() {};
                                            
                            private void test(A a) {
                                a.method();
                            }
                        }
                        """, """
                        class A {
                            void method() {}
                                            
                            private static void test(A a) {
                                a.method();
                            }
                        }
                        """));
            }

            @Test
            void newInnerClassOfOtherInstanceByParameter() {
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

        }

        @Nested
        class StaticNestedClass {
            @Test
            void staticNestedClassInstantiation() {
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
            void staticNestedClassFieldAccess() {
                rewriteRun(java("""
                        class A {
                            static class B {
                                int field = 0;
                            }
                            
                            private int test() {
                                B b = new B();
                                return b.field;
                            }
                        }
                        """, """
                        class A {
                            static class B {
                                int field = 0;
                            }
                            
                            private static int test() {
                                B b = new B();
                                return b.field;
                            }
                        }
                        """));
            }

            @Test
            void staticNestedClassMethodInvocation() {
                rewriteRun(java("""
                        class A {
                            static class B {
                                void method() {}
                            }
                            
                            private void test() {
                                B b = new B();
                                b.method();
                            }
                        }
                        """, """
                        class A {
                            static class B {
                                void method() {}
                            }
                            
                            private static void test() {
                                B b = new B();
                                b.method();
                            }
                        }
                        """));
            }

            @Test
            void staticFieldAccessInNestedClass() {
                rewriteRun(java("""
                        class A {
                            class B {
                                static int field = 0;
                            }
                            
                            private int test() {
                                return B.field;
                            }
                        }
                        """, """
                        class A {
                            class B {
                                static int field = 0;
                            }
                            
                            private static int test() {
                                return B.field;
                            }
                        }
                        """));
            }

            @Test
            void staticMethodAccessInNestedClass() {
                rewriteRun(java("""
                        class A {
                            class B {
                                static int method() {
                                    return 0;
                                }
                            }
                            
                            private int test() {
                                return B.method();
                            }
                        }
                        """, """
                        class A {
                            class B {
                                static int method() {
                                    return 0;
                                }
                            }
                            
                            private static int test() {
                                return B.method();
                            }
                        }
                        """));
            }

            @Test
            void staticAccessInMultipleNestedClass() {
                rewriteRun(java("""
                        class A {
                            class B {
                                class C {
                                    static int field = 0;
                                }
                            }
                            
                            private int test() {
                                return B.C.field;
                            }
                        }
                        """, """
                        class A {
                            class B {
                                class C {
                                    static int field = 0;
                                }
                            }
                            
                            private static int test() {
                                return B.C.field;
                            }
                        }
                        """));
            }

        }

        @Nested
        class OtherClass {
            @Test
            void otherClassInstantiation() {
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
            void otherClassInstanceAccess() {
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
            void otherClassMethodInvocation() {
                rewriteRun(java("""
                        class B {
                            void method() {}
                        }
                        class A {
                            private int test(B b) {
                                return b.method();
                            }
                        }
                        """, """
                        class B {
                            void method() {}
                        }
                        class A {
                            private static int test(B b) {
                                return b.method();
                            }
                        }
                        """));
            }

            @Test
            void otherClassInnerClassInstantiation() {
                rewriteRun(java("""
                        class B {
                            class C {
                            }
                        }
                        class A {
                            private int test() {
                                B b = new B();
                                return b.new C();
                            }
                        }
                        """, """
                        class B {
                            class C {
                            }
                        }
                        class A {
                            private static int test() {
                                B b = new B();
                                return b.new C();
                            }
                        }
                        """));
            }

        }

        @Nested
        class AnonymousClass {

            @Test
            void privateMethodInAnonymousClass() {
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
            void privateMethodInAnonymousInsideMethod() {
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
            void privateMethodInAnonymousInsideMethodWithAccessToParameter() {
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
            void anonymousClassInsideMethodWithAccessToAnonymousClassField() {
                rewriteRun(java("""
                        class C {
                            int field;
                        }
                        class A {
                            private int test() {
                                C c = new C() {
                                };
                                return c.field;
                            }
                        }
                        """, """
                            class C {
                                int field;
                            }
                            class A {
                                private static int test() {
                                    C c = new C() {
                                    };
                                    return c.field;
                                }
                            }
                        """));
            }

            @Test
            void anonymousClassInsideMethodWithAccessToAnonymousClassMethod() {
                rewriteRun(java("""
                        interface I {
                            int method();
                        }
                        class A {
                            private int test() {
                                I i = new I() {
                                    public int method() {
                                        return 0;
                                    }
                                };
                                return i.method();
                            }
                        }
                        """, """
                            interface I {
                                int method();
                            }
                            class A {
                                private static int test() {
                                    I i = new I() {
                                        public int method() {
                                            return 0;
                                        }
                                    };
                                    return i.method();
                                }
                            }
                        """));
            }

        }

        @Nested
        class MethodLocalInnerClass {

            @Test
            public void methodLocalInnerClassInstantiation() {
                rewriteRun(java("""
                        class A {
                            private void method() {
                                class C {
                                }
                                C c = new C();
                            }
                        }
                        """, """
                        class A {
                            private static void method() {
                                class C {
                                }
                                C c = new C();
                            }
                        }
                        """));
            }

            @Test
            public void methodLocalInnerClassInstanceAccess() {
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

        }
    }
}
