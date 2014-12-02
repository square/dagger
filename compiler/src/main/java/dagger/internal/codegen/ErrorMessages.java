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

import dagger.Provides;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.lang.model.element.AnnotationMirror;

/**
 * The collection of error messages to be reported back to users.
 *
 * @author Gregory Kick
 * @since 2.0
 */
final class ErrorMessages {
  /*
   * Common constants.
   */
  static final String INDENT = "    ";

  /*
   * JSR-330 errors
   *
   * These are errors that are explicitly outlined in the JSR-330 APIs
   */

  /* constructors */
  static final String MULTIPLE_INJECT_CONSTRUCTORS =
      "Types may only contain one @Inject constructor.";

  /* fields */
  static final String FINAL_INJECT_FIELD = "@Inject fields may not be final";

  /* methods */
  static final String ABSTRACT_INJECT_METHOD = "Methods with @Inject may not be abstract.";
  static final String GENERIC_INJECT_METHOD =
      "Methods with @Inject may not declare type parameters.";

  /* qualifiers */
  static final String MULTIPLE_QUALIFIERS =
      "A single injection site may not use more than one @Qualifier.";

  /* scope */
  static final String MULTIPLE_SCOPES = "A single binding may not declare more than one @Scope.";

  /*
   * Dagger errors
   *
   * These are errors that arise due to restrictions imposed by the dagger implementation.
   */

  /* constructors */
  static final String INJECT_ON_PRIVATE_CONSTRUCTOR =
      "Dagger does not support injection into private constructors";
  static final String INJECT_CONSTRUCTOR_ON_INNER_CLASS =
      "@Inject constructors are invalid on inner classes";
  static final String INJECT_CONSTRUCTOR_ON_GENERIC_CLASS =
      "Generic types may not use @Inject constructors. "
          + "Use a @Provides method to bind the type parameters.";
  static final String INJECT_CONSTRUCTOR_ON_ABSTRACT_CLASS =
      "@Inject is nonsense on the constructor of an abstract class";
    static final String QUALIFIER_ON_INJECT_CONSTRUCTOR =
      "@Qualifier annotations are not allowed on @Inject constructors.";

  /* fields */
  static final String PRIVATE_INJECT_FIELD =
      "Dagger does not support injection into private fields";

  /* methods */
  static final String PRIVATE_INJECT_METHOD =
      "Dagger does not support injection into private methods";

  /* all */
  static final String INJECT_INTO_PRIVATE_CLASS =
      "Dagger does not support injection into private classes";

  /*
   * Configuration errors
   *
   * These are errors that relate specifically to the Dagger configuration API (@Module, @Provides,
   * etc.)
   */
  static final String DUPLICATE_BINDINGS_FOR_KEY_FORMAT =
      "%s is bound multiple times:";

  static final String PROVIDES_METHOD_RETURN_TYPE =
      "@Provides methods must either return a primitive, an array or a declared type.";

  static final String PRODUCES_METHOD_RETURN_TYPE =
      "@Produces methods must either return a primitive, an array or a declared type, or a"
      + " ListenableFuture of one of those types.";

  static final String PRODUCES_METHOD_RAW_FUTURE =
      "@Produces methods cannot return a raw ListenableFuture.";

  static final String BINDING_METHOD_SET_VALUES_RAW_SET =
      "@%s methods of type set values cannot return a raw Set";

  static final String PROVIDES_METHOD_SET_VALUES_RETURN_SET =
      "@Provides methods of type set values must return a Set";

  static final String PRODUCES_METHOD_SET_VALUES_RETURN_SET =
      "@Produces methods of type set values must return a Set or ListenableFuture of Set";

  static final String BINDING_METHOD_MUST_RETURN_A_VALUE =
      "@%s methods must return a value (not void).";

  static final String BINDING_METHOD_ABSTRACT = "@%s methods cannot be abstract";

  static final String BINDING_METHOD_STATIC = "@%s methods cannot be static";

