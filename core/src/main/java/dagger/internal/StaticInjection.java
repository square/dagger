/*
 * Copyright (C) 2012 Square Inc.
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

import dagger.ObjectGraph;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 * Injects the static fields of a class.
 *
 * @author Jesse Wilson
 */
public abstract class StaticInjection {
  private static final Logger LOGGER = Logger.getLogger(ObjectGraph.class.getName());

  public abstract void attach(Linker linker);

  public abstract void inject();

  public static StaticInjection get(Class<?> injectedClass) {
    try {
      String adapter = injectedClass.getName() + "$StaticInjection";
      Class<?> c = Class.forName(adapter);
      Constructor<?> constructor = c.getConstructor();
      constructor.setAccessible(true);
      return (StaticInjection) constructor.newInstance();
    } catch (Exception e) {
      LOGGER.log(Level.FINE, "No generated static injection for " + injectedClass.getName()
          + ". Falling back to reflection.", e);
    }

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

  static class ReflectiveStaticInjection extends StaticInjection {
    private final Field[] fields;
    private Binding<?>[] bindings;

    private ReflectiveStaticInjection(Field[] fields) {
      this.fields = fields;
    }

    @Override
    public void attach(Linker linker) {
      bindings = new Binding<?>[fields.length];
      for (int i = 0; i < fields.length; i++) {
        Field field = fields[i];
        String key = Keys.get(field.getGenericType(), field.getAnnotations(), field);
        bindings[i] = linker.requestBinding(key, field);
      }
    }

    @Override
    public void inject() {
      try {
        for (int f = 0; f < fields.length; f++) {
          fields[f].set(null, bindings[f].get());
        }
      } catch (IllegalAccessException e) {
        throw new AssertionError(e);
      }
    }
  }
}
