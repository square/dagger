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

import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;
import static dagger.internal.codegen.ErrorMessages.PROVIDES_METHOD_ABSTRACT;
import static dagger.internal.codegen.ErrorMessages.PROVIDES_METHOD_MUST_RETURN_A_VALUE;
import static dagger.internal.codegen.ErrorMessages.PROVIDES_METHOD_NOT_IN_MODULE;
import static dagger.internal.codegen.ErrorMessages.PROVIDES_METHOD_PRIVATE;
import static dagger.internal.codegen.ErrorMessages.PROVIDES_METHOD_RETURN_TYPE;
import static dagger.internal.codegen.ErrorMessages.PROVIDES_METHOD_SET_VALUES_RAW_SET;
import static dagger.internal.codegen.ErrorMessages.PROVIDES_METHOD_SET_VALUES_RETURN_SET;
import static dagger.internal.codegen.ErrorMessages.PROVIDES_METHOD_STATIC;
import static dagger.internal.codegen.ErrorMessages.PROVIDES_METHOD_TYPE_PARAMETER;
import static org.truth0.Truth.ASSERT;

import com.google.common.collect.ImmutableSet;
import com.google.testing.compile.JavaFileObjects;

import dagger.Provides;

import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.Processor;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.ElementFilter;
import javax.tools.JavaFileObject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class ProvidesMethodValidatorTest {
  @Test public void validate_notInModule() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Provides;",
        "",
        "final class TestModule {",
        "  @Provides String provideString() {",
        "    return \"\";",
        "  }",
        "}");
    ASSERT.about(javaSource()).that(moduleFile)
        .processedWith(validationProcessor())
        .failsToCompile()
        .withErrorContaining(PROVIDES_METHOD_NOT_IN_MODULE);
  }

  @Test public void validate_abstract() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "abstract class TestModule {",
        "  @Provides abstract String provideString();",
        "}");
    ASSERT.about(javaSource()).that(moduleFile)
        .processedWith(validationProcessor())
        .failsToCompile()
        .withErrorContaining(PROVIDES_METHOD_ABSTRACT);
  }

  @Test public void validate_private() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides private String provideString() {",
        "    return \"\";",
        "  }",
        "}");
    ASSERT.about(javaSource()).that(moduleFile)
        .processedWith(validationProcessor())
        .failsToCompile()
        .withErrorContaining(PROVIDES_METHOD_PRIVATE);
  }

  @Test public void validate_static() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides static String provideString() {",
        "    return \"\";",
        "  }",
        "}");
    ASSERT.about(javaSource()).that(moduleFile)
        .processedWith(validationProcessor())
        .failsToCompile()
        .withErrorContaining(PROVIDES_METHOD_STATIC);
  }

  @Test public void validate_void() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides void provideNothing() {}",
        "}");
    ASSERT.about(javaSource()).that(moduleFile)
        .processedWith(validationProcessor())
        .failsToCompile()
        .withErrorContaining(PROVIDES_METHOD_MUST_RETURN_A_VALUE);
  }

  @Test public void validate_typeParameter() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides <T> String provideString() {",
        "    return \"\";",
        "  }",
        "}");
    ASSERT.about(javaSource()).that(moduleFile)
        .processedWith(validationProcessor())
        .failsToCompile()
        .withErrorContaining(PROVIDES_METHOD_TYPE_PARAMETER);
  }

  @Test public void validate_setValuesWildcard() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import static dagger.Provides.Type.SET_VALUES;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "import java.util.Set;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides(type = SET_VALUES) Set<?> provideWildcard() {",
        "    return null;",
        "  }",
        "}");
    ASSERT.about(javaSource()).that(moduleFile)
        .processedWith(validationProcessor())
        .failsToCompile()
        .withErrorContaining(PROVIDES_METHOD_RETURN_TYPE);
  }

  @Test public void validate_setValuesRawSet() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import static dagger.Provides.Type.SET_VALUES;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "import java.util.Set;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides(type = SET_VALUES) Set provideSomething() {",
        "    return null;",
        "  }",
        "}");
    ASSERT.about(javaSource()).that(moduleFile)
        .processedWith(validationProcessor())
        .failsToCompile()
        .withErrorContaining(PROVIDES_METHOD_SET_VALUES_RAW_SET);
  }

  @Test public void validate_setValuesNotASet() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import static dagger.Provides.Type.SET_VALUES;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "import java.util.List;",
        "",
        "@Module",
        "final class TestModule {",
        "  @Provides(type = SET_VALUES) List<String> provideStrings() {",
        "    return null;",
        "  }",
        "}");
    ASSERT.about(javaSource()).that(moduleFile)
        .processedWith(validationProcessor())
        .failsToCompile()
        .withErrorContaining(PROVIDES_METHOD_SET_VALUES_RETURN_SET);
  }

  private Processor validationProcessor() {
    return new AbstractProcessor() {
      ProvidesMethodValidator validator;

      @Override
      public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.validator = new ProvidesMethodValidator(processingEnv.getElementUtils());
      }

      @Override public Set<String> getSupportedAnnotationTypes() {
        return ImmutableSet.of(Provides.class.getName());
      }

      @Override
      public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (ExecutableElement element
            : ElementFilter.methodsIn(roundEnv.getElementsAnnotatedWith(Provides.class))) {
          validator.validate(element).printMessagesTo(processingEnv.getMessager());
        }
        return false;
      }
    };
  }
}
