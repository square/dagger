/*
 * Copyright (C) 2014 Google, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dagger.internal.codegen;

import com.google.auto.common.AnnotationMirrors;
import com.google.auto.common.MoreElements;
import com.google.auto.common.MoreTypes;
import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import dagger.Component;
import dagger.MapKey;
import dagger.Module;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;

import static com.google.auto.common.AnnotationMirrors.getAnnotationValue;
import static com.google.auto.common.MoreElements.getAnnotationMirror;
import static com.google.common.base.Preconditions.checkNotNull;
/**
 * Utility methods related to dagger configuration annotations (e.g.: {@link Component}
 * and {@link Module}).
 *
 * @author Gregory Kick
 */
final class ConfigurationAnnotations {
  private static final String MODULES_ATTRIBUTE = "modules";

  static ImmutableList<TypeMirror> getComponentModules(AnnotationMirror componentAnnotation) {
    checkNotNull(componentAnnotation);
    return convertClassArrayToListOfTypes(componentAnnotation, MODULES_ATTRIBUTE);
  }

  private static final String DEPENDENCIES_ATTRIBUTE = "dependencies";

  static ImmutableList<TypeMirror> getComponentDependencies(AnnotationMirror componentAnnotation) {
    checkNotNull(componentAnnotation);
    return convertClassArrayToListOfTypes(componentAnnotation, DEPENDENCIES_ATTRIBUTE);
  }

  private static final String INCLUDES_ATTRIBUTE = "includes";

  static ImmutableList<TypeMirror> getModuleIncludes(AnnotationMirror moduleAnnotation) {
    checkNotNull(moduleAnnotation);
    return convertClassArrayToListOfTypes(moduleAnnotation, INCLUDES_ATTRIBUTE);
  }

  private static final String INJECTS_ATTRIBUTE = "injects";

  static ImmutableList<TypeMirror> getModuleInjects(AnnotationMirror moduleAnnotation) {
    checkNotNull(moduleAnnotation);
    return convertClassArrayToListOfTypes(moduleAnnotation, INJECTS_ATTRIBUTE);
  }

  static ImmutableSet<? extends AnnotationMirror> getMapKeys(Element element) {
    return AnnotationMirrors.getAnnotatedAnnotations(element, MapKey.class);
  }

  static ImmutableList<TypeMirror> convertClassArrayToListOfTypes(
      AnnotationMirror annotationMirror, final String elementName) {
    @SuppressWarnings("unchecked") // that's the whole point of this method
    List<? extends AnnotationValue> listValue = (List<? extends AnnotationValue>)
        getAnnotationValue(annotationMirror, elementName).getValue();
    return FluentIterable.from(listValue).transform(new Function<AnnotationValue, TypeMirror>() {
      @Override public TypeMirror apply(AnnotationValue typeValue) {
        return (TypeMirror) typeValue.getValue();
      }
    }).toList();
  }

  /**
   * Returns the full set of modules transitively {@linkplain Module#includes included} from the
   * given seed modules.  If a module is malformed and a type listed in {@link Module#includes}
   * is not annotated with {@link Module}, it is ignored.
   */
  static ImmutableMap<TypeElement, ImmutableSet<TypeElement>> getTransitiveModules(
      Types types, Elements elements, ImmutableSet<TypeElement> seedModules) {
    TypeMirror objectType = elements.getTypeElement(Object.class.getCanonicalName()).asType();
    Queue<TypeElement> moduleQueue = Queues.newArrayDeque(seedModules);
    Map<TypeElement, ImmutableSet<TypeElement>> moduleElements = Maps.newLinkedHashMap();
    for (TypeElement moduleElement = moduleQueue.poll();
        moduleElement != null;
        moduleElement = moduleQueue.poll()) {
      Optional<AnnotationMirror> moduleMirror = getAnnotationMirror(moduleElement, Module.class);
      if (moduleMirror.isPresent()) {
        ImmutableSet.Builder<TypeElement> moduleDependenciesBuilder = ImmutableSet.builder();
        moduleDependenciesBuilder.addAll(
            MoreTypes.asTypeElements(types, getModuleIncludes(moduleMirror.get())));
        // (note: we don't recurse on the parent class because we don't want the parent class as a
        // root that the component depends on, and also because we want the dependencies rooted
        // against this element, not the parent.)
        addIncludesFromSuperclasses(types, moduleElement, moduleDependenciesBuilder, objectType);
        ImmutableSet<TypeElement> moduleDependencies = moduleDependenciesBuilder.build();
        moduleElements.put(moduleElement, moduleDependencies);
        for (TypeElement dependencyType : moduleDependencies) {
          if (!moduleElements.containsKey(dependencyType)) {
            moduleQueue.add(dependencyType);
          }
        }
      }
    }
    return ImmutableMap.copyOf(moduleElements);
  }
  
  /** Traverses includes from superclasses and adds them into the builder. */
  private static void addIncludesFromSuperclasses(Types types, TypeElement element,
      ImmutableSet.Builder<TypeElement> builder, TypeMirror objectType) {
    // Also add the superclass to the queue, in case any @Module definitions were on that.
    TypeMirror superclass = element.getSuperclass();
    while(!types.isSameType(objectType, superclass)
        && superclass.getKind().equals(TypeKind.DECLARED)) {
      element = MoreElements.asType(types.asElement(superclass));
      Optional<AnnotationMirror> moduleMirror = getAnnotationMirror(element, Module.class);
      if (moduleMirror.isPresent()) {
        builder.addAll(MoreTypes.asTypeElements(types, getModuleIncludes(moduleMirror.get())));
      }
      superclass = element.getSuperclass();
    }
  }

  private ConfigurationAnnotations() {}
}
