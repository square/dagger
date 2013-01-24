package dagger.internal.plugins.reflect;

import dagger.internal.Binding;
import dagger.internal.IndexedUniqueSet;
import dagger.internal.Keys;
import dagger.internal.Linker;

import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.Set;

public class ReflectiveFactoryBinding<T> extends Binding<T> {
  private final Class<T> factory;
  private final Method moduleMethod;
  private final Object moduleInstance;
  private final String targetKey;
  private final Type targetType;
  private Binding<?> targetBinding;
  private Method factoryMethod;
  private Integer[] transposition;

  public ReflectiveFactoryBinding(String provideKey, String membersKey,
                                  String targetKey, Class<T> factory, Type targetType,
                                  Method moduleMethod, Object moduleInstance) {
    super(provideKey, membersKey, true, moduleMethod);
    this.moduleMethod = moduleMethod;
    moduleMethod.setAccessible(true);
    this.targetKey = targetKey;
    this.moduleInstance = moduleInstance;
    this.factory = factory;
    this.targetType = targetType;
  }

  @Override
  public void attach(Linker linker) {
    targetBinding = linker.requestBinding(targetKey, moduleMethod);
    if (factoryMethod == null && targetBinding != null) {
      findFactoryMethod();
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public T get() {
    return (T) Proxy.newProxyInstance(ReflectiveFactoryBinding.class.getClassLoader(),
        new Class[]{factory}, new FactoryInvocationHandler());
  }

  @Override
  public void injectMembers(T t) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void getDependencies(Set<Binding<?>> get, Set<Binding<?>> injectMembers) {
    injectMembers.add(targetBinding);
  }

  private void findFactoryMethod() {
    IndexedUniqueSet<String> assistedKeys = new IndexedUniqueSet<String>();
    targetBinding.getAssistedDependencies(assistedKeys);

    Integer[] indexes = new Integer[assistedKeys.size()];

    for (Method method : factory.getMethods()) {
      if (!method.getGenericReturnType().equals(targetType)) {
        continue;
      }

      Type[] types = method.getGenericParameterTypes();
      Annotation[][] annotations = method.getParameterAnnotations();
      if (types.length != assistedKeys.size()) {
        continue;
      }


      for (int i = 0; i < types.length; i++) {
        String key = Keys.get(types[i], annotations[i], method + " parameter " + i);
        if (!Keys.isAssisted(key)) {
          key = Keys.getWithDefaultAssisted(types[i]);
        }

        int index = assistedKeys.indexOf(key);
        if (index == -1) {
          continue;
        }

        indexes[i] = index;
      }

      transposition = indexes;
      factoryMethod = method;
    }
    if (factoryMethod == null) {
      throw new AssertionError("Factory method for " + targetBinding + " in "
          + factory.getCanonicalName() + " not found");
    }
  }

  private class FactoryInvocationHandler implements InvocationHandler {

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      if (factoryMethod.equals(method)) {
        Object[] newArgs = new Object[args.length];
        for (int i = 0; i < newArgs.length; i++) {
          newArgs[i] = args[transposition[i]];
        }
        return moduleMethod.invoke(moduleInstance, targetBinding.get(newArgs));
      }
      return method.invoke(proxy, args);
    }
  }

  @Override
  public String toString() {
    return provideKey != null ? provideKey : membersKey;
  }
}
