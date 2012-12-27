package dagger.internal.plugins.reflect;

import dagger.internal.Binding;
import dagger.internal.Keys;
import dagger.internal.Linker;
import dagger.internal.UniqueMap;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.lang.reflect.Type;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: ylevin
 * Date: 17.12.12
 * Time: 13:03
 */
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
    findFactoryMethod();
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
    IndexedSet<String> assistedKeys = new IndexedSet<String>();
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

        int index = assistedKeys.getIndexOf(key);
        if (index == -1) {
          continue;
        }

        indexes[i] = index;
      }

      transposition = indexes;
      factoryMethod = method;
    }
    if (factoryMethod == null) {
      throw new AssertionError("Not found factory method for " + targetBinding + " in "
          + factory.getCanonicalName());
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

  private class IndexedSet<T> extends AbstractSet<T> implements Set<T> {

    private final Map<T, Integer> map = new UniqueMap<T, Integer>();

    private int counter = 0;

    @Override public int size() {
      return map.size();
    }

    @Override public boolean contains(Object o) {
      return map.containsKey(o);
    }

    @Override public Iterator<T> iterator() {
      return map.keySet().iterator();
    }

    @Override public boolean add(T t) {
      map.put(t, counter++);
      return true;
    }

    public int getIndexOf(T t) {
      Integer index = map.get(t);
      if (index == null) {
        return -1;
      }
      return index;
    }
  }

}
