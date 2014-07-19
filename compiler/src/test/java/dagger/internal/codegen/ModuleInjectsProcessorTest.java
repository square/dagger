package dagger.internal.codegen;

import com.google.common.annotations.GoogleInternal;
import com.google.testing.compile.JavaFileObjects;
import javax.tools.JavaFileObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import static com.google.common.truth.Truth.assert_;
import static com.google.testing.compile.JavaSourceSubjectFactory.javaSource;

@GoogleInternal
@RunWith(JUnit4.class)
public class ModuleInjectsProcessorTest {
  @Test public void singleProvidesMethodNoArgs() {
    JavaFileObject moduleFile = JavaFileObjects.forSourceLines("test.TestModule",
        "package test;",
        "",
        "import dagger.Module;",
        "import dagger.Provides;",
        "",
        "@Module(injects = {String.class, Object.class})",
        "final class TestModule {",
        "}");
    JavaFileObject interfaceFile = JavaFileObjects.forSourceLines(
        "test.TestModule$$InjectsInterface",
        "package test;",
        "",
        "import javax.annotation.Generated;",
        "",
        "@Generated(\"dagger.internal.codegen.ModuleInjectsProcessor\")",
        "public interface TestModule$$InjectsInterface {",
        "  public abstract String java_lang_String();",
        "  public abstract Object java_lang_Object();",
        "}");
    assert_().about(javaSource()).that(moduleFile)
        .processedWith(new ModuleInjectsProcessor())
        .compilesWithoutError()
        .and().generatesSources(interfaceFile);
  }
}
