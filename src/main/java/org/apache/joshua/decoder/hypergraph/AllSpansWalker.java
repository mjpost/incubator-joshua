/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.joshua.decoder.hypergraph;

import java.util.HashSet;
import java.util.Set;

import org.apache.joshua.corpus.Span;

/***
 * Uses {@link ForestWalker} to visit one {@link HGNode} per span of the chart. No guarantees are
 * provided as to which HGNode will be visited in each span.
 * 
 * @author Matt Post post@cs.jhu.edu
 * 
 */

public class AllSpansWalker {
  private final Set<Span> visitedSpans;

  public AllSpansWalker() {
    visitedSpans = new HashSet<>();
  }

  /**
   * This function wraps a {@link ForestWalker}, preventing calls to its walker function for all but
   * the first node reached for each span.
   * 
   * @param node the {@link org.apache.joshua.decoder.hypergraph.HGNode} we wish to walk
   * @param walker the {@link org.apache.joshua.decoder.hypergraph.WalkerFunction} 
   * implementation to do the walking
   */
  public void walk(HGNode node, final WalkerFunction walker) {
    new ForestWalker().walk(node, (node1, index) -> {
      if (node1 != null) {
        Span span = new Span(node1.i, node1.j);
        if (!visitedSpans.contains(span)) {
          walker.apply(node1, 0);
          visitedSpans.add(span);
        }
      }
    });
  }
}
