package dagger.internal.codegen;

import com.google.auto.common.SuperficialValidation;
import com.google.common.annotations.GoogleInternal;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import dagger.Component;
import dagger.Module;
import dagger.ObjectGraph;
import dagger.internal.codegen.writer.ClassName;
import dagger.internal.codegen.writer.InterfaceWriter;
import dagger.internal.codegen.writer.JavaWriter;
import dagger.internal.codegen.writer.TypeNames;
import java.io.IOException;
import java.util.Set;
import javax.annotation.Generated;
import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;

import static com.google.auto.common.MoreElements.asType;
import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static dagger.internal.codegen.ConfigurationAnnotations.getModuleInjects;
import static javax.lang.model.element.Modifier.ABSTRACT;
import static javax.lang.model.element.Modifier.PUBLIC;

/**
 * A simple annotation processor that generates a {@link Component}-style interface from
 * {@link Module#injects} to be used as a migration aid in the transition from {@link ObjectGraph}
 * to components.  The generated interface is not annotated with {@link Component}, but is suitable
 * as a supertype for an interface that is annotated as such.  For example, given the following
 * modules: <pre>   {@code
 *
 *   @Module(...)
 *   final class AModule {...}
 *
 *   @Module(...)
 *   final class BModule {...}}</pre>
 *
 * <p>This processor would generate interfaces named {@code AModule$$InjectsInterface} and
 * {@code BModule$$InjectsInterface}.  Those modules could then be used to create a component that
 * looks like the following: <pre>   {@code
 *
 *   @Component(modules = {AModule.class, BModule.class})
 *   interface MyComponent extends AModule$$InjectsInterface, BModule$$InjectsInterface  {...}}
 *
 * <p>The methods on the generated interface are named with the canonical name of the type, but with
 * the periods replaced with underscores.  They are intentionally ugly.  Use this as motivation to
 * migrate to better interfaces as soon as possible.
 */
@GoogleInternal
public final class ModuleInjectsProcessor extends AbstractProcessor {
  @Override
  public Set<String> getSupportedAnnotationTypes() {
    return ImmutableSet.of(Module.class.getCanonicalName());
  }

  @Override
  public SourceVersion getSupportedSourceVersion() {
    return SourceVersion.latestSupported();
  }

  @Override
  public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
    Set<? extends Element> moduleElements = roundEnv.getElementsAnnotatedWith(Module.class);
    for (Element moduleElement : moduleElements) {
      if (SuperficialValidation.validateElement(moduleElement)) {
        ClassName moduleClassName = ClassName.fromTypeElement(asType(moduleElement));
        AnnotationMirror moduleAnnotation = getAnnotationMirror(moduleElement, Module.class).get();
        ImmutableList<TypeMirror> moduleInjects =
            getModuleInjects(processingEnv.getElementUtils(), moduleAnnotation);
        JavaWriter javaWriter = JavaWriter.inPackage(moduleClassName.packageName());
        InterfaceWriter interfaceWriter =
            javaWriter.addInterface(moduleClassName.classFileName() + "$$InjectsInterface");
        interfaceWriter.addModifiers(PUBLIC);
        interfaceWriter.annotate(Generated.class)
            .setValue(ModuleInjectsProcessor.class.getCanonicalName());

        for (TypeMirror injectType : moduleInjects) {
          // these method names are horrendous, but that's ok because these are placeholder
          // interfaces
          interfaceWriter.addMethod(injectType,
              TypeNames.forTypeMirror(injectType).toString().replace('.', '_'))
                  .addModifiers(PUBLIC, ABSTRACT);
        }
        try {
          javaWriter.file(processingEnv.getFiler(), ImmutableList.of(moduleElement));
        } catch (IOException e) {
          processingEnv.getMessager()
              .printMessage(Kind.ERROR, "Could not write file.", moduleElement);
        }
      }
    }
    return false;
  }
}