  static final String BINDING_METHOD_PRIVATE = "@%s methods cannot be private";

  static final String BINDING_METHOD_TYPE_PARAMETER =
      "@%s methods may not have type parameters.";

  static final String BINDING_METHOD_NOT_IN_MODULE =
      "@%s methods can only be present within a @%s";

  static final String BINDING_METHOD_NOT_MAP_HAS_MAP_KEY =
      "@%s methods of non map type cannot declare a map key";

  static final String BINDING_METHOD_WITH_NO_MAP_KEY =
      "@%s methods of type map must declare a map key";

  static final String BINDING_METHOD_WITH_MULTIPLE_MAP_KEY =
      "@%s methods may not have more than one @MapKey-marked annotation";

  static final String BINDING_METHOD_WITH_SAME_NAME =
      "Cannot have more than one @%s method with the same name in a single module";

  /*mapKey errors*/
  static final String MAPKEY_WITHOUT_FIELDS =
      "Map key annotation does not have fields";

  /* collection binding errors */
  static final String MULTIPLE_BINDING_TYPES_FORMAT =
      "More than one binding present of different types %s";

  static final String MULTIPLE_BINDING_TYPES_FOR_KEY_FORMAT =
      "%s has incompatible bindings:\n";

  static final String REQUIRES_AT_INJECT_CONSTRUCTOR_OR_PROVIDER_FORMAT =
      "%s cannot be provided without an @Inject constructor or from an @Provides-annotated method.";

  static final String REQUIRES_PROVIDER_FORMAT =
      "%s cannot be provided without an @Provides-annotated method.";

  static final String MEMBERS_INJECTION_DOES_NOT_IMPLY_PROVISION =
      "This type supports members injection but cannot be implicitly provided.";

  static final String CONTAINS_DEPENDENCY_CYCLE_FORMAT = "%s.%s() contains a dependency cycle:\n%s";

  static final String MALFORMED_MODULE_METHOD_FORMAT =
      "Cannot generated a graph because method %s on module %s was malformed";

  /**
   * A regular expression to match a small list of specific packages deemed to
   * be unhelpful to display in fully qualified types in error messages.
   *
   * Note: This should never be applied to messages themselves.
   */
  private static final Pattern COMMON_PACKAGE_PATTERN = Pattern.compile(
      "(?:^|[^.a-z_])"     // What we want to match on but not capture.
      + "((?:"             // Start a group with a non-capturing or part
      + "java[.]lang"
      + "|java[.]util"
      + "|javax[.]inject"
      + "|dagger"
      + "|com[.]google[.]common[.]base"
      + "|com[.]google[.]common[.]collect"
      + ")[.])"            // Always end with a literal .
      + "[A-Z]");           // What we want to match on but not capture.

  /**
   * A method to strip out common packages and a few rare type prefixes
   * from types' string representation before being used in error messages.
   *
   * This type assumes a String value that is a valid fully qualified
   * (and possibly parameterized) type, and should NOT be used with
   * arbitrary text, especially prose error messages.
   *
   * TODO(user): Tighten these to take type representations (mirrors
   *     and elements) to avoid accidental mis-use by running errors
   *     through this method.
   */
  static String stripCommonTypePrefixes(String type) {
    // Special case this enum's constants since they will be incredibly common.
    type = type.replace(Provides.Type.class.getCanonicalName() + ".", "");

    // Do regex magic to remove common packages we care to shorten.
    Matcher matcher = COMMON_PACKAGE_PATTERN.matcher(type);
    StringBuilder result = new StringBuilder();
    int index = 0;
    while (matcher.find()) {
      result.append(type.subSequence(index, matcher.start(1)));
      index = matcher.end(1); // Skip the matched pattern content.
    }
    result.append(type.subSequence(index, type.length()));
    return result.toString();
  }

  //TODO(user): Extract Formatter and do something less stringy.
  static String format(AnnotationMirror annotation) {
    return stripCommonTypePrefixes(annotation.toString());
  }

  private ErrorMessages() {}
}
