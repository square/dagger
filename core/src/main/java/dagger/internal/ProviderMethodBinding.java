/*
 * Copyright (C) 2012 Square, Inc.
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

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Set;
import javax.inject.Singleton;

/**
 * Invokes a method to provide a value. The method's parameters are injected.
 */
final class ProviderMethodBinding<T> extends Binding<T> {
  private Binding[] parameters;
  private final Method method;
  private final Object instance;

  public ProviderMethodBinding(Method method, String key, Object instance) {
    super(key, null, method.isAnnotationPresent(Singleton.class), method);
    this.method = method;
    this.instance = instance;
    method.setAccessible(true);
  }

  @Override public void attach(Linker linker) {
    Type[] types = method.getGenericParameterTypes();
    Annotation[][] annotations = method.getParameterAnnotations();
    parameters = new Binding[types.length];
    for (int i = 0; i < parameters.length; i++) {
      String key = Keys.get(types[i], annotations[i], method + " parameter " + i);
      parameters[i] = linker.requestBinding(key, method);
    }
  }

  @SuppressWarnings("unchecked") // The caller is required to make 'method' and 'T' match
  @Override public T get() {
    Object[] args = new Object[parameters.length];
    for (int i = 0; i < parameters.length; i++) {
      args[i] = parameters[i].get();
    }
    try {
      return (T) method.invoke(instance, args);
    } catch (IllegalAccessException e) {
      throw new RuntimeException(e);
    } catch (InvocationTargetException e) {
      throw new RuntimeException(e.getCause());
    }
  }

  @Override public void getDependencies(Set<Binding<?>> get, Set<Binding<?>> injectMembers) {
    for (Binding binding : parameters) {
      get.add(binding);
    }
  }

  @Override public String toString() {
    return method.toString();
  }
}
