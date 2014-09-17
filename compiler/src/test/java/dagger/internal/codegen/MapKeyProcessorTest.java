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

import com.google.auto.value.processor.AutoAnnotationProcessor;
import com.google.common.collect.ImmutableList;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assert_;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;

@RunWith(JUnit4.class)
public class MapKeyProcessorTest {
  @Test
  public void mapKeyCreatorFile() {
    JavaFileObject enumKeyFile = JavaFileObjects.forSourceLines("test.PathKey",
        "package test;",
        "import dagger.MapKey;",
        "import java.lang.annotation.Retention;",
        "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
        "",
        "@MapKey(unwrapValue = false)",
        "@Retention(RUNTIME)",
        "public @interface PathKey {",
        "  PathEnum value();",
        "  String relativePath() default \"Defaultpath\";",
        "}");
    JavaFileObject pathEnumFile = JavaFileObjects.forSourceLines("test.PathEnum",
        "package test;",
        "",
        "public enum PathEnum {",
        "    ADMIN,",
        "    LOGIN;",
        "}");
    JavaFileObject generatedKeyCreator = JavaFileObjects.forSourceLines("test.PathKeyCreator",
        "package test;",
        "",
        "import javax.annotation.Generated;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public class PathKeyCreator {",
        "  @com.google.auto.value.AutoAnnotation",
        "  public static PathKey create(PathEnum value, String relativePath) { ",
        "    return new AutoAnnotation_PathKeyCreator_create(value, relativePath);",
        "  }",
        "}");
    assert_().about(javaSources())
    .that(ImmutableList.of(
        enumKeyFile,
        pathEnumFile))
    .processedWith(new ComponentProcessor(), new AutoAnnotationProcessor())
    .compilesWithoutError()
    .and()
    .generatesSources(generatedKeyCreator);
  }

