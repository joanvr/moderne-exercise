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
import org.openrewrite.java.tree.*;
import org.openrewrite.marker.Markers;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
            private static final List<MethodMatcher> serializableMethods = List.of(
                    new MethodMatcher("* writeObject(java.io.ObjectOutputStream)"),
                    new MethodMatcher("* readObject(java.io.ObjectInputStream)"),
                    new MethodMatcher("* readObjectNoData()")
            );


            private Set<JavaType.Method> methodsToBeStatic = new HashSet<>();

            // Helper class to hold together the method type and it's instance access data in a stream.
            static class MethodWithInstanceAccess {
                public MethodWithInstanceAccess(JavaType.Method method, InstanceAccess instanceAccess) {
                    this.method = method;
                    this.instanceAccess = instanceAccess;
                }
                public final JavaType.Method method;
                public final InstanceAccess instanceAccess;
            }
            @Override
            public J.ClassDeclaration visitClassDeclaration(J.ClassDeclaration classDecl, ExecutionContext executionContext) {
                List<J.MethodDeclaration> methods = collectNonOverridableMethods(classDecl.getBody());

                List<MethodWithInstanceAccess> noInstanceAccess =
                        enrichAndFilterWithNoInstanceAccess(methods);

                List<JavaType.Method> toModify =
                        filterNonStaticMethodInvocations(noInstanceAccess, this.methodsToBeStatic);

                this.methodsToBeStatic.addAll(toModify);

                return super.visitClassDeclaration(classDecl, executionContext);
            }

            @Override
            public J.NewClass visitNewClass(J.NewClass newClass, ExecutionContext executionContext) {
                if (newClass.getBody() != null) {
                    List<J.MethodDeclaration> methods = collectNonOverridableMethods(newClass.getBody());

                    List<MethodWithInstanceAccess> noInstanceAccess =
                            enrichAndFilterWithNoInstanceAccess(methods);

                    List<JavaType.Method> toModify =
                            filterNonStaticMethodInvocations(noInstanceAccess, this.methodsToBeStatic);

                    this.methodsToBeStatic.addAll(toModify);
                }

                return super.visitNewClass(newClass, executionContext);
            }

            private static List<J.MethodDeclaration> collectNonOverridableMethods(J.Block body) {
                return body
                        .getStatements()
                        .stream()
                        .filter(statement -> statement instanceof J.MethodDeclaration)
                        .map(J.MethodDeclaration.class::cast)
                        .filter(md -> !md.hasModifier(J.Modifier.Type.Static))
                        .filter(md -> md.hasModifier(J.Modifier.Type.Private) || md.hasModifier(J.Modifier.Type.Final))
                        .filter(md -> !md.isConstructor())
                        .filter(md -> !isSerializableException(md))
                        .collect(Collectors.toList());

            }

            private static boolean isSerializableException(J.MethodDeclaration methodDeclaration) {
                JavaType.Method method = methodDeclaration.getMethodType();
                if (method != null) {
                    List<JavaType.FullyQualified> interfaces = method.getDeclaringType().getInterfaces();
                    return interfaces.stream().anyMatch(i -> i.getFullyQualifiedName().equals("java.io.Serializable")) &&
                            serializableMethods.stream().anyMatch(matcher -> matcher.matches(method));
                }
                return false;
            }

            private static List<MethodWithInstanceAccess> enrichAndFilterWithNoInstanceAccess(List<J.MethodDeclaration> methods) {
                // Enriching with AccessInstanceDataVisitor and filtering the ones that have instance access
                return methods.stream()
                        .map(md -> new MethodWithInstanceAccess(
                                        md.getMethodType(),
                                        AccessInstanceDataVisitor.find(md.getBody())
                                )
                        )
                        .filter(mia -> !mia.instanceAccess.get())
                        .collect(Collectors.toList());
            }

            private static List<JavaType.Method> filterNonStaticMethodInvocations(
                    List<MethodWithInstanceAccess> noInstanceAccess,
                    Set<JavaType.Method> previousValidMethods
            ) {
                int prevSize;
                do {
                    // Creating a set with the current potential methods to become static.
                    Set<JavaType.Method> validMethods = noInstanceAccess
                            .stream()
                            .map(mia -> mia.method)
                            .collect(Collectors.toSet());
                    // Also adding previous methods from upper scopes
                    validMethods.addAll(previousValidMethods);

                    // We keep the previous size, to see if we actually removed any methods, and we need to iterate again
                    prevSize = noInstanceAccess.size();
                    // We remove all methods that have invocations to methods that won't become static
                    noInstanceAccess = noInstanceAccess
                            .stream()
                            .filter(mia -> validMethods.containsAll(mia.instanceAccess.methodInvocations))
                            .collect(Collectors.toList());
                } while (noInstanceAccess.size() < prevSize && noInstanceAccess.size() > 0);
                // We keep iterating if we removed some methods, to propagate the changes in invocation chains

                return noInstanceAccess.stream()
                        .map(mia -> mia.method)
                        .collect(Collectors.toList());

            }

            @Override
            public J.MethodDeclaration visitMethodDeclaration(J.MethodDeclaration methodDec, ExecutionContext executionContext) {
                J.MethodDeclaration methodDeclaration = super.visitMethodDeclaration(methodDec, executionContext);

                // All the analysis have already been done in the previous visit methods,
                // Here we just need to check the list of methods to become static and apply the modifier if we found it.
                if (this.methodsToBeStatic.contains(methodDec.getMethodType())) {
                    methodDeclaration = autoFormat(
                            methodDeclaration.withModifiers(
                                    ListUtils.concat(methodDeclaration.getModifiers(), new J.Modifier(Tree.randomId(), Space.EMPTY, Markers.EMPTY, J.Modifier.Type.Static, Collections.emptyList()))
                            ), executionContext);
                }

                return methodDeclaration;
            }
        };
    }

    // Helper class to encapsulate the returned data of the AccessInstanceDataVisitor.
    // We have a flag that starts at false, and can only be set up to true.
    // We also have a list of method invocations to non-static private or final methods, to check later on
    // Due to the short-circuit that we have on the visitor, if the flag is set to true, the list of method invocations
    // may be incomplete.
    private static class InstanceAccess {
        private boolean instanceAccess = false;
        private Set<JavaType.Method> methodInvocations = new HashSet<>();

        public void set() {
            this.instanceAccess = true;
        }

        public boolean get() {
            return this.instanceAccess;
        }
        public void addMethodInvocation(JavaType.Method method) {
            this.methodInvocations.add(method);
        }

        public Set<JavaType.Method> getMethodInvocations() {
            return this.methodInvocations;
        }
    }


    @Value
    @EqualsAndHashCode(callSuper = true)
    private static class AccessInstanceDataVisitor extends JavaIsoVisitor<InstanceAccess> {

        static InstanceAccess find(J.Block body) {
            return new AccessInstanceDataVisitor()
                    .reduce(body, new InstanceAccess());
        }


        @Override
        public J.Identifier visitIdentifier(J.Identifier id, InstanceAccess instanceAccess) {
            // Return quickly if we already found an instance access before
            if (instanceAccess.get()) {
                return id;
            }

            J.Identifier identifier = super.visitIdentifier(id, instanceAccess);

            Cursor parent = getCursor().getParent();
            if (parent != null) {
                // Discard if the identifier is a NamedVariable
                if (parent.getValue() instanceof J.VariableDeclarations.NamedVariable) {
                    return identifier;
                }

                // Discard if the identifier is a ParametrizedType
                if (parent.getValue() instanceof J.ParameterizedType) {
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
                        instanceAccess.set();
                    }
                }

                // Owner can also be a method (it is a parameter).
                // No need to check it, since it can never be instance access.
            }

            return identifier;
        }


        @Override
        public J.MethodInvocation visitMethodInvocation(J.MethodInvocation mi, InstanceAccess instanceAccess) {
            // Return quickly if we already found an instance access before
            if (instanceAccess.get()) {
                return mi;
            }

            J.MethodInvocation methodInvocation = super.visitMethodInvocation(mi, instanceAccess);

            // Discard if the target of the invocation is not implicit this.
            // We are taking care of explicit this in visitIdentifier
            // We do not care about method invocation on other objects
            if (methodInvocation.getSelect() != null) {
                return methodInvocation;
            }

            JavaType.Method methodType = methodInvocation.getMethodType();
            if (methodType != null) {
                // Check if it is a method call to non-static member
                if (!methodType.hasFlags(Flag.Static)) {
                    // If it's access to a potential to become static method, we add it to the list of method invocations
                    if (methodType.hasFlags(Flag.Private) || methodType.hasFlags(Flag.Final)) {
                        instanceAccess.addMethodInvocation(methodType);
                    } else { // Otherwise we just set instance access
                        instanceAccess.set();
                    }

                }
            }

            return methodInvocation;
        }

        @Override
        public J.NewClass visitNewClass(J.NewClass nc, InstanceAccess instanceAccess) {
            // Return quickly if we already found an instance access before
            if (instanceAccess.get()) {
                return nc;
            }

            J.NewClass newClass = super.visitNewClass(nc, instanceAccess);

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
                    instanceAccess.set();
                }
            }

            return newClass;
        }

        @Override
        public J.MemberReference visitMemberReference(J.MemberReference mr, InstanceAccess instanceAccess) {
            if (instanceAccess.get()) {
                return mr;
            }
            J.MemberReference memberRef = super.visitMemberReference(mr, instanceAccess);

            if (memberRef.getContaining() instanceof J.Identifier) {
                J.Identifier id = (J.Identifier) memberRef.getContaining();
                // On member references, we just care about the specific case where we are accessing through `this::`
                // Any other member reference is either with static context or to a local parameter or variable.
                if (id.getSimpleName().equals("this")) {
                    instanceAccess.set();
                }
                // For the special case of `new`, we need to check as in visitNewClass if it's a static nested class
                else if (memberRef.getReference().getSimpleName().equals("new")) {
                    JavaType.Class clazz = (JavaType.Class) id.getType();
                    if (clazz != null && clazz.getOwningClass() != null) {
                        if (!clazz.hasFlags(Flag.Static)) {
                            instanceAccess.set();
                        }
                    }
                }
            }

            return memberRef;
        }
    }

}
