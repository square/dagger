package dagger.androidmanifest;

import java.io.File;
import java.io.IOException;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;
import org.xml.sax.SAXException;

@SuppressWarnings("UnusedDeclaration") // Methods used implicitly by Ant.
public class ModuleGeneratorTask extends Task {
  private File manifestFile = new File("AndroidManifest.xml");
  private String moduleName = "ManifestModule";
  private File outputDirectory = new File("gen");

  public void setManifest(File manifestFile) {
    this.manifestFile = manifestFile;
  }

  public void setName(String moduleName) {
    this.moduleName = moduleName;
  }

  public void setOut(File outputDirectory) {
    this.outputDirectory = outputDirectory;
  }

  @Override public void execute() {
    try {
      ModuleGenerator.generate(manifestFile, moduleName, outputDirectory);
    } catch (IOException e) {
      throw new BuildException("Unable to generate module.", e);
    } catch (SAXException e) {
      throw new BuildException("Unable to generate module.", e);
    } catch (ParserConfigurationException e) {
      throw new BuildException("Unable to generate module.", e);
    }
  }
}
