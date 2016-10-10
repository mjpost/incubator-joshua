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
package org.apache.joshua.decoder.ff;

/***
 * @author Gideon Wenniger
 */

import java.util.List;

import org.apache.joshua.decoder.JoshuaConfiguration;
import org.apache.joshua.decoder.chart_parser.SourcePath;
import org.apache.joshua.decoder.ff.state_maintenance.DPState;
import org.apache.joshua.decoder.ff.tm.Rule;
import org.apache.joshua.decoder.hypergraph.HGNode;
import org.apache.joshua.decoder.segment_file.Sentence;
import org.apache.joshua.util.ListUtil;

public class LabelSubstitutionFF extends StatelessFF {
  private static final String MATCH_SUFFIX = "MATCH";
  private static final String NO_MATCH_SUFFIX = "NOMATCH";

  public LabelSubstitutionFF(FeatureVector weights, String[] args, JoshuaConfiguration config) {
    super(weights, "LabelSubstitution", args, config);
  }

  public String getLowerCasedFeatureName() {
    return name.toLowerCase();
  }

  public String getMatchFeatureSuffix(String ruleNonterminal, String substitutionNonterminal) {
    if (ruleNonterminal.equals(substitutionNonterminal)) {
      return MATCH_SUFFIX;
    } else {
      return NO_MATCH_SUFFIX;
    }
  }

  public static String getSubstitutionSuffix(String ruleNonterminal, String substitutionNonterminal) {
    return substitutionNonterminal + "_substitutes_" + ruleNonterminal;
  }

  private String computeLabelMatchingFeature(String ruleNonterminal,
      String substitutionNonterminal) {
    String result = getLowerCasedFeatureName() + "_";
    result += getMatchFeatureSuffix(ruleNonterminal, substitutionNonterminal);
    return result;
  }

  private String computeLabelSubstitutionFeature(String ruleNonterminal,
      String substitutionNonterminal) {
    String result = getLowerCasedFeatureName() + "_";
    result += getSubstitutionSuffix(ruleNonterminal, substitutionNonterminal);
    return result;
  }

  private static String getRuleLabelsDescriptorString(Rule rule) {
    String result = "";
    String leftHandSide = RulePropertiesQuerying.getLHSAsString(rule);
    List<String> ruleSourceNonterminals = RulePropertiesQuerying
        .getRuleSourceNonterminalStrings(rule);
    boolean isInverting = rule.isInverting();
    result += "<LHS>" + leftHandSide + "</LHS>";
    result += "_<Nont>";
    result += ListUtil.stringListStringWithoutBracketsCommaSeparated(ruleSourceNonterminals);
    result += "</Nont>";
    if(isInverting)
    {  
      result += "_INV";
    }
    else
    {
      result += "_MONO";
    }
    
    return result;
  }

  private static String getSubstitutionsDescriptorString(List<HGNode> tailNodes) {
    String result = "_<Subst>";
    List<String> substitutionNonterminals = RulePropertiesQuerying
        .getSourceNonterminalStrings(tailNodes);
    result += ListUtil.stringListStringWithoutBracketsCommaSeparated(substitutionNonterminals);
    result += "</Subst>";
    return result;
  }

  public final String getGapLabelsForRuleSubstitutionSuffix(Rule rule, List<HGNode> tailNodes) {
    String result = getLowerCasedFeatureName() + "_";
    result += getRuleLabelsDescriptorString(rule);
    result += getSubstitutionsDescriptorString(tailNodes);
    return result;
  }

  @Override
  public DPState compute(Rule rule, List<HGNode> tailNodes, int i, int j, SourcePath sourcePath,
      Sentence sentence, Accumulator acc) {
    if (rule != null && (tailNodes != null)) {

      List<String> ruleSourceNonterminals = RulePropertiesQuerying
          .getRuleSourceNonterminalStrings(rule);
      List<String> substitutionNonterminals = RulePropertiesQuerying
          .getSourceNonterminalStrings(tailNodes);
      // Assert.assertEquals(ruleSourceNonterminals.size(), substitutionNonterminals.size());
      for (int nonterinalIndex = 0; nonterinalIndex < ruleSourceNonterminals.size(); nonterinalIndex++) {
        String ruleNonterminal = ruleSourceNonterminals.get(nonterinalIndex);
        String substitutionNonterminal = substitutionNonterminals.get(nonterinalIndex);
        acc.add(computeLabelMatchingFeature(ruleNonterminal, substitutionNonterminal), 1);
        acc.add(computeLabelSubstitutionFeature(ruleNonterminal, substitutionNonterminal), 1);
      }
      acc.add(getGapLabelsForRuleSubstitutionSuffix(rule, tailNodes), 1);
    }
    return null;
  }
}
