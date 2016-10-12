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

import java.util.List;

import javax.swing.text.Segment;

/**
 * This interface represents a collection of constraints for a given span in the associated segment.
 * Intuitively, each constraint corresponds to one or more items in the chart for parsing, except
 * that we pre-seed the chart with these items before beginning the parsing algorithm. Some
 * constraints can be "hard", in which case the regular grammar is not consulted for these spans. It
 * is an error to have hard constraints for overlapping spans.
 * <p>
 * Indices for the span boundaries mark the transitions between words. Thus, the 0 index occurs
 * before the first word, the 1 index occurs between the first and second words, 2 is between the
 * second and third, etc. Consequently, it is an error for the end index to be equal to or less than
 * the start index. It is also an error to have negative indices or to have indices larger than the
 * count of words in the segment. Clients may assume that no <code>ConstraintSpan</code> objects are
 * constructed which violate these laws.
 * <p>
 * The {@link Segment}, {@link ConstraintSpan}, and {@link ConstraintRule} interfaces are for
 * defining an interchange format between a SegmentFileParser and the Chart class. These interfaces
 * <b>should not</b> be used internally by the Chart. The objects returned by a
 * SegmentFileParser will not be optimal for use during decoding. The Chart should convert each of
 * these objects into its own internal representation during construction. That is the contract
 * described by these interfaces.
 * 
 * @author wren ng thornton wren@users.sourceforge.net
 */
public interface ConstraintSpan {

  /**
   * Return the starting index of the span covered by this constraint.
   * @return the starting index of the span covered by this constraint
   */
  int start();

  /**
   * Return the ending index of the span covered by this constraint. Clients may assume
   * <code>this.end() &gt;= 1 + this.start()</code>.
   * @return the ending index of the span covered by this constraint
   */
  int end();

  /**
   * Return whether this is a hard constraint which should override the grammar. This value only
   * really matters for sets of <code>RULE</code> type constraints.
   * @return true if a hard constraint exists which should override the grammar
   */
  boolean isHard();

  /**
   * Return a collection of the "rules" for this constraint span.
   * <p>
   * This return type is suboptimal for some SegmentFileParsers. It should be an
   * {@link java.util.Iterator} instead in order to reduce the coupling between this class and
   * Chart. See the note above about the fact that this interface should not be used internally by
   * the Chart class because it will not be performant.
   * @return a {@link java.util.List} of {@link org.apache.joshua.decoder.segment_file.ConstraintRule}'s
   */
  List<ConstraintRule> rules();
}
