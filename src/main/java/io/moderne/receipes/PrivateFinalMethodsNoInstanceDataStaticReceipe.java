package io.moderne.receipes;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.openrewrite.Cursor;
import org.openrewrite.ExecutionContext;
import org.openrewrite.Recipe;
import org.openrewrite.Tree;
import org.openrewrite.internal.ListUtils;
import org.openrewrite.java.JavaIsoVisitor;
import org.openrewrite.java.TreeVisitingPrinter;
import org.openrewrite.java.tree.Flag;
import org.openrewrite.java.tree.J;
import org.openrewrite.java.tree.JavaType;
import org.openrewrite.java.tree.Space;
import org.openrewrite.marker.Markers;

import java.util.Collections;
import java.util.concurrent.atomic.AtomicBoolean;

public class PrivateFinalMethodsNoInstanceDataStaticReceipe extends Recipe {

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
            @Override
            public J.CompilationUnit visitCompilationUnit(J.CompilationUnit cu, ExecutionContext executionContext) {
                // TODO: remove the debug
                System.out.println(TreeVisitingPrinter.printTree(getCursor()));
                return super.visitCompilationUnit(cu, executionContext);
            }

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
        public J.FieldAccess visitFieldAccess(J.FieldAccess fa, AtomicBoolean hasInstanceAccess) {
            // Return quickly if we already found a instance access before
            if (hasInstanceAccess.get()) {
                return fa;
            }

            J.FieldAccess fieldAccess = super.visitFieldAccess(fa, hasInstanceAccess);

            // We just want to analyze field access whose target is an identifier.
            if (fieldAccess.getTarget() instanceof J.Identifier) {
                J.Identifier identifier = (J.Identifier) fieldAccess.getTarget();
                JavaType.Variable fieldType = identifier.getFieldType();
                // We can check with fieldType if it's referencing a field in a class
                if (fieldType != null && fieldType.getOwner() instanceof JavaType.Class) {
                    JavaType.Class clazz = (JavaType.Class) fieldType.getOwner();
                    // TODO check fqn?
                    if (!fieldType.hasFlags(Flag.Static)) {
                        hasInstanceAccess.set(true);
                    }
                }
            }

            return fa;
        }


        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation m, AtomicBoolean hasInstanceAccess) {
            // Return quickly if we already found a instance access before
            if (hasInstanceAccess.get()) {
                return m;
            }

            J.MethodInvocation method = super.visitMethodInvocation(m, hasInstanceAccess);
            // Here we only want to detect method invocations to implicit this (select==null) that are not static
            if (method.getSelect() == null) {
                JavaType.Method methodType = method.getMethodType();
                if (methodType != null && !methodType.hasFlags(Flag.Static)) {
                    hasInstanceAccess.set(true);
                }
            }
            return method;
        }

        @Override
        public J.Identifier visitIdentifier(J.Identifier id, AtomicBoolean hasInstanceAccess) {
            // Return quickly if we already found a instance access before
            if (hasInstanceAccess.get()) {
                return id;
            }
            J.Identifier identifier = super.visitIdentifier(id, hasInstanceAccess);

            // Discard if identifier is in FieldAccess or in VariableDeclarations, we just want to analyze implicit this and parameters
            // TODO check other stuff to discard?
            Cursor cursor2 = getCursor().dropParentUntil(parent ->
                    parent instanceof J.FieldAccess ||
                    parent instanceof J.VariableDeclarations ||
                    parent instanceof J.Block);
            if (!(cursor2.getValue() instanceof J.Block)) {
                return identifier;
            };


            // Check if access to instance (this or super) of non static field
            JavaType.Variable fieldType = identifier.getFieldType();
            if (fieldType != null && fieldType.getOwner() instanceof JavaType.Class) {
                if (!fieldType.hasFlags(Flag.Static)) {
                    hasInstanceAccess.set(true);
                }
            }

            return identifier;
        }

        @Override
        public J.NewClass visitNewClass(J.NewClass nClass, AtomicBoolean hasInstanceAccess) {
            //if (hasInstanceAccess.get()) {
            //    return newClass;
            //}

            J.NewClass newClass = super.visitNewClass(nClass, hasInstanceAccess);
            // We just want to analyze the implicit this for nested classes
            if (newClass.getEnclosing() == null ) {
                JavaType.Class clazz = (JavaType.Class) newClass.getType();
                // If we got owning class, it has to be our enclosing class
                if (clazz != null && clazz.getOwningClass() != null) {
                    // If it's non-static we have instance access
                    if (!clazz.hasFlags(Flag.Static)) {
                        hasInstanceAccess.set(true);
                    }
                }
            }
            return super.visitNewClass(newClass, hasInstanceAccess);
        }
    }

}
