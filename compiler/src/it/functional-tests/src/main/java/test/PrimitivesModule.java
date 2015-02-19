package test;

import dagger.Module;
import dagger.Provides;

@Module
final class PrimitivesModule {
  static final byte BOUND_BYTE = -41;
  static final char BOUND_CHAR = 'g';
  static final short BOUND_SHORT = 21840;
  static final int BOUND_INT = 1894833693;
  static final long BOUND_LONG = -4369839828653523584L;
  static final boolean BOUND_BOOLEAN = true;
  static final float BOUND_FLOAT = (float) 0.9964542;
  static final double BOUND_DOUBLE = 0.12681322049667765;

  /*
   * While we can't ensure that these constants stay constant, this is a test so we're just going to
   * keep our fingers crossed that we're not going to be jerks.
   */
  static final byte[] BOUND_BYTE_ARRAY =  {1, 2, 3};
  static final char[] BOUND_CHAR_ARRAY = {'g', 'a', 'k'};
  static final short[] BOUND_SHORT_ARRAY = {2, 4};
  static final int[] BOUND_INT_ARRAY = {3, 1, 2};
  static final long[] BOUND_LONG_ARRAY = {1, 1, 2, 3, 5};
  static final boolean[] BOUND_BOOLEAN_ARRAY = {false, true, false, false};
  static final float[] BOUND_FLOAT_ARRAY = {(float) 0.1, (float) 0.01, (float) 0.001};
  static final double[] BOUND_DOUBLE_ARRAY = {0.2, 0.02, 0.002};

  @Provides byte provideByte() {
    return BOUND_BYTE;
  }

  @Provides char provideChar() {
    return BOUND_CHAR;
  }

  @Provides short provideShort() {
    return BOUND_SHORT;
  }

  @Provides int provideInt() {
    return BOUND_INT;
  }

  @Provides long provideLong() {
    return BOUND_LONG;
  }

  @Provides boolean provideBoolean() {
    return BOUND_BOOLEAN;
  }

  @Provides float provideFloat() {
    return BOUND_FLOAT;
  }

  @Provides double boundDouble() {
    return BOUND_DOUBLE;
  }

  @Provides byte[] provideByteArray() {
    return BOUND_BYTE_ARRAY;
  }

  @Provides char[] provideCharArray() {
    return BOUND_CHAR_ARRAY;
  }

  @Provides short[] provideShortArray() {
    return BOUND_SHORT_ARRAY;
  }

  @Provides int[] provideIntArray() {
    return BOUND_INT_ARRAY;
  }

  @Provides long[] provideLongArray() {
    return BOUND_LONG_ARRAY;
  }

  @Provides boolean[] provideBooleanArray() {
    return BOUND_BOOLEAN_ARRAY;
  }

  @Provides float[] provideFloatArray() {
    return BOUND_FLOAT_ARRAY;
  }

  @Provides double[] boundDoubleArray() {
    return BOUND_DOUBLE_ARRAY;
  }
}
