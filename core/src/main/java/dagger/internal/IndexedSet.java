package dagger.internal;

import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: ylevin
 * Date: 28.12.12
 * Time: 18:17
 */
public class IndexedSet<T> extends AbstractSet<T> {

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
