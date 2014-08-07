package dagger.internal.codegen;

import com.google.auto.common.MoreTypes;
import com.google.auto.value.AutoValue;
import com.google.common.base.Equivalence;
import com.google.common.collect.ImmutableList;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

import static com.google.common.base.Preconditions.checkNotNull;

@AutoValue
abstract class MethodSignature {
  abstract String name();
  abstract ImmutableList<Equivalence.Wrapper<TypeMirror>> parameterTypes();
  abstract ImmutableList<Equivalence.Wrapper<TypeMirror>> thrownTypes();

  static MethodSignature fromExecutableElement(ExecutableElement method) {
    checkNotNull(method);
    ImmutableList.Builder<Equivalence.Wrapper<TypeMirror>> parameters = ImmutableList.builder();
    ImmutableList.Builder<Equivalence.Wrapper<TypeMirror>> thrownTypes = ImmutableList.builder();
    for (VariableElement parameter : method.getParameters()) {
      parameters.add(MoreTypes.equivalence().wrap(parameter.asType()));
    }
    for (TypeMirror thrownType : method.getThrownTypes()) {
      thrownTypes.add(MoreTypes.equivalence().wrap(thrownType));
    }
    return new AutoValue_MethodSignature(
        method.getSimpleName().toString(),
        parameters.build(),
        thrownTypes.build());
  }
}
