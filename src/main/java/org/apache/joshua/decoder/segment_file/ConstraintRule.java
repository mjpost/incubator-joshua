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
package org.apache.joshua.decoder.segment_file;

import javax.swing.text.Segment;

/**
 * This interface is for an individual (partial) item to seed the chart with. All rules should be
 * flat (no hierarchical nonterminals).
 * <p>
 * The {@link Segment}, {@link ConstraintSpan}, and {@link ConstraintRule} interfaces are for
 * defining an interchange format between a SegmentFileParser and the Chart class. These interfaces
 * <b>should not</b> be used internally by the Chart. The objects returned by a
 * SegmentFileParser will not be optimal for use during decoding. The Chart should convert each of
 * these objects into its own internal representation during construction. That is the contract
 * described by these interfaces.
 * 
 * @see org.apache.joshua.decoder.segment_file.ConstraintRule.Type
 * 
 * @author wren ng thornton wren@users.sourceforge.net
 * @version $LastChangedDate: 2009-03-26 15:06:57 -0400 (Thu, 26 Mar 2009) $
 */
public interface ConstraintRule {

  /**
   * <p>There are three types of ConstraintRule. The RULE type returns non-null values for all methods.
   * The LHS type provides a (non-null) value for the lhs method, but returns null for everything
   * else. And the RHS type provides a (non-null) value for nativeRhs and foreignRhs but returns
   * null for the lhs and features.</p>
   * <p>
   * The interpretation of a RULE is that it adds a new rule to the grammar which only applies to
   * the associated span. If the associated span is hard, then the set of rules for that span will
   * override the regular grammar.</p>
   * <p>
   * The intepretation of a LHS is that it provides a hard constraint that the associated span be
   * treated as the nonterminal for that span, thus filtering the regular grammar.</p>
   * <p>
   * The interpretation of a RHS is that it provides a hard constraint to filter the regular grammar
   * such that only rules generating the desired translation can be used.</p>
   */
  enum Type {
    RULE, LHS, RHS
  }

  /** 
   * Return the type of this ConstraintRule.
   * @return the {@link org.apache.joshua.decoder.segment_file.ConstraintRule.Type}
   */
  Type type();


  /**
   * Return the left hand side of the constraint rule. If this is null, then this object is
   * specifying a translation for the span, but that translation may be derived from any
   * nonterminal. The nonterminal here must be one used by the regular grammar.
   * @return the left hand side of the constraint rule
   */
  String lhs();


  /**
   * Return the native right hand side of the constraint rule. If this is null, then the regular
   * grammar will be used to fill in the derivation from the lhs.
   * @return the native right hand side of the constraint rule
   */
  String nativeRhs();


  /**
   * Return the foreign right hand side of the constraint rule. This must be consistent with the
   * sentence for the associated span, and is provided as a convenience method.
   * @return the foreign right hand side of the constraint rule
   */
  String foreignRhs();


  /**
   * Return the grammar feature values for the RULE. The length of this array must be the same as
   * for the regular grammar. We cannot enforce this requirement, but the
   * {@link org.apache.joshua.decoder.chart_parser.Chart} must throw an error if there is a mismatch.
   * @return an array of floating feature values for the RULE 
   */
  float[] features();
}