  @Test
  public void mapKeyComponentFileWithDisorderedKeyField() {
    JavaFileObject mapModuleOneFile = JavaFileObjects.forSourceLines("test.MapModuleOne",
        "package test;",
        "",
        "import static dagger.Provides.Type.MAP;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class MapModuleOne {",
        "  @Provides(type = MAP) @PathKey(relativePath = \"AdminPath\", value = PathEnum.ADMIN)",
        "      Handler provideAdminHandler() {",
        "    return new AdminHandler(); ",
        "  }",
        "}");
    JavaFileObject mapModuleTwoFile =JavaFileObjects.forSourceLines("test.MapModuleTwo",
        "package test;",
        "",
        "import static dagger.Provides.Type.MAP;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class MapModuleTwo {",
        "  @Provides(type = MAP) @PathKey(value = PathEnum.LOGIN, relativePath = \"LoginPath\")",
        "      Handler provideLoginHandler() {",
        "    return new LoginHandler(); ",
        "  }",
        "}");
    JavaFileObject enumKeyFile = JavaFileObjects.forSourceLines("test.PathKey",
        "package test;",
        "import dagger.MapKey;",
        "import java.lang.annotation.Retention;",
        "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
        "",
        "@MapKey(unwrapValue = false)",
        "@Retention(RUNTIME)",
        "public @interface PathKey {",
        "  PathEnum value();",
        "  String relativePath() default \"DefaultPath\";",
        "}");
    JavaFileObject pathEnumFile = JavaFileObjects.forSourceLines("test.PathEnum",
        "package test;",
        "",
        "public enum PathEnum {",
        "    ADMIN,",
        "    LOGIN;",
        "}");
    JavaFileObject handlerFile = JavaFileObjects.forSourceLines("test.Handler",
        "package test;",
        "",
        "interface Handler {}");
    JavaFileObject loginHandlerFile = JavaFileObjects.forSourceLines("test.LoginHandler",
        "package test;",
        "",
        "class LoginHandler implements Handler {",
        "  public LoginHandler() {}",
        "}");
    JavaFileObject adminHandlerFile = JavaFileObjects.forSourceLines("test.AdminHandler",
        "package test;",
        "",
        "class AdminHandler implements Handler {",
        "  public AdminHandler() {}",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import java.util.Map;",
        "import javax.inject.Provider;",
        "",
        "@Component(modules = {MapModuleOne.class, MapModuleTwo.class})",
        "interface TestComponent {",
        "  Map<PathKey, Provider<Handler>> dispatcher();",
        "}");
    JavaFileObject generatedComponent = JavaFileObjects.forSourceLines("test.Dagger_TestComponent",
        "package test;",
        "",
        "import dagger.internal.MapProviderFactory;",
        "import java.util.Map;",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class Dagger_TestComponent implements TestComponent {",
        "  private final MapModuleOne mapModuleOne;",
        "  private final MapModuleTwo mapModuleTwo;",
        "  private final Provider<Map<PathKey, Provider<Handler>>> ",
        "      mapOfPathKeyAndProviderOfHandlerProvider;",
        "",
        "  private Dagger_TestComponent(Builder builder) {",
        "    assert builder != null;",
        "    this.mapModuleOne = builder.mapModuleOne;",
        "    this.mapModuleTwo = builder.mapModuleTwo;",
        "    this.mapOfPathKeyAndProviderOfHandlerProvider = ",
        "        MapProviderFactory.<PathKey, Handler>builder(2)",
        "            .put(PathKeyCreator.create(PathEnum.ADMIN, \"AdminPath\"),",
        "                 new MapModuleOne$$ProvideAdminHandlerFactory(mapModuleOne))",
        "            .put(PathKeyCreator.create(PathEnum.LOGIN, \"LoginPath\"),",
        "                 new MapModuleTwo$$ProvideLoginHandlerFactory(mapModuleTwo))",
        "            .build();",
        "  }",
        "",
        "  public static Builder builder() {",
        "    return new Builder();",
        "  }",
        "",
        "  public static TestComponent create() {",
        "    return builder().build();",
        "  }",
        "",
        "  @Override",
        "  public Map<PathKey, Provider<Handler>> dispatcher() {",
        "    return mapOfPathKeyAndProviderOfHandlerProvider.get();",
        "  }",
        "",
        "  public static final class Builder {",
        "    private MapModuleOne mapModuleOne;",
        "    private MapModuleTwo mapModuleTwo;",
        "",
        "    private Builder() {",
        "    }",
        "",
        "    public TestComponent build() {",
        "      if (mapModuleOne == null) {",
        "        this.mapModuleOne = new MapModuleOne();",
        "      }",
        "      if (mapModuleTwo == null) {",
        "        this.mapModuleTwo = new MapModuleTwo();",
        "      }",
        "      return new Dagger_TestComponent(this);",
        "    }",
        "",
        "    public Builder mapModuleOne(MapModuleOne mapModuleOne) {",
        "      if (mapModuleOne == null) {",
        "        throw new NullPointerException(\"mapModuleOne\");",
        "      }",
        "      this.mapModuleOne = mapModuleOne;",
        "      return this;",
        "    }",
        "",
        "    public Builder mapModuleTwo(MapModuleTwo mapModuleTwo) {",
        "      if (mapModuleTwo == null) {",
        "        throw new NullPointerException(\"mapModuleTwo\");",
        "      }",
        "      this.mapModuleTwo = mapModuleTwo;",
        "      return this;",
        "    }",
        "  }",
        "}",
        "");
    assert_().about(javaSources())
        .that(ImmutableList.of(
            mapModuleOneFile,
            mapModuleTwoFile,
            enumKeyFile,
            pathEnumFile,
            handlerFile,
            loginHandlerFile,
            adminHandlerFile,
            componentFile))
        .processedWith(new ComponentProcessor(), new AutoAnnotationProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(generatedComponent);
  }

  @Test
  public void mapKeyComponentFileWithDefaultField() {
    JavaFileObject mapModuleOneFile = JavaFileObjects.forSourceLines("test.MapModuleOne",
        "package test;",
        "",
        "import static dagger.Provides.Type.MAP;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class MapModuleOne {",
        "  @Provides(type = MAP) @PathKey(value = PathEnum.ADMIN) Handler provideAdminHandler() {",
        "    return new AdminHandler(); ",
        "  }",
        "}");
    JavaFileObject mapModuleTwoFile =JavaFileObjects.forSourceLines("test.MapModuleTwo",
        "package test;",
        "",
        "import static dagger.Provides.Type.MAP;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module",
        "final class MapModuleTwo {",
        "  @Provides(type = MAP) @PathKey(value = PathEnum.LOGIN, relativePath = \"LoginPath\")",
        "      Handler provideLoginHandler() {",
        "    return new LoginHandler(); ",
        "  }",
        "}");
    JavaFileObject enumKeyFile = JavaFileObjects.forSourceLines("test.PathKey",
        "package test;",
        "import dagger.MapKey;",
        "import java.lang.annotation.Retention;",
        "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
        "",
        "@MapKey(unwrapValue = false)",
        "@Retention(RUNTIME)",
        "public @interface PathKey {",
        "  PathEnum value();",
        "  String relativePath() default \"DefaultPath\";",
        "}");
    JavaFileObject pathEnumFile = JavaFileObjects.forSourceLines("test.PathEnum",
        "package test;",
        "",
        "public enum PathEnum {",
        "    ADMIN,",
        "    LOGIN;",
        "}");
    JavaFileObject handlerFile = JavaFileObjects.forSourceLines("test.Handler",
        "package test;",
        "",
        "interface Handler {}");
    JavaFileObject loginHandlerFile = JavaFileObjects.forSourceLines("test.LoginHandler",
        "package test;",
        "",
        "class LoginHandler implements Handler {",
        "  public LoginHandler() {}",
        "}");
    JavaFileObject adminHandlerFile = JavaFileObjects.forSourceLines("test.AdminHandler",
        "package test;",
        "",
        "class AdminHandler implements Handler {",
        "  public AdminHandler() {}",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "import java.util.Map;",
        "import javax.inject.Provider;",
        "",
        "@Component(modules = {MapModuleOne.class, MapModuleTwo.class})",
        "interface TestComponent {",
        "  Map<PathKey, Provider<Handler>> dispatcher();",
        "}");
    JavaFileObject generatedComponent = JavaFileObjects.forSourceLines("test.Dagger_TestComponent",
        "package test;",
        "",
        "import dagger.internal.MapProviderFactory;",
        "import java.util.Map;",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class Dagger_TestComponent implements TestComponent {",
        "  private final MapModuleOne mapModuleOne;",
        "  private final MapModuleTwo mapModuleTwo;",
        "  private final Provider<Map<PathKey, Provider<Handler>>> ",
        "      mapOfPathKeyAndProviderOfHandlerProvider;",
        "",
        "  private Dagger_TestComponent(Builder builder) {",
        "    assert builder != null;",
        "    this.mapModuleOne = builder.mapModuleOne;",
        "    this.mapModuleTwo = builder.mapModuleTwo;",
        "    this.mapOfPathKeyAndProviderOfHandlerProvider = ",
        "        MapProviderFactory.<PathKey, Handler>builder(2)",
        "            .put(PathKeyCreator.create(PathEnum.ADMIN, \"DefaultPath\"),",
        "                 new MapModuleOne$$ProvideAdminHandlerFactory(mapModuleOne))",
        "            .put(PathKeyCreator.create(PathEnum.LOGIN, \"LoginPath\"),",
        "                 new MapModuleTwo$$ProvideLoginHandlerFactory(mapModuleTwo))",
        "            .build();",
        "  }",
        "",
        "  public static Builder builder() {",
        "    return new Builder();",
        "  }",
        "",
        "  public static TestComponent create() {",
        "    return builder().build();",
        "  }",
        "",
        "  @Override",
        "  public Map<PathKey, Provider<Handler>> dispatcher() {",
        "    return mapOfPathKeyAndProviderOfHandlerProvider.get();",
        "  }",
        "",
        "  public static final class Builder {",
        "    private MapModuleOne mapModuleOne;",
        "    private MapModuleTwo mapModuleTwo;",
        "",
        "    private Builder() {",
        "    }",
        "",
        "    public TestComponent build() {",
        "      if (mapModuleOne == null) {",
        "        this.mapModuleOne = new MapModuleOne();",
        "      }",
        "      if (mapModuleTwo == null) {",
        "        this.mapModuleTwo = new MapModuleTwo();",
        "      }",
        "      return new Dagger_TestComponent(this);",
        "    }",
        "",
        "    public Builder mapModuleOne(MapModuleOne mapModuleOne) {",
        "      if (mapModuleOne == null) {",
        "        throw new NullPointerException(\"mapModuleOne\");",
        "      }",
        "      this.mapModuleOne = mapModuleOne;",
        "      return this;",
        "    }",
        "",
        "    public Builder mapModuleTwo(MapModuleTwo mapModuleTwo) {",
        "      if (mapModuleTwo == null) {",
        "        throw new NullPointerException(\"mapModuleTwo\");",
        "      }",
        "      this.mapModuleTwo = mapModuleTwo;",
        "      return this;",
        "    }",
        "  }",
        "}",
        "");
    assert_().about(javaSources())
        .that(ImmutableList.of(
            mapModuleOneFile,
            mapModuleTwoFile,
            enumKeyFile,
            pathEnumFile,
            handlerFile,
            loginHandlerFile,
            adminHandlerFile,
            componentFile))
        .processedWith(new ComponentProcessor(), new AutoAnnotationProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(generatedComponent);
  }
}