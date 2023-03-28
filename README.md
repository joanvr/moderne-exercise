# moderne-exercise

Not apply:
- constructors
- public, package and protected visibility
- private or final with:
  - explicit access to this or super
  - access to instance variables
  - invocation to instance methods
  - creation of inner classes of the enclosing class (also anonymous)
Apply:
private or final with:
- access only to method parameters
- access only to static class fields
- access only to static class methods
- creation of static nested classes
- creation of method-local Inner Class
- creation of anonymous class from interface (always static)
