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

import com.google.auto.common.MoreElements;
import com.google.testing.compile.CompilationRule;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static org.junit.Assert.fail;
import static org.truth0.Truth.ASSERT;

@RunWith(JUnit4.class)
public class ElementUtilTest {
  @Rule public CompilationRule compilation = new CompilationRule();

  @Test public void asTypeElement() {
    Element typeElement =
        compilation.getElements().getTypeElement(String.class.getCanonicalName());
    ASSERT.that(MoreElements.asType(typeElement)).is(typeElement);
  }

  @Test public void asTypeElement_notATypeElement() {
    TypeElement typeElement =
        compilation.getElements().getTypeElement(String.class.getCanonicalName());
    for (ExecutableElement e : ElementFilter.methodsIn(typeElement.getEnclosedElements())) {
      try {
        MoreElements.asType(e);
        fail();
      } catch (IllegalArgumentException expected) {
      }
    }
  }
}
