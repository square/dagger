package dagger.internal;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

public class IndexedUniqueSet<T> extends AbstractSet<T> {

  private final Map<T, Integer> map = new LinkedHashMap<T, Integer>();

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
    Integer clobbered = map.put(t, counter++);
    if (clobbered != null) {
      map.put(t, clobbered);
      counter--;
      throw new IllegalArgumentException("Duplicate: " + t);
    }
    return true;
  }

  public int indexOf(T t) {
    Integer index = map.get(t);
    if (index == null) {
      return -1;
    }
    return index;
  }
}
