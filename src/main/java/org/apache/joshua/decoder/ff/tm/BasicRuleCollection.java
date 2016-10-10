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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.apache.joshua.decoder.ff.FeatureFunction;

/**
 * Basic collection of translation rules.
 * 
 * @author Lane Schwartz
 * @author Zhifei Li
 */
public class BasicRuleCollection implements RuleCollection {

  /**
   * Indicates whether the rules in this collection have been sorted based on the latest feature
   * function values.
   */
  protected boolean sorted;

  /** List of rules stored in this collection. */
  protected final List<Rule> rules;

  /** Number of nonterminals in the source pattern. */
  protected int arity;

  /**
   * Sequence of terminals and nonterminals in the source pattern.
   */
  protected int[] sourceTokens;

  /**
   * Constructs an initially empty rule collection.
   * 
   * @param arity Number of nonterminals in the source pattern
   * @param sourceTokens Sequence of terminals and nonterminals in the source pattern
   */
  public BasicRuleCollection(int arity, int[] sourceTokens) {
    this.rules = new ArrayList<>();
    this.sourceTokens = sourceTokens;
    this.arity = arity;
    this.sorted = false;
  }

  public int getArity() {
    return this.arity;
  }

  /**
   * Returns a list of the rules, without ensuring that they are first sorted.
   */
  @Override
  public List<Rule> getRules() {
    return this.rules;
  }
  
  @Override
  public boolean isSorted() {
    return sorted;
  }

  /**
   * Return a list of rules sorted according to their estimated model costs.
   */
  @Override
  public synchronized List<Rule> getSortedRules(List<FeatureFunction> models) {
    if (! isSorted()) {
      for (Rule rule: getRules())
        rule.estimateRuleCost(models);

      Collections.sort(rules, Rule.EstimatedCostComparator);
      this.sorted = true;      
    }
    
    return this.rules;
  }

  public int[] getSourceSide() {
    return this.sourceTokens;
  }
}
