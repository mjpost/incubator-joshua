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
package org.apache.joshua.decoder.ff.tm;

import java.util.List;

import org.apache.joshua.decoder.ff.FeatureFunction;

/**
 * Grammar is a class for wrapping a trie of TrieGrammar in order to store holistic metadata.
 * 
 * @author wren ng thornton wren@users.sourceforge.net
 * @author Zhifei Li, zhifei.work@gmail.com
 */
public interface Grammar {

  /**
   * Gets the root of the <code>Trie</code> backing this grammar.
   * <p>
   * <em>Note</em>: This method should run as a small constant-time function.
   * 
   * @return the root of the <code>Trie</code> backing this grammar
   */
  Trie getTrieRoot();

  /**
   * After calling this method, the rules in this grammar are guaranteed to be sorted based on the
   * latest feature function values.
   * <p>
   * Cube-pruning requires that the grammar be sorted based on the latest feature functions.
   * 
   * @param models list of {@link org.apache.joshua.decoder.ff.FeatureFunction}'s
   */
  void sortGrammar(List<FeatureFunction> models);

  /**
   * Determines whether the rules in this grammar have been sorted based on the latest feature
   * function values.
   * <p>
   * This method is needed for the cube-pruning algorithm.
   * 
   * @return <code>true</code> if the rules in this grammar have been sorted based on the latest
   *         feature function values, <code>false</code> otherwise
   */
  boolean isSorted();

  /**
   * Returns whether this grammar has any valid rules for covering a particular span of a sentence.
   * Hiero's "glue" grammar will only say True if the span is longer than our span limit, and is
   * anchored at startIndex==0. Hiero's "regular" grammar will only say True if the span is less
   * than the span limit. Other grammars, e.g. for rule-based systems, may have different behaviors.
   * 
   * @param startIndex Indicates the starting index of a phrase in a source input phrase, or a
   *          starting node identifier in a source input lattice
   * @param endIndex Indicates the ending index of a phrase in a source input phrase, or an ending
   *          node identifier in a source input lattice
   * @param pathLength Length of the input path in a source input lattice. If a source input phrase
   *          is used instead of a lattice, this value will likely be ignored by the underlying
   *          implementation, but would normally be defined as <code>endIndex-startIndex</code>
   * @return true if there is a rule for this span
   */
  boolean hasRuleForSpan(int startIndex, int endIndex, int pathLength);

  /**
   * Gets the number of rules stored in the grammar.
   * 
   * @return the number of rules stored in the grammar
   */
  int getNumRules();
  
  /**
   * Returns the number of dense features.
   * 
   * @return the number of dense features
   */
  int getNumDenseFeatures();

  /**
   * Return the grammar's owner.
   * @return grammar owner
   */
  OwnerId getOwner();

  /**
   * Return the maximum source phrase length (terminals + nonterminals)
   * @return the maximum source phrase length
   */
  int getMaxSourcePhraseLength();
  
  /**
   * Add an OOV rule for the requested word for the grammar.
   * 
   * @param word input word to add rules to
   * @param featureFunctions a {@link java.util.List} of {@link org.apache.joshua.decoder.ff.FeatureFunction}'s
   */
  void addOOVRules(int word, List<FeatureFunction> featureFunctions);
  
  /**
   * Add a rule to the grammar.
   *
   * @param rule the {@link org.apache.joshua.decoder.ff.tm.Rule}
   */
  void addRule(Rule rule);
}
