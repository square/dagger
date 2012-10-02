// Copyright 2012 Square, Inc.
package dagger.androidmanifest;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;

/**
 * Goal which generates an ActivitiesModule for the specified {@code AndroidManifest.xml}.
 *
 * @goal generate
 * @phase generate-sources
 */
@SuppressWarnings({ "JavaDoc", "UnusedDeclaration" }) // Class and non-standard Javadoc used by Maven.
public class ModuleGeneratorMojo extends AbstractMojo {

  /**
   * The {@code AndroidManifest.xml} file.
   *
   * @parameter default-value="${project.basedir}/AndroidManifest.xml"
   * @required
   */
  private File androidManifest;

  /**
   * The {@code AndroidManifest.xml} file.
   *
   * @parameter default-value="ActivitiesModule"
   * @required
   */
  private String moduleName;

  /**
   * Location of the file.
   *
   * @parameter expression="${project.build.directory}/generated-sources/dagger"
   * @required
   */
  private File outputDirectory;

  /**
   * Maven project.
   *
   * @parameter expression="${project}"
   * @required
   * @readonly
   */
  private MavenProject project;

  public void execute() throws MojoExecutionException {
    try {
      // Attempt to generate the module from the specified manifest.
      ModuleGenerator.generate(androidManifest, moduleName, outputDirectory);
      // Add the generated source file to the compile path.
      project.addCompileSourceRoot(outputDirectory.getAbsolutePath());
    } catch (IOException e) {
      throw new MojoExecutionException("Unable to generate module.", e);
    } catch (SAXException e) {
      throw new MojoExecutionException("Unable to generate module.", e);
    } catch (ParserConfigurationException e) {
      throw new MojoExecutionException("Unable to generate module.", e);
    }
  }
}
