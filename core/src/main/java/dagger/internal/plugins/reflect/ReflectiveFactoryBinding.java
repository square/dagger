package dagger.internal.plugins.reflect;

import dagger.internal.Binding;
import dagger.internal.Keys;
import dagger.internal.Linker;
import dagger.internal.UniqueMap;

import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: ylevin
 * Date: 17.12.12
 * Time: 13:03
 */
public class ReflectiveFactoryBinding<T, F> extends Binding<F> {
  private final Method factoryMethod;
  private final Constructor<T> constructor;
  private final Class<F> factory;

  private final Binding[] constructorBindings;
  private final Binding[] fieldBindings;

  private final Field[] fields;

  private final Integer[] assistedParameters;
  private final Integer[] assistedFields;

  private final String[] keys;

  private final Method moduleMethod;
  private final Object moduleInstance;

  private final Class<?> supertype;
  private Binding<? super T> supertypeBinding;

  public ReflectiveFactoryBinding(String provideKey, String membersKey, Object requiredBy,
                                  Class<F> factory, String[] keys, Class<?> supertype,
                                  Integer[] assistedFields, Integer[] assistedParameters,
                                  Field[] fields, Constructor<T> constructor,
                                  int parameterCount, Method factoryMethod,
                                  Method moduleMethod, Object moduleInstance) {
    super(provideKey, membersKey, true, requiredBy);
    this.supertype = supertype;
    this.keys = keys;
    this.assistedFields = assistedFields;
    this.assistedParameters = assistedParameters;
    this.fields = fields;

    this.constructor = constructor;
    this.factoryMethod = factoryMethod;
    this.moduleMethod = moduleMethod;
    moduleMethod.setAccessible(true);
    this.moduleInstance = moduleInstance;
    this.factory = factory;

    this.fieldBindings = new Binding[fields.length];
    this.constructorBindings = new Binding[parameterCount];
  }


