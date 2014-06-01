/*
 * Copyright (C) 2013 Square, Inc.
 * Copyright (C) 2013 Google, Inc.
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

import dagger.internal.loaders.ReflectiveAtInjectBinding;
import dagger.internal.loaders.ReflectiveStaticInjection;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import static dagger.internal.loaders.GeneratedAdapters.INJECT_ADAPTER_SUFFIX;
import static dagger.internal.loaders.GeneratedAdapters.MODULE_ADAPTER_SUFFIX;
import static dagger.internal.loaders.GeneratedAdapters.STATIC_INJECTION_SUFFIX;

/**
 * Handles loading/finding of modules, injection bindings, and static injections by use of a
 * strategy of "load the appropriate generated code" or, if no such code is found, create a
 * reflective equivalent.
 */
public final class FailoverLoader extends Loader {
  /*
   * Note that String.concat is used throughout this code because it is the most efficient way to
   * concatenate _two_ strings.  javac uses StringBuilder for the + operator and it has proven to
   * be wasteful in terms of both CPU and memory allocated.
   */

  private final Memoizer<Class<?>, ModuleAdapter<?>> loadedAdapters =
      new Memoizer<Class<?>, ModuleAdapter<?>>() {
        @Override protected ModuleAdapter<?> create(Class<?> type) {
          ModuleAdapter<?> result =
              instantiate(type.getName().concat(MODULE_ADAPTER_SUFFIX), type.getClassLoader());
          if (result == null) {
            throw new IllegalStateException("Module adapter for " + type + " could not be loaded. "
                + "Please ensure that code generation was run for this module.");
          }
          return result;
        }
      };

  /**
   * Obtains a module adapter for {@code module} from the first responding resolver.
   */
  @SuppressWarnings("unchecked") // cache ensures types match
  @Override public <T> ModuleAdapter<T> getModuleAdapter(Class<T> type) {
    return (ModuleAdapter<T>) loadedAdapters.get(type);
  }

  private final Memoizer<AtInjectBindingKey, AtInjectBindingInfo> atInjectBindings =
      new Memoizer<AtInjectBindingKey, AtInjectBindingInfo>() {
        @Override protected AtInjectBindingInfo create(AtInjectBindingKey key) {
          return getAtInjectBindingInfo(key.classLoader, key.className);
        }
      };

  private static final class AtInjectBindingKey {
    // classLoader can be null
    private final ClassLoader classLoader;
    private final String className;

    AtInjectBindingKey(ClassLoader classLoader, String className) {
      this.classLoader = classLoader;
      this.className = className;
    }

    @Override
    public int hashCode() {
      // It is highly unlikely for the same class name to be present in multiple class loaders. If
      // this does happen, we'll just let those keys collide.
      return className.hashCode();
    }

    @Override
    public boolean equals(Object object) {
      if (object == this) {
        return true;
      }
      if (object instanceof AtInjectBindingKey) {
        AtInjectBindingKey other = (AtInjectBindingKey) object;
        return (classLoader == other.classLoader) && className.equals(other.className);
      }
      return false;

    }
  }

  private static final class AtInjectBindingInfo {
    private final Constructor<Binding<?>> adapterConstructor;
    private final ReflectiveAtInjectBinding.Factory<?> reflectiveBindingFactory;

    AtInjectBindingInfo(Constructor<Binding<?>> adapterConstructor,
        ReflectiveAtInjectBinding.Factory<?> reflectiveBindingFactory) {
      this.adapterConstructor = adapterConstructor;
      this.reflectiveBindingFactory = reflectiveBindingFactory;
    }
  }

  @Override public Binding<?> getAtInjectBinding(
      String key, String className, ClassLoader classLoader, boolean mustHaveInjections) {
    AtInjectBindingInfo info = atInjectBindings.get(new AtInjectBindingKey(classLoader, className));
    if (info.adapterConstructor != null) {
      try {
        return info.adapterConstructor.newInstance();
        // Duplicated catch statements becase: android.
      } catch (InstantiationException e) {
        throw new IllegalStateException(
            "Could not create an instance of the inject adapter for class " + className, e);
      } catch (IllegalAccessException e) {
        throw new IllegalStateException(
            "Could not create an instance of the inject adapter for class " + className, e);
      } catch (IllegalArgumentException e) {
        throw new IllegalStateException(
            "Could not create an instance of the inject adapter for class " + className, e);
      } catch (InvocationTargetException e) {
        throw new IllegalStateException(
            "Could not create an instance of the inject adapter for class " + className, e);
      }
    } else if (info.reflectiveBindingFactory != null) {
      return info.reflectiveBindingFactory.create(mustHaveInjections);
    } else {
      return null;
    }
  }

  private AtInjectBindingInfo getAtInjectBindingInfo(ClassLoader classLoader, String className) {
    Class<?> adapterClass = loadClass(classLoader, className.concat(INJECT_ADAPTER_SUFFIX));
    if (!adapterClass.equals(Void.class)) {
      // Found loadable adapter, using it.
      try {
        @SuppressWarnings("unchecked")
        Constructor<Binding<?>> constructor
            = (Constructor<Binding<?>>) adapterClass.getConstructor();
        return new AtInjectBindingInfo(constructor, null);
      } catch (NoSuchMethodException e) {
        throw new IllegalStateException(
            "Couldn't find default constructor in the generated inject adapter for class "
            + className);
      }
    }
    Class<?> type = loadClass(classLoader, className);
    if (type.equals(Void.class)) {
      throw new IllegalStateException("Could not load class " + className);
    }
    if (type.isInterface()) {
      // Short-circuit since we can't build reflective bindings for interfaces.
      return new AtInjectBindingInfo(null, null);
    }
    ReflectiveAtInjectBinding.Factory<?> reflectiveBindingFactory
        = ReflectiveAtInjectBinding.createFactory(type);
    return new AtInjectBindingInfo(null, reflectiveBindingFactory);
  }

  @Override public StaticInjection getStaticInjection(Class<?> injectedClass) {
    StaticInjection result = instantiate(
          injectedClass.getName().concat(STATIC_INJECTION_SUFFIX), injectedClass.getClassLoader());
    if (result != null) {
      return result;
    }
    return ReflectiveStaticInjection.create(injectedClass);
  }
}
