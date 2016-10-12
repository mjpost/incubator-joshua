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

/**
 * Classes implementing this interface define a single function that is applied to each node. This
 * interface is used for various walkers (ViterbiExtractor).
 */
public interface WalkerFunction {

  /**
   * Function that is applied to node at tail node index nodeIndex.
   * nodeIndex indicates the index of node in the list of tailnodes for the
   * outgoing edge.
   * @param node the {{@link org.apache.joshua.decoder.hypergraph.HGNode} we
   * wish to apply some Walker Function to.
   * @param nodeIndex node in the list of tailnodes for the outgoing edge
   */
  void apply(HGNode node, int nodeIndex);

}
