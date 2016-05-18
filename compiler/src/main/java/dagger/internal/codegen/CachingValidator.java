/*
 * Copyright (C) 2016 Google, Inc.
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

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import javax.lang.model.element.Element;

/** A decorator for {@link Validator}s that caches validation reports. */
final class CachingValidator<T extends Element> extends Validator<T> {

  private final LoadingCache<T, ValidationReport<T>> cache;

  /** A {@link Validator} that caches validation reports from {@code validator}. */
  static <T extends Element> CachingValidator<T> caching(Validator<T> validator) {
    return new CachingValidator<T>(validator);
  }

  private CachingValidator(final Validator<T> validator) {
    this.cache =
        CacheBuilder.newBuilder()
            .build(
                new CacheLoader<T, ValidationReport<T>>() {
                  @Override
                  public ValidationReport<T> load(T key) {
                    return validator.validate(key);
                  }
                });
  }

  @Override
  public ValidationReport<T> validate(T element) {
    return cache.getUnchecked(element);
  }
}
