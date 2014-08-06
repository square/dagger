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

import com.google.common.collect.ImmutableList;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assert_;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;

@RunWith(JUnit4.class)
public class MapBindingComponentProcessorTest {

  @Test
  public void mapBindingsWithEnumKey() {
    JavaFileObject mapModuleOneFile =
        JavaFileObjects
            .forSourceLines("test.MapModuleOne",
                "package test;",
                "",
                "import static dagger.Provides.Type.MAP;",
                "",
                "import dagger.Module;",
                "import dagger.Provides;",
                "",
                "@Module",
                "final class MapModuleOne {",
                "  @Provides(type = MAP) @PathKey(PathEnum.ADMIN) Handler provideAdminHandler() {",
                "    return new AdminHandler(); ",
                "  }",
                "}");
    JavaFileObject mapModuleTwoFile =
        JavaFileObjects
            .forSourceLines("test.MapModuleTwo",
                "package test;",
                "",
                "import static dagger.Provides.Type.MAP;",
                "",
                "import dagger.Module;",
                "import dagger.Provides;",
                "",
                "@Module",
                "final class MapModuleTwo {",
                "  @Provides(type = MAP) @PathKey(PathEnum.LOGIN) Handler provideLoginHandler() {",
                "    return new LoginHandler(); ",
                "  }",
                "}");
    JavaFileObject enumKeyFile = JavaFileObjects.forSourceLines("test.PathKey",
        "package test;",
        "import dagger.MapKey;",
        "import java.lang.annotation.Retention;",
        "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
        "",
        "@MapKey(unwrapValue = true)",
        "@Retention(RUNTIME)",
        "public @interface PathKey {",
        "  PathEnum value();",
        "}");
    JavaFileObject pathEnumFile = JavaFileObjects.forSourceLines("test.PathEnum",
        "package test;",
        "",
        "public enum PathEnum {",
        "    ADMIN,",
        "    LOGIN;",
        "}");

    JavaFileObject HandlerFile = JavaFileObjects.forSourceLines("test.Handler",
        "package test;",
        "",
        "interface Handler {}");
    JavaFileObject LoginHandlerFile = JavaFileObjects.forSourceLines("test.LoginHandler",
        "package test;",
        "",
        "class LoginHandler implements Handler {",
        "  public LoginHandler() {}",
        "}");
    JavaFileObject AdminHandlerFile = JavaFileObjects.forSourceLines("test.AdminHandler",
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
        "  Map<PathEnum, Provider<Handler>> dispatcher();",
        "}");
    JavaFileObject generatedComponent =
        JavaFileObjects
            .forSourceLines("test.Dagger_TestComponent",
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
                "  private final Provider<Map<PathEnum, Provider<Handler>>> ",
                "      mapOfPathEnumAndProviderOfHandlerProvider;",
                "",
                "  private Dagger_TestComponent(Builder builder) {",
                "    assert builder != null;",
                "    this.mapModuleOne = builder.mapModuleOne;",
                "    this.mapModuleTwo = builder.mapModuleTwo;",
                "    this.mapOfPathEnumAndProviderOfHandlerProvider = ",
                "        MapProviderFactory.<PathEnum, Handler>builder(2)",
                "            .put(test.PathEnum.ADMIN, ",
                "                new MapModuleOne$$ProvideAdminHandlerFactory(mapModuleOne))",
                "            .put(test.PathEnum.LOGIN, ",
                "                new MapModuleTwo$$ProvideLoginHandlerFactory(mapModuleTwo))",
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
                "  public Map<PathEnum, Provider<Handler>> dispatcher() {",
                "    return mapOfPathEnumAndProviderOfHandlerProvider.get();",
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
        .that(ImmutableList.of(mapModuleOneFile,
            mapModuleTwoFile,
            enumKeyFile,
            pathEnumFile,
            HandlerFile,
            LoginHandlerFile,
            AdminHandlerFile,
            componentFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(generatedComponent);
  }

  @Test
  public void mapBindingsWithStringKey() {
    JavaFileObject mapModuleOneFile =
        JavaFileObjects
            .forSourceLines("test.MapModuleOne",
                "package test;",
                "",
                "import static dagger.Provides.Type.MAP;",
                "",
                "import dagger.Module;",
                "import dagger.Provides;",
                "",
                "@Module",
                "final class MapModuleOne {",
                "  @Provides(type = MAP) @StringKey(\"Admin\") Handler provideAdminHandler() {",
                "    return new AdminHandler();",
                "  }",
                "}");
    JavaFileObject mapModuleTwoFile =
        JavaFileObjects
            .forSourceLines("test.MapModuleTwo",
                "package test;",
                "",
                "import static dagger.Provides.Type.MAP;",
                "",
                "import dagger.Module;",
                "import dagger.Provides;",
                "",
                "@Module",
                "final class MapModuleTwo {",
                "  @Provides(type = MAP) @StringKey(\"Login\") Handler provideLoginHandler() {",
                "    return new LoginHandler();",
                "  }",
                "}");
    JavaFileObject stringKeyFile = JavaFileObjects.forSourceLines("test.StringKey",
        "package test;",
        "import dagger.MapKey;",
        "import java.lang.annotation.Retention;",
        "import static java.lang.annotation.RetentionPolicy.RUNTIME;",
        "",
        "@MapKey(unwrapValue = true)",
        "@Retention(RUNTIME)",
        "public @interface StringKey {",
        "  String value();",
        "}");
    JavaFileObject HandlerFile = JavaFileObjects.forSourceLines("test.Handler",
        "package test;",
        "",
        "interface Handler {}");
    JavaFileObject LoginHandlerFile = JavaFileObjects.forSourceLines("test.LoginHandler",
        "package test;",
        "",
        "class LoginHandler implements Handler {",
        "  public LoginHandler() {}",
        "}");
    JavaFileObject AdminHandlerFile = JavaFileObjects.forSourceLines("test.AdminHandler",
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
        "  Map<String, Provider<Handler>> dispatcher();",
        "}");
    JavaFileObject generatedComponent =
        JavaFileObjects
            .forSourceLines("test.Dagger_TestComponent",
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
                "  private final Provider<Map<String, Provider<Handler>>> ",
                "      mapOfStringAndProviderOfHandlerProvider;",
                "",
                "",
                "  private Dagger_TestComponent(Builder builder) {",
                "    assert builder != null;",
                "    this.mapModuleOne = builder.mapModuleOne;",
                "    this.mapModuleTwo = builder.mapModuleTwo;",
                "    this.mapOfStringAndProviderOfHandlerProvider =",
                "        MapProviderFactory.<String, Handler>builder(2)",
                "            .put(\"Admin\", ",
                "                new MapModuleOne$$ProvideAdminHandlerFactory(mapModuleOne))",
                "            .put(\"Login\", ",
                "                new MapModuleTwo$$ProvideLoginHandlerFactory(mapModuleTwo))",
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
                "  public Map<String, Provider<Handler>> dispatcher() {",
                "    return mapOfStringAndProviderOfHandlerProvider.get();",
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
                "}");

    assert_().about(javaSources())
        .that(ImmutableList.of(mapModuleOneFile,
            mapModuleTwoFile,
            stringKeyFile,
            HandlerFile,
            LoginHandlerFile,
            AdminHandlerFile,
            componentFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and()
        .generatesSources(generatedComponent);
  }
}
