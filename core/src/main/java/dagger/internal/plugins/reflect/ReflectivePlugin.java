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
package dagger.internal.plugins.reflect;

import dagger.Module;
import dagger.internal.Binding;
import dagger.internal.ModuleAdapter;
import dagger.internal.Plugin;
import dagger.internal.StaticInjection;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;

/**
 * Resolves a {@code ModuleAdapter<T>} for a given module T
 */
public class ReflectivePlugin implements Plugin {

  @Override
  public Binding<?> getAtInjectBinding(String key, String className, boolean mustBeInjectable)
      throws ClassNotFoundException {
    try {
      Class<?> c = Class.forName(className);
      if (c.isInterface()) {
        return null;
      }
      return ReflectiveAtInjectBinding.create(c, mustBeInjectable);
    } catch (Exception ignored) {
      return null;
    }
  }

  /**
   * Returns a module adapter that processes modules via reflection.
   */
  @Override
  @SuppressWarnings("unchecked") // Runtime checks validate that the result type matches 'T'.
  public <T> ModuleAdapter<T> getModuleAdapter(Class<? extends T> moduleClass, T module) {
    Module annotation = moduleClass.getAnnotation(Module.class);
    if (annotation == null) {
      // TODO(cgruber): Should we throw, or just return no module adapter?
      throw new IllegalArgumentException("No @Module on " + moduleClass.getName());
    }
    return (ModuleAdapter<T>) new ReflectiveModuleAdapter(moduleClass, annotation);
  }

  @Override
  public StaticInjection getStaticInjection(Class<?> injectedClass) {
    List<Field> fields = new ArrayList<Field>();
    for (Field field : injectedClass.getDeclaredFields()) {
      if (field.getAnnotation(Inject.class) == null
          || !Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      field.setAccessible(true);
      fields.add(field);
    }
    if (fields.isEmpty()) {
      throw new IllegalArgumentException("No static injections: " + injectedClass.getName());
    }
    return new ReflectiveStaticInjection(fields.toArray(new Field[fields.size()]));
  }
}