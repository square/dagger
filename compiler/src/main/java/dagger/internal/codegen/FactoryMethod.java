package dagger.internal.codegen;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.util.List;

public class FactoryMethod {
    private final ExecutableElement method;
    private final List<Integer> transposition;
    private final TypeElement type;
    private final TypeElement factory;

    public FactoryMethod(ExecutableElement method, List<Integer> transposition,
                         TypeElement type, TypeElement factory) {
      this.method = method;
      this.transposition = transposition;
      this.type = type;
      this.factory = factory;
    }

    public List<Integer> getTransposition() {
      return transposition;
    }

    public ExecutableElement getMethod() {
      return method;
    }

    public TypeElement getType() {
      return type;
    }

    public TypeElement getFactory() {
      return factory;
    }
}