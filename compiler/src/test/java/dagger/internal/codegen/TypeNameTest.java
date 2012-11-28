/**
 * Copyright (C) 2012 Square, Inc.
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

import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;

import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVisitor;

import org.junit.Test;

public class TypeNameTest {
  @Test public void typeNameFromTypeMirror() throws IOException {
    TypeMirror mockTypeMirror = new TypeMirror() {
      @Override
      public TypeKind getKind() {
        return null;
      }      
      @Override
      public <R, P> R accept(TypeVisitor<R, P> v, P p) {
        return null;
      }
      @Override
      public String toString() {
        return "com.example.GenericClass<I>";
      }
    };
    assertThat(CodeGen.canonicalNameFromTypeMirror(mockTypeMirror)).isEqualTo("com.example.GenericClass");
  }
}
