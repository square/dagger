/*
 * Copyright (C) 2015 The Dagger Authors.
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

import static dagger.internal.codegen.Util.ENCLOSING_TYPE_ELEMENT;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import dagger.internal.codegen.Key.HasKey;
import java.util.Set;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;

/** An object that declares or specifies a binding. */
abstract class BindingDeclaration implements HasKey {

  /**
   * The {@link Element} that declares the binding. Absent for bindings without identifying
   * declarations.
   */
  abstract Optional<Element> bindingElement();

  /**
   * The type enclosing the {@link #bindingElement()}, or {@link Optional#absent()} if {@link
   * #bindingElement()} is absent.
   */
  Optional<TypeElement> bindingTypeElement() {
    return bindingElement()
        .transform(
            new Function<Element, TypeElement>() {
              @Override
              public TypeElement apply(Element element) {
                return element.accept(ENCLOSING_TYPE_ELEMENT, null);
              }
            });
  }
  
  /**
   * The installed module class that contributed the {@link #bindingElement()}. May be a subclass of
   * the class that contains {@link #bindingElement()}. Absent if {@link #bindingElement()} is
   * absent.
   */
  abstract Optional<TypeElement> contributingModule();

  /**
   * A function that returns a singleton set containing the {@link #contributingModule()} for
   * binding declarations that have one, and an empty set for those that don't.
   */
  static final Function<BindingDeclaration, Set<TypeElement>> CONTRIBUTING_MODULE =
      new Function<BindingDeclaration, Set<TypeElement>>() {
        @Override
        public Set<TypeElement> apply(BindingDeclaration bindingDeclaration) {
          return bindingDeclaration.contributingModule().asSet();
        }
      };

  /**
   * A predicate that passes for binding declarations for which {@link #bindingElement()} is
   * present.
   */
  static final Predicate<BindingDeclaration> HAS_BINDING_ELEMENT =
      new Predicate<BindingDeclaration>() {
        @Override
        public boolean apply(BindingDeclaration bindingDeclaration) {
          return bindingDeclaration.bindingElement().isPresent();
        }
      };
}
