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
package dagger.internal.codegen.writer;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.collect.FluentIterable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import java.io.IOException;
import java.util.Collections;
import java.util.Formatter;
import java.util.Iterator;
import java.util.Set;

public abstract class Snippet implements HasClassReferences, Writable {

  abstract ImmutableSet<TypeName> types();

  @Override
  public String toString() {
    return Writables.writeToString(this);
  }

  @Override
  public final Set<ClassName> referencedClasses() {
    return FluentIterable.from(types())
        .transformAndConcat(
            new Function<TypeName, Set<ClassName>>() {
              @Override
              public Set<ClassName> apply(TypeName input) {
                return input.referencedClasses();
              }
            })
        .toSet();
  }

  private static final class BasicSnippet extends Snippet {
    final String format;
    final ImmutableSet<TypeName> types;
    final ImmutableList<Object> args;

    BasicSnippet(String format, ImmutableSet<TypeName> types, ImmutableList<Object> args) {
      this.format = format;
      this.types = types;
      this.args = args;
    }

    @Override
    ImmutableSet<TypeName> types() {
      return types;
    }

    @Override
    public Appendable write(Appendable appendable, Context context) throws IOException {
      ImmutableList.Builder<Object> formattedArgsBuilder = ImmutableList.builder();
      for (Object arg : args) {
        if (arg instanceof Writable) {
          formattedArgsBuilder.add(((Writable) arg).write(new StringBuilder(), context).toString());
        } else {
          formattedArgsBuilder.add(arg);
        }
      }

      @SuppressWarnings("resource") // intentionally don't close the formatter
      Formatter formatter = new Formatter(appendable);
      formatter.format(format, Iterables.toArray(formattedArgsBuilder.build(), Object.class));

      return appendable;
    }
  }

  private static final class CompoundSnippet extends Snippet {
    final String joinToken;
    final ImmutableList<Snippet> snippets;

    CompoundSnippet(String joinToken, ImmutableList<Snippet> snippets) {
      this.joinToken = joinToken;
      this.snippets = snippets;
    }

    @Override
    ImmutableSet<TypeName> types() {
      return FluentIterable.from(snippets)
          .transformAndConcat(
              new Function<Snippet, Iterable<TypeName>>() {
                @Override
                public Iterable<TypeName> apply(Snippet input) {
                  return input.types();
                }
              })
          .toSet();
    }

    @Override
    public Appendable write(Appendable appendable, Context context) throws IOException {
      Iterator<Snippet> snippetIterator = snippets.iterator();
      if (snippetIterator.hasNext()) {
        Snippet firstSnippet = snippetIterator.next();
        firstSnippet.write(appendable, context);
        while (snippetIterator.hasNext()) {
          Snippet nextSnippet = snippetIterator.next();
          appendable.append(joinToken);
          nextSnippet.write(appendable, context);
        }
      }
      return appendable;
    }
  }

  public static Snippet format(String format, Object... args) {
    ImmutableSet.Builder<TypeName> types = ImmutableSet.builder();
    for (Object arg : args) {
      if (arg instanceof Snippet) {
        types.addAll(((Snippet) arg).types());
      }
      if (arg instanceof TypeName) {
        types.add((TypeName) arg);
      }
      if (arg instanceof HasTypeName) {
        types.add(((HasTypeName) arg).name());
      }
    }
    return new BasicSnippet(format, types.build(), ImmutableList.copyOf(args));
  }

  public static Snippet format(String format, Iterable<? extends Object> args) {
    return format(format, Iterables.toArray(args, Object.class));
  }

  public static Snippet memberSelectSnippet(Iterable<? extends Object> selectors) {
    return format(Joiner.on('.').join(Collections.nCopies(Iterables.size(selectors), "%s")),
        selectors);
  }

  public static Snippet nullCheck(Object thingToCheck) {
    return format("if (%s == null) { throw new NullPointerException(); } ", thingToCheck);
  }

  public static Snippet nullCheck(Object thingToCheck, String message) {
    return format("if (%s == null) { throw new NullPointerException(%s); } ",
        thingToCheck,
        StringLiteral.forValue(message));
  }

  public static Snippet makeParametersSnippet(Iterable<Snippet> parameterSnippets) {
    return join(", ", parameterSnippets);
  }

  /**
   * A snippet that concatenates its arguments with each snippet separated by a new line.
   */
  public static Snippet concat(Iterable<Snippet> snippets) {
    return join("\n", snippets);
  }

  /**
   * A snippet that joins its arguments with {@code joiner}.
   */
  public static Snippet join(String joinToken, Iterable<Snippet> snippets) {
    return new CompoundSnippet(joinToken, ImmutableList.copyOf(snippets));
  }
}
