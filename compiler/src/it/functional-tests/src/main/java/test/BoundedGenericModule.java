/*
 * Copyright (C) 2015 Google, Inc.
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
package test;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import dagger.Module;
import dagger.Provides;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

@Module
class BoundedGenericModule {

  @Provides
  Integer provideInteger() {
    return 1;
  }

  @Provides
  Double provideDouble() {
    return 2d;
  }

  @Provides
  ArrayList<String> provideArrayListString() {
    return Lists.newArrayList("arrayListOfString");
  }

  @Provides
  LinkedList<String> provideLinkedListString() {
    return BoundedGenericModule.newLinkedList("linkedListOfString");
  }

  @Provides
  LinkedList<CharSequence> provideLinkedListCharSeq() {
    return BoundedGenericModule.<CharSequence>newLinkedList("linkedListOfCharSeq");
  }

  @Provides
  @SuppressWarnings("unchecked")
  LinkedList<Comparable<String>> provideArrayListOfComparableString() {
    return BoundedGenericModule.<Comparable<String>>newLinkedList("arrayListOfComparableOfString");
  }

  @Provides
  List<Integer> provideListOfInteger() {
    return Lists.newArrayList(3);
  }

  @Provides
  Set<Double> provideSetOfDouble() {
    return Sets.newHashSet(4d);
  }

  private static <E> LinkedList<E> newLinkedList(E... elements) {
    LinkedList<E> list = Lists.newLinkedList();
    Collections.addAll(list, elements);
    return list;
  }
}
