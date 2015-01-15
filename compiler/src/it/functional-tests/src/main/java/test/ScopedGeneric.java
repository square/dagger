package test;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class ScopedGeneric<T> { 
  final T t;  
  @Inject ScopedGeneric(T t) {
    this.t = t;
  }  
}