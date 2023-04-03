package io.moderne.receipes;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.MethodMatcher;
import org.openrewrite.java.TreeVisitingPrinter;
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class NonOverridableMethodsNoInstanceDataToStaticRecipe extends Recipe {

    @Override
    public String getDisplayName() {
        return "\"private\" and \"final\" methods that don't access instance data should be \"static\"";
    }

    @Override
    public String getDescription() {
        return "Non-overridable methods (private or final) that don’t access instance data can be static to prevent any misunderstanding about the contract of the method.";
    }

    @Override
    public JavaIsoVisitor<ExecutionContext> getVisitor() {
        return new JavaIsoVisitor<ExecutionContext>() {
            private final List<MethodMatcher> serializableMethods = List.of(
                    new MethodMatcher("* writeObject(java.io.ObjectOutputStream)"),
                    new MethodMatcher("* readObject(java.io.ObjectInputStream)"),
                    new MethodMatcher("* readObjectNoData()")
            );

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDec, ExecutionContext executionContext) {
                J.MethodDeclaration methodDeclaration = super.visitMethodDeclaration(methodDec, executionContext);

                // If the method already has a "static" modifier, we don't need to check anything else
                if (methodDeclaration.hasModifier(J.Modifier.Type.Static)) {
                    return methodDeclaration;
                }

                // If the method is a constructor, we cannot make it static
                if (methodDeclaration.isConstructor()) {
                    return methodDeclaration;
                }

                // If it's not private or final, it could be overriden, so we cannot make it static
                if (!(methodDeclaration.hasModifier(J.Modifier.Type.Private) ||
                        methodDeclaration.hasModifier(J.Modifier.Type.Final))) {
                    return methodDeclaration;
                }

                // We need to check for the exceptions of java.io.Serializable methods
                JavaType.Method method = methodDeclaration.getMethodType();
                if (method != null) {
                    List<JavaType.FullyQualified> interfaces = method.getDeclaringType().getInterfaces();
                    if (interfaces.stream().anyMatch(i -> i.getFullyQualifiedName().equals("java.io.Serializable")) &&
                        serializableMethods.stream().anyMatch(matcher -> matcher.matches(method))
                    ) {
                        return methodDeclaration;
                    }
                }


                // At this point we have to check if the body of the method has any access to instance data.
                // We should always have a body, since private or final methods cannot be abstract, so no need to check that
                if (!AccessInstanceDataVisitor.find(methodDeclaration.getBody()).get()) {
                    methodDeclaration = autoFormat(
                            methodDeclaration.withModifiers(
                                    ListUtils.concat(methodDeclaration.getModifiers(), new J.Modifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, J.Modifier.Type.Static, Collections.emptyList()))
                            ), executionContext);
                }

                return methodDeclaration;
            }
        };
    }

    @Value
    @EqualsAndHashCode(callSuper = true)
    private static class AccessInstanceDataVisitor extends JavaIsoVisitor<AtomicBoolean> {

        static AtomicBoolean find(J.Block body) {
            return new AccessInstanceDataVisitor()
                    .reduce(body, new AtomicBoolean());
        }


        @Override
        public J.Identifier visitIdentifier(J.Identifier id, AtomicBoolean hasInstanceAccess) {
            // Return quickly if we already found an instance access before
            if (hasInstanceAccess.get()) {
                return id;
            }

            J.Identifier identifier = super.visitIdentifier(id, hasInstanceAccess);

            Cursor parent = getCursor().getParent();
            if (parent != null) {
                // Discard if the identifier is a NamedVariable
                if (parent.getValue() instanceof J.VariableDeclarations.NamedVariable) {
                    return identifier;
                }

                // Discard if the identifier is a ClassDeclaration
                if (parent.getValue() instanceof J.ClassDeclaration) {
                    return identifier;
                }

                // Discard if the identifier is a MethodDeclaration
                if (parent.getValue() instanceof J.MethodDeclaration) {
                    return identifier;
                }

                // Discard if identifier in MethodInvocation: analyzing those in visitMethodInvocation
                if (parent.getValue() instanceof J.MethodInvocation) {
                    return identifier;
                }

                // Discard if identifier in NewClass: analyzing those in visitNewClass
                if (parent.getValue() instanceof J.NewClass) {
                    return identifier;
                }

                // Discard if identifier not top-level of FieldAccess
                // Since we will keep the top-level (target) of FieldAccess, no need to have a separate visitor
                // for FieldAccess. We will just check it here if it targets our instance object class.
                if (parent.getValue() instanceof JLeftPadded) {
                    Cursor parent2 = parent.getParent();
                    if (parent2 != null && parent2.getValue() instanceof J.FieldAccess) {
                        return identifier;
                    }
                }

            }

            JavaType.Variable fieldType = identifier.getFieldType();
            if (fieldType != null) {
                // Check if access to instance (also this or super) of non-static field.
                // Since we are not in a nested FieldAccess, it can only be to our own class.
                // Thus, no need to check FQN of class.
                if (fieldType.getOwner() instanceof JavaType.Class) {
                    if (!fieldType.hasFlags(Flag.Static)) {
                        hasInstanceAccess.set(true);
                    }
                }

                // Owner can also be a method (it is a parameter).
                // No need to check it, since it can never be instance access.
            }

            return identifier;
        }

        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, AtomicBoolean hasInstanceAccess) {
            // Return quickly if we already found an instance access before
            if (hasInstanceAccess.get()) {
                return mi;
            }

            J.MethodInvocation methodInvocation = super.visitMethodInvocation(mi, hasInstanceAccess);

            // Discard if the target of the invocation is not implicit this.
            // We are taking care of explicit this in visitIdentifier
            // We do not care about method invocation on other objects
            if (methodInvocation.getSelect() != null) {
                return methodInvocation;
            }

            // Check if it is a method call to non-static member
            JavaType.Method methodType = methodInvocation.getMethodType();
            if (methodType != null && !methodType.hasFlags(Flag.Static)) {
                hasInstanceAccess.set(true);
            }

            return methodInvocation;
        }

        @Override
        public J.NewClass visitNewClass(J.NewClass nc, AtomicBoolean hasInstanceAccess) {
            // Return quickly if we already found an instance access before
            if (hasInstanceAccess.get()) {
                return nc;
            }

            J.NewClass newClass = super.visitNewClass(nc, hasInstanceAccess);

            // Discard if we have an enclosing
            // We are taking care of explicit this in visitIdentifier
            // We do not care about instantiation on other objects
            if (newClass.getEnclosing() != null) {
                return newClass;
            }

            // Check if class reference to non-static nested class.
            // No need to check owning class, since we already discarded NewClass with enclosing.
            // We can only be referring to a nested class of our own class.
            JavaType.Class clazz = (JavaType.Class) newClass.getType();
            if (clazz != null && clazz.getOwningClass() != null) {
                if (!clazz.hasFlags(Flag.Static)) {
                    hasInstanceAccess.set(true);
                }
            }

            return newClass;
        }

    }

}