  @SuppressWarnings("unchecked") // We're careful to make keys and bindings match up.
  @Override
  public void attach(Linker linker) {
    int k = 0;
    for (int i = 0; i < fields.length; i++) {
      if (fieldBindings[i] == null && assistedFields[i] == null) {
        fieldBindings[i] = linker.requestBinding(keys[k], fields[i]);
      }
      k++;
    }
    for (int i = 0; i < constructorBindings.length; i++) {
      if (constructorBindings[i] == null && assistedParameters[i] == null) {
        constructorBindings[i] = linker.requestBinding(keys[k], constructor);
      }
      k++;
    }
    if (supertype != null && supertypeBinding == null) {
      supertypeBinding = (Binding<? super T>) linker.requestBinding(keys[k], membersKey, false);
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public F get() {
    return (F) Proxy.newProxyInstance(ReflectiveFactoryBinding.class.getClassLoader(),
        new Class[]{factory}, new FactoryInvocationHandler());
  }

  @Override
  public void injectMembers(F f) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void getDependencies(Set<Binding<?>> get, Set<Binding<?>> injectMembers) {
    for (Binding<?> binding : constructorBindings) {
      if (binding != null) {
        get.add(binding);
      }
    }
    for (Binding<?> binding : fieldBindings) {
      if (binding != null) {
        injectMembers.add(binding);
      }
    }
    if (supertypeBinding != null) {
      injectMembers.add(supertypeBinding);
    }
  }


  private class FactoryInvocationHandler implements InvocationHandler {

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if (factoryMethod.equals(method)) {
        return moduleMethod.invoke(moduleInstance, get(args));
      }
      return method.invoke(proxy, args);
    }

    public T get(Object[] args) throws Throwable {
      Object[] params = new Object[assistedParameters.length];
      for (int i = 0; i < params.length; i++) {
        if (assistedParameters[i] != null) {
          params[i] = args[assistedParameters[i]];
        } else {
          params[i] = constructorBindings[i].get();
        }
      }
      T instance = constructor.newInstance(params);
      for (int i = 0; i < fields.length; i++) {
        if (assistedFields[i] != null) {
          fields[i].set(instance, args[assistedFields[i]]);
        } else {
          fields[i].set(instance, fieldBindings[i].get());
        }
      }
      return instance;
    }
  }

  @Override
  public String toString() {
    return provideKey != null ? provideKey : membersKey;
  }

  public static <T, F> Binding<F> create(Class<T> type, Type genericType, Class<F> factory,
                                         Method method, Object instance) {
    List<String> keys = new ArrayList<String>();
    Map<String, AssistedParameter> assisted = new UniqueMap<String, AssistedParameter>();

    List<AssistedParameter> assistedFields = new ArrayList<AssistedParameter>();
    List<AssistedParameter> assistedParameters = new ArrayList<AssistedParameter>();

    // Lookup the injectable fields and their corresponding keys.
    List<Field> injectedFields = new ArrayList<Field>();
    for (Class<?> c = type; c != Object.class; c = c.getSuperclass()) {
      for (Field field : c.getDeclaredFields()) {
        if (!field.isAnnotationPresent(Inject.class) || Modifier.isStatic(field.getModifiers())) {
          continue;
        }
        field.setAccessible(true);
        String key = Keys.get(field.getGenericType(), field.getAnnotations(), field);
        injectedFields.add(field);
        if (Keys.isAssisted(key)) {
          AssistedParameter assistedParameter = new AssistedParameter();
          assistedFields.add(assistedParameter);
          assisted.put(key, assistedParameter);
          keys.add(null);
        } else {
          keys.add(key);
          assistedFields.add(null);
        }
      }
    }

    // Look up @Inject-annotated constructors. If there's no @Inject-annotated
    // constructor, use a default public constructor if the class has other
    // injections. Otherwise treat the class as non-injectable.
    Constructor<T> injectedConstructor = null;
    for (Constructor<T> constructor : getConstructorsForType(type)) {
      if (!constructor.isAnnotationPresent(Inject.class)) {
        continue;
      }
      if (injectedConstructor != null) {
        throw new IllegalArgumentException("Too many injectable constructors on " + type.getName());
      }
      injectedConstructor = constructor;
    }
    if (injectedConstructor == null) {
      if (injectedFields.isEmpty()) {
        throw new IllegalArgumentException("No injectable members on " + type.getName()
            + ". Do you want to add an injectable constructor?");
      }
      try {
        injectedConstructor = type.getDeclaredConstructor();
      } catch (NoSuchMethodException ignored) {
      }
    }

    int parameterCount;
    String provideKey;
    if (injectedConstructor != null) {
      provideKey = Keys.get(type);
      injectedConstructor.setAccessible(true);
      Type[] types = injectedConstructor.getGenericParameterTypes();

      parameterCount = types.length;
      if (parameterCount != 0) {
        Annotation[][] annotations = injectedConstructor.getParameterAnnotations();
        for (int p = 0; p < types.length; p++) {
          String key = Keys.get(types[p], annotations[p], injectedConstructor);
          if (Keys.isAssisted(key)) {
            AssistedParameter assistedParameter = new AssistedParameter();
            assistedParameters.add(assistedParameter);
            assisted.put(key, assistedParameter);
            keys.add(null);
          } else {
            keys.add(key);
            assistedParameters.add(null);
          }
        }
      }
    } else {
      provideKey = null;
      parameterCount = 0;
    }

    Class<? super T> supertype = type.getSuperclass();
    if (supertype != null) {
      if (Keys.isPlatformType(supertype.getName())) {
        supertype = null;
      } else {
        keys.add(Keys.getMembersKey(supertype));
      }
    }

    Method factoryMethod = findFactoryMethod(genericType, factory, assisted);

    String membersKey = Keys.getMembersKey(type);
    return new ReflectiveFactoryBinding<T, F>(provideKey, membersKey, type, factory,
        keys.toArray(new String[keys.size()]), supertype,
        getIndexes(assistedFields), getIndexes(assistedParameters),
        injectedFields.toArray(new Field[injectedFields.size()]), injectedConstructor,
        parameterCount, factoryMethod, method, instance);
  }

  private static Integer[] getIndexes(List<AssistedParameter> assistedParameters) {
    Integer[] indexes = new Integer[assistedParameters.size()];
    for (int i = 0; i < indexes.length; i++) {
      AssistedParameter parameter = assistedParameters.get(i);
      if (parameter != null) {
        indexes[i] = parameter.index;
      }
    }
    return indexes;
  }

  private final static class AssistedParameter {
    Integer index = null;
  }

  private static Method findFactoryMethod(Type type, Class factory,
                                          Map<String, AssistedParameter> assisted) {
    Method factoryMethod = null;
    findMethod:
    for (Method method : factory.getDeclaredMethods()) {
      if (!method.getGenericReturnType().equals(type)) {
        continue;
      }

      Type[] types = method.getGenericParameterTypes();

      if (types.length != assisted.size()) {
        continue;
      }

      Annotation[][] annotations = method.getParameterAnnotations();
      for (int i = 0; i < types.length; i++) {
        String key = Keys.get(types[i], annotations[i], method + " parameter " + i);
        if (!Keys.isAssisted(key)) {
          key = Keys.getWithDefaultAssisted(types[i]);
        }
        if (!assisted.containsKey(key)) {
          continue findMethod;
        }
        assisted.get(key).index = i;
      }

      if (factoryMethod != null) {
        throw new IllegalArgumentException("Too much factory methods in "
            + factory.getName() + " for " + type);
      }

      factoryMethod = method;
    }

    if (factoryMethod == null) {
      throw new IllegalArgumentException("Not found factory method in "
          + factory.getName() + " for " + type);
    }

    return factoryMethod;
  }

  @SuppressWarnings("unchecked") // Class.getDeclaredConstructors is an unsafe API.
  private static <T> Constructor<T>[] getConstructorsForType(Class<T> type) {
    return (Constructor<T>[]) type.getDeclaredConstructors();
  }
}
