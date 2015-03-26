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
package dagger.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.inject.Provider;

import static dagger.internal.Collections.newLinkedHashSetWithExpectedSize;
import static java.util.Collections.unmodifiableSet;

/**
 * A {@link Factory} implementation used to implement {@link Set} bindings. This factory always
 * returns a new {@link Set} instance for each call to {@link #get} (as required by {@link Factory})
 * whose elements are populated by subsequent calls to their {@link Provider#get} methods.
 *
 * @author Gregory Kick
 * @since 2.0
 */
public final class SetFactory<T> implements Factory<Set<T>> {
  /**
   * Returns a new factory that creates {@link Set} instances that from the union of the given
   * {@link Provider} instances.
   */
  public static <T> Factory<Set<T>> create(Provider<Set<T>> first,
      @SuppressWarnings("unchecked") Provider<Set<T>>... rest) {
    if (first == null) {
      throw new NullPointerException();
    }
    if (rest == null) {
      throw new NullPointerException();
    }
    Set<Provider<Set<T>>> contributingProviders = newLinkedHashSetWithExpectedSize(1 + rest.length);
    contributingProviders.add(first);
    for (Provider<Set<T>> provider : rest) {
      if (provider == null) {
        throw new NullPointerException();
      }
      contributingProviders.add(provider);
    }
    return new SetFactory<T>(contributingProviders);
  }

  private final Set<Provider<Set<T>>> contributingProviders;

  private SetFactory(Set<Provider<Set<T>>> contributingProviders) {
    this.contributingProviders = contributingProviders;
  }

  /**
   * Returns a {@link Set} whose iteration order is that of the elements given by each of the
   * providers, which are invoked in the order given at creation.
   *
   * @throws NullPointerException if any of the delegate {@link Set} instances or elements therein
   *     are {@code null}
   */
  @Override
  public Set<T> get() {
    List<Set<T>> providedSets = new ArrayList<Set<T>>(contributingProviders.size());
    for (Provider<Set<T>> provider : contributingProviders) {
      Set<T> providedSet = provider.get();
      if (providedSet == null) {
        throw new NullPointerException(provider + " returned null");
      }
      providedSets.add(providedSet);
    }
    int size = 0;
    for (Set<T> providedSet : providedSets) {
      size += providedSet.size();
    }
    Set<T> result = newLinkedHashSetWithExpectedSize(size);
    for (Set<T> s : providedSets) {
      for (T element : s) {
        if (element == null) {
          throw new NullPointerException("a null element was provided");
        }
        result.add(element);
      }
    }
    return unmodifiableSet(result);
  }
}
