package dagger.internal.codegen;

import com.google.common.collect.ImmutableList;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assertAbout;
import static com.google.testing.compile.JavaSourcesSubjectFactory.javaSources;

@RunWith(JUnit4.class)
public class RepeatedModuleValidationTest {
  private static final JavaFileObject MODULE_FILE =
      JavaFileObjects.forSourceLines(
          "test.TestModule",
          "package test;",
          "",
          "import dagger.Module;",
          "",
          "@Module",
          "final class TestModule {}");

  @Test
  public void moduleRepeatedInSubcomponentFactoryMethod() {
    JavaFileObject subcomponentFile =
        JavaFileObjects.forSourceLines(
            "test.TestSubcomponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = TestModule.class)",
            "interface TestSubcomponent {",
            "}");
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  TestSubcomponent newTestSubcomponent(TestModule module);",
            "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(MODULE_FILE, subcomponentFile, componentFile))
        .processedWith(new ComponentProcessor())
        .failsToCompile()
        .withErrorContaining("This module is present in test.TestComponent.")
        .in(componentFile)
        .onLine(7)
        .atColumn(51);
  }

  @Test
  public void moduleRepeatedInSubcomponentBuilderMethod() {
    JavaFileObject subcomponentFile =
        JavaFileObjects.forSourceLines(
            "test.TestSubcomponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = TestModule.class)",
            "interface TestSubcomponent {",
            "  @Subcomponent.Builder",
            "  interface Builder {",
            "    Builder testModule(TestModule testModule);",
            "    TestSubcomponent build();",
            "  }",
            "}");
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  TestSubcomponent.Builder newTestSubcomponentBuilder();",
            "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(MODULE_FILE, subcomponentFile, componentFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError();
    // TODO(gak): assert about the warning when we have that ability
  }

  @Test
  public void moduleRepeatedButNotPassed() {
    JavaFileObject subcomponentFile =
        JavaFileObjects.forSourceLines(
            "test.TestSubcomponent",
            "package test;",
            "",
            "import dagger.Subcomponent;",
            "",
            "@Subcomponent(modules = TestModule.class)",
            "interface TestSubcomponent {",
            "}");
    JavaFileObject componentFile =
        JavaFileObjects.forSourceLines(
            "test.TestComponent",
            "package test;",
            "",
            "import dagger.Component;",
            "",
            "@Component(modules = TestModule.class)",
            "interface TestComponent {",
            "  TestSubcomponent newTestSubcomponent();",
            "}");
    assertAbout(javaSources())
        .that(ImmutableList.of(MODULE_FILE, subcomponentFile, componentFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError();
  }
}
