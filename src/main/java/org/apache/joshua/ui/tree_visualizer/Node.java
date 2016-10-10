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
package org.apache.joshua.ui.tree_visualizer;

/**
 * A representation of a node in a derivation tree. The derivation tree class itself is
 * parameterized in terms of this class and the <code>DerivationEdge</code> class. A
 * <code>Node</code> may represent either a non-terminal symbol or one or more terminal symbols of
 * the derivation.
 */
public class Node {
  /**
   * The label to be shown on the node. If the node is a non-terminal symbol, it is the name of the
   * symbol. Otherwise, it is terminal symbols joined with spaces.
   */
  public final String label;

  /**
   * Indicates whether this node is part of the source-side of target- side derivation tree.
   */
  public final boolean isSource;

  /**
   * A boolean to let the renderer know whether this vertex is highlighted.
   */
  public boolean isHighlighted = false;

  /**
   * Constructor used for root nodes or nodes whose parent is not given.
   * 
   * @param label a <code>String</code> that represents the symbols at this node
   * @param isSource a boolean saying whether this is a source-side node
   */
  public Node(String label, boolean isSource) {
    this.label = label;
    this.isSource = isSource;
  }

	@Override
  public String toString() {
    return label;
  }
}
