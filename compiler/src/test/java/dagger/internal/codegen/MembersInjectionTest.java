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
public class MembersInjectionTest {
  @Test
  public void parentClass_noInjectedMembers() {
    JavaFileObject childFile = JavaFileObjects.forSourceLines("test.Child",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "public final class Child extends Parent {",
        "  @Inject Child() {}",
        "}");
    JavaFileObject parentFile = JavaFileObjects.forSourceLines("test.Parent",
        "package test;",
        "",
        "public abstract class Parent {}");

    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "",
        "@Component",
        "interface TestComponent {",
        "  Child child();",
        "}");
    JavaFileObject generatedComponent = JavaFileObjects.forSourceLines(
        "test.Dagger_TestComponent",
        "package test;",
        "",
        "import dagger.MembersInjector;",
        "import dagger.internal.MembersInjectors;",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class Dagger_TestComponent implements TestComponent {",
        "  private MembersInjector<Parent> parentMembersInjector;",
        "  private MembersInjector<Child> childMembersInjector;",
        "  private Provider<Child> childProvider;",
        "",
        "  private Dagger_TestComponent(Builder builder) {",
        "    assert builder != null;",
        "    initialize();",
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
        "  private void initialize() {",
        "    this.parentMembersInjector = MembersInjectors.noOp();",
        "    this.childMembersInjector = MembersInjectors.delegatingTo(parentMembersInjector);",
        "    this.childProvider = new Child$$Factory(childMembersInjector);",
        "  }",
        "",
        "  @Override",
        "  public Child child() {",
        "    return childProvider.get();",
        "  }",
        "",
        "  public static final class Builder {",
        "    private Builder() {",
        "    }",
        "",
        "    public TestComponent build() {",
        "      return new Dagger_TestComponent(this);",
        "    }",
        "  }",
        "}");
    assert_().about(javaSources())
        .that(ImmutableList.of(childFile, parentFile, componentFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(generatedComponent);
  }

  @Test
  public void parentClass_injectedMembersInSupertype() {
    JavaFileObject childFile = JavaFileObjects.forSourceLines("test.Child",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "public final class Child extends Parent {",
        "  @Inject Child() {}",
        "}");
    JavaFileObject parentFile = JavaFileObjects.forSourceLines("test.Parent",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "public abstract class Parent {",
        "  @Inject Dep dep;",
        "}");
    JavaFileObject depFile = JavaFileObjects.forSourceLines("test.Dep",
        "package test;",
        "",
        "import javax.inject.Inject;",
        "",
        "final class Dep {",
        "  @Inject Dep() {}",
        "}");
    JavaFileObject componentFile = JavaFileObjects.forSourceLines("test.TestComponent",
        "package test;",
        "",
        "import dagger.Component;",
        "",
        "",
        "@Component",
        "interface TestComponent {",
        "  Child child();",
        "}");
    JavaFileObject generatedComponent = JavaFileObjects.forSourceLines(
        "test.Dagger_TestComponent",
        "package test;",
        "",
        "import dagger.MembersInjector;",
        "import dagger.internal.MembersInjectors;",
        "import javax.annotation.Generated;",
        "import javax.inject.Provider;",
        "",
        "@Generated(\"dagger.internal.codegen.ComponentProcessor\")",
        "public final class Dagger_TestComponent implements TestComponent {",
        "  private Provider<Dep> depProvider;",
        "  private MembersInjector<Parent> parentMembersInjector;",
        "  private MembersInjector<Child> childMembersInjector;",
        "  private Provider<Child> childProvider;",
        "",
        "  private Dagger_TestComponent(Builder builder) {",
        "    assert builder != null;",
        "    initialize();",
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
        "  private void initialize() {",
        "    this.depProvider = new Dep$$Factory();",
        "    this.parentMembersInjector = new Parent$$MembersInjector(depProvider);",
        "    this.childMembersInjector = MembersInjectors.delegatingTo(parentMembersInjector);",
        "    this.childProvider = new Child$$Factory(childMembersInjector);",
        "  }",
        "",
        "  @Override",
        "  public Child child() {",
        "    return childProvider.get();",
        "  }",
        "",
        "  public static final class Builder {",
        "    private Builder() {",
        "    }",
        "",
        "    public TestComponent build() {",
        "      return new Dagger_TestComponent(this);",
        "    }",
        "  }",
        "}");
    assert_().about(javaSources())
        .that(ImmutableList.of(childFile, parentFile, depFile, componentFile))
        .processedWith(new ComponentProcessor())
        .compilesWithoutError()
        .and().generatesSources(generatedComponent);
  }
}
