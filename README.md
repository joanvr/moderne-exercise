# moderne.io exercise

## The exercise
The purpose of this exercise is to implement a recipe with the OpenRewrite library that 
automatically fixes the code smell of this Sonar Rule: https://rules.sonarsource.com/java/RSPEC-2325

The sonar rule says that `private` and `final` methods that don't access instace data should be `static`.
The rule only apply to `private` and `final` because they cannot be overriden, so we can safely add the 
`static` modifier without breaking child classes that extend our class and may override the methods.
The idea about adding the `static` modifier is that it makes clear in the contract of the method that it 
is not using any instance data, thus avoiding any misunderstanding.

## Analysis of the rule

First of all, we can start to identify to which methods we can apply this rule and which ones we can skip.

Methods with `public`, `protected` or `default` (package visibility) are out of the scope, because those could
be overriden in child classes, and we cannot change the interface of our class. We can also skip constructors,
because it makes no sense to have a static constructor. Finally, there is an exception on some methods if
our class implements `java.io.Serializable`, that some methods cannot be static even if they do not access
instance data.

Then, we are only left with `private` or `final` methods, the non-overridable ones. Let's dig deep into those,
to analyze the different scenarios were we cannot apply the recipe:

- Usage of `this` or `super`. This is the most obvious one. Does not matter what we do afterwards, but if we use
`this` or `super` we are definitely accessing the instance object. We cannot apply the recipe there, because
those identifiers cannot be accessed from an `static` method, and would break the code.
- Access to instance variables or method invocations with implicit `this`. This one seems pretty straightforward
too. Even though we are not using the `this` keyword, it is always implicit for calls or access to our own 
(and the inherited ones) instance variables and methods. So we will need to evaluate if the variable identifier
or method invocation refers to our own instance object. In this case, we would not be able to apply the recipe.
- Instantiation of Inner Classes. In Java we can have nested classes (a class within a class). We have different types of nested
classes: Non-Static Nested Classes (also called Inner Classes) and Static Nested Classes. 
The later ones does not cause any problem, because they cannot access the parent class, and are instantiated on
their own. But the first ones can access the parent data, and actually to create them we need an instance of the 
parent class, since they can only exist in the context of the parent class. Sometimes this is not that obvious
when instantiating them inside the class, because we are taking advantage of the implicit `this`, and the apparently
harmless `new A()` is actually the not so common `this.new A()`. Since we are actually using the instance object, we
cannot apply the recipe.
- Anonymous Classes of Inner Classes. If we create an anonymous class in our method that "extends" from a inner class
we have the same issue reviewed in the previous scenario. Since we are extending the Inner Class, we still need
the instance object to instantiate the anonymous class. Then, we cannoy apply our recipe.

There are some other scenarios that are we will encounter, but those are safe to apply the recipe:
- The method only has access to the method parameters. Then it's safe to add the `static` modifier, so it makes
clear on the contract of the method that no instance data is being used.
- The method only has access to static class fields or methods. The access to static fields or methods of our own
class it's not a problem, because they live in the singleton object of the class definition, and not in the
instance object. So we can safely add the `static` modifier to our method too.
- Instantiation of Static Nested Classes. Previously we saw how we cannot apply the recipe on Inner Classes, but
with Static Nested Classes we do not have this problem at all, since they do not have any access to the instance
object and can be created on their own.
- Instantiation of Method-Local Inner Class. There is an extra type of nested classes. We can also have class
definitions inside methods. However, those definitions are local to the scope of the method and cannot be used
outside of it. So, by definition they are like Static Nested Classes and do not cause any problem for our recipe.
- Anonymous class from Nested Interface. There is a very specific scenario with instantiation of anonymous 
classes, where the parent class is an interface. By definition, nested interfaces are always static (like static
nested classes). In general, interfaces only define public method signatures; an interface that implementing 
classes have to implement. However, since Java 8 they can also have default and static method implementations, 
but neither of those cannot have access to instance data of the enclosing or implementing class.
- Method references that do not use `this::` are safe, because they are referring to the method either with static
context or to a local parameter or variable. We also need to care about the special case where `::new` is being
referenced, and treat it as instantiation of nested classes.

Method Invocations of non-static private or final methods, are kinda tricky, because those can potentially become
static due to the recipe, but they might not if they have any instance access. We will need to take care of those
in a particular way if we want to be able to support recursive calls, cross-recursive or chained method calls.

## Writing Tests

A very good approach to develop a new recipe, is first to write all the needed tests to check the scenarios we
found on our previous analysis. We grouped them in two main blocks: `Modify` and `NotModify`, to split them by 
when we can apply our recipe or when we cannot.

Then, we have grouped them by "similar scenario", just to have some kind of organized tests.

We also added some tests that are a bit more dependent on the implementation of the solution, to make sure we
were capturing and analyzing the right scenarios and scopes.

Please, check the test file to find all the implemented tests.


## Writing the recipe

With the tests created, it's now time to create the recipe.

First we will have a visitor that selects the methods on which we can potentially apply the recipe:
non-overridable methods. 

Later on, we will invoke a second visitor on the body of those methods, to try to find any instance data
access. This second visitor will (mostly) analyze identifiers. 

On this second visitor, we decided to implement visitIdentifier, visitMethodInvocation, visitNewClass and visitMethodReference,
instead of using only visitIdentifier and navigating the LST with cursors from there. This way 
we have shorter methods, less nested conditionals, and easier to understand and follow code. 
I truly believe it produces a neater approach.

There is a special scenario, regarding method invocations that we need to consider: Invocations to non-static, 
private or final methods, are on the limbo. Those calls are actually instance access, but due to the recipe, 
some of those methods can become static, and thus not be a problem. That's the reason why this second visitor, 
will also store all those invocations in the returned data, so we can analise them in a global way on our first visitor,
instead of trying to solve them on the scope of each method.

Later on, on the first visitor, we will check all potential to become static methods, if all of their calls to
private or final methods are actually on the list of methods to turn static. Otherwise, we will remove them from
the list of potential candidates, and iterate again until we are done.

Please, take a look at the source code of the recipe for further details. I added a lot of comments to
make it easier to understand my approach and the line of thought of the solution.


## Final thoughts

It was a very interesting and fun exercise to do. I really enjoyed exploring all the corner cases (I hope
I did not miss a lot of them!), and exploring the classes of OpenRewrite. It would have been nicer to have
much more detailed JavaDoc or proper detailed examples of all the methods, but this forced me to read a lot
of recipes and helped me to get familiar with the library.

Thank you very much for the opportunity (and the time and feedback provided during the proccess) and hope you 
like my solution as much as I enjoyed working on it!