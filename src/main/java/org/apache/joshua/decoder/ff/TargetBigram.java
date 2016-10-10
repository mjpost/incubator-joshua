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

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;

import org.apache.joshua.corpus.Vocabulary;
import org.apache.joshua.decoder.JoshuaConfiguration;
import org.apache.joshua.decoder.chart_parser.SourcePath;
import org.apache.joshua.decoder.ff.state_maintenance.DPState;
import org.apache.joshua.decoder.ff.state_maintenance.NgramDPState;
import org.apache.joshua.decoder.ff.tm.Rule;
import org.apache.joshua.decoder.hypergraph.HGNode;
import org.apache.joshua.decoder.segment_file.Sentence;
import org.apache.joshua.util.FormatUtils;
import org.apache.joshua.util.io.LineReader;

/***
 * The RuleBigram feature is an indicator feature that counts target word bigrams that are created when
 * a rule is applied. It accepts three parameters:
 *
 * -vocab /path/to/vocab
 *
 *  The path to a vocabulary, where each line is of the format ID WORD COUNT.
 *
 * -threshold N
 *
 *  Mask to UNK all words whose COUNT is less than N.
 *
 * -top-n N
 *
 *  Only use the top N words.
 */

public class TargetBigram extends StatefulFF {

  private HashSet<String> vocab = null;
  private int maxTerms = 1000000;
  private int threshold = 0;

  public TargetBigram(FeatureVector weights, String[] args, JoshuaConfiguration config) {
    super(weights, "TargetBigram", args, config);

    if (parsedArgs.containsKey("threshold"))
      threshold = Integer.parseInt(parsedArgs.get("threshold"));

    if (parsedArgs.containsKey("top-n"))
      maxTerms = Integer.parseInt(parsedArgs.get("top-n"));

    if (parsedArgs.containsKey("vocab")) {
      loadVocab(parsedArgs.get("vocab"));
    }
  }

  /**
   * Load vocabulary items passing the 'threshold' and 'top-n' filters.
   *
   * @param filename
   */
  private void loadVocab(String filename) {
    this.vocab = new HashSet<>();
    this.vocab.add("<s>");
    this.vocab.add("</s>");
    try(LineReader lineReader = new LineReader(filename);) {
      for (String line: lineReader) {
        if (lineReader.lineno() > maxTerms)
          break;

        String[] tokens = line.split("\\s+");
        String word = tokens[1];
        int count = Integer.parseInt(tokens[2]);

        if (count >= threshold)
          vocab.add(word);
      }

    } catch (IOException e) {
      throw new RuntimeException(String.format(
          "* FATAL: couldn't load TargetBigram vocabulary '%s'", filename), e);
    }
  }

  @Override
  public DPState compute(Rule rule, List<HGNode> tailNodes, int spanStart, int spanEnd,
      SourcePath sourcePath, Sentence sentence, Accumulator acc) {

    int[] enWords = rule.getEnglish();

    int left = -1;
    int right = -1;

    List<String> currentNgram = new LinkedList<>();
    for (int curID : enWords) {
      if (FormatUtils.isNonterminal(curID)) {
        int index = -(curID + 1);
        NgramDPState state = (NgramDPState) tailNodes.get(index).getDPState(stateIndex);
        int[] leftContext = state.getLeftLMStateWords();
        int[] rightContext = state.getRightLMStateWords();

        // Left context.
        for (int token : leftContext) {
          currentNgram.add(getWord(token));
          if (left == -1)
            left = token;
          right = token;
          if (currentNgram.size() == 2) {
            String ngram = join(currentNgram);
            acc.add(String.format("%s_%s", name, ngram), 1);
            //            System.err.println(String.format("ADDING %s_%s", name, ngram));
            currentNgram.remove(0);
          }
        }
        // Replace right context.
        int tSize = currentNgram.size();
        for (int i = 0; i < rightContext.length; i++)
          currentNgram.set(tSize - rightContext.length + i, getWord(rightContext[i]));

      } else { // terminal words
        currentNgram.add(getWord(curID));
        if (left == -1)
          left = curID;
        right = curID;
        if (currentNgram.size() == 2) {
          String ngram = join(currentNgram);
          acc.add(String.format("%s_%s", name, ngram), 1);
          //          System.err.println(String.format("ADDING %s_%s", name, ngram));
          currentNgram.remove(0);
        }
      }
    }

    //    System.err.println(String.format("RULE %s -> state %s", rule.getRuleString(), state));
    return new NgramDPState(new int[] { left }, new int[] { right });
  }

  /**
   * Returns the word after comparing against the private vocabulary (if set).
   *
   * @param curID
   * @return the word
   */
  private String getWord(int curID) {
    String word = Vocabulary.word(curID);

    if (vocab != null && ! vocab.contains(word)) {
      return "UNK";
    }

    return word;
  }

  /**
   * We don't compute a future cost.
   */
  @Override
  public float estimateFutureCost(Rule rule, DPState state, Sentence sentence) {
    return 0.0f;
  }

  /**
   * There is nothing to be done here, since &lt;s&gt; and &lt;/s&gt; are included in rules that are part
   * of the grammar. We simply return the DP state of the tail node.
   */
  @Override
  public DPState computeFinal(HGNode tailNode, int i, int j, SourcePath sourcePath,
      Sentence sentence, Accumulator acc) {

    return tailNode.getDPState(stateIndex);
  }

  /**
   * TargetBigram features are only computed across hyperedges, so there is nothing to be done here.
   */
  @Override
  public float estimateCost(Rule rule) {
    return 0.0f;
  }

  /**
   * Join a list with the _ character. I am sure this is in a library somewhere.
   *
   * @param list a list of strings
   * @return the joined String
   */
  private String join(List<String> list) {
    StringBuilder sb = new StringBuilder();
    for (String item : list) {
      sb.append(item).append("_");
    }

    return sb.substring(0, sb.length() - 1);
  }
}
