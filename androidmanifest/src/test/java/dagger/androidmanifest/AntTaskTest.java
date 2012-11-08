package dagger.androidmanifest;

import java.io.File;
import java.io.IOException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.codehaus.plexus.util.FileUtils;
import org.fest.util.Files;
import org.junit.Test;

import static org.fest.assertions.Assertions.assertThat;

public class AntTaskTest {
  @Test public void antTaskCreatesModule() throws IOException {
    File baseDir = Files.newTemporaryFolder();

    // Copy build.xml and AndroidManifest.xml to temporary directory.
    File manifest = new File(getClass().getResource("/AndroidManifest.xml").getFile());
    FileUtils.copyFile(manifest, new File(baseDir, "AndroidManifest.xml"));
    File buildSrc = new File(getClass().getResource("/build.xml").getFile());
    File buildDest = new File(baseDir, "build.xml");
    FileUtils.copyFile(buildSrc, buildDest);

    // Invoke build.xml's "test" target.
    Project project = new Project();
    ProjectHelper projectHelper = ProjectHelper.getProjectHelper();
    project.setUserProperty("ant.file", buildDest.getAbsolutePath());
    project.setProperty("maven.class.dir", System.getProperty("basedir") + "/target/classes/");
    project.setProperty("temp.dir", baseDir.getAbsolutePath());
    project.init();
    project.addReference("ant.projectHelper", projectHelper);
    projectHelper.parse(project, buildDest);
    project.executeTarget("test");

    // Verify generated ManifestModule.java
    File generated = new File(baseDir, "gen");
    assertThat(generated).exists().isDirectory();
    File manifestModule = new File(generated, "com/squareup/badhorse/ManifestModule.java");
    assertThat(manifestModule).exists().isFile();
  }
}
