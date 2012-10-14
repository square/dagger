package dagger.testing.it;

import java.io.File;
import java.io.FileInputStream;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.Charset;

public class BuildLogValidator {

  /**
   * Processes a log file, ensuring it has all the provided strings within it.
   *
   * @param buildLogfile a log file to be searched
   * @param expectedStrings the strings that must be present in the log file for it to be valid
   */
  public void hasText(File buildLogfile, String ... expectedStrings) throws Throwable {
    String buildOutput;
    FileInputStream stream = new FileInputStream(buildLogfile);
    try {
      FileChannel fc = stream.getChannel();
      MappedByteBuffer buf = fc.map(FileChannel.MapMode.READ_ONLY, 0, fc.size());
      buildOutput = Charset.defaultCharset().decode(buf).toString();
    } finally {
      stream.close();
    }
    if (buildOutput == null) {
      throw new Exception("Could not read build output");
    }

    StringBuilder sb = new StringBuilder("Build output did not contain expected error text:");
    boolean missing = false;

    for (String expected : expectedStrings) {
      if (!buildOutput.contains(expected)) {
        missing = true;
        sb.append("\n\t\"").append(expected).append("\"");
      }
    }
    if (missing) {
      throw new Exception(sb.toString());
    }
  }

}
