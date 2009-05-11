/* This file is part of the Joshua Machine Translation System.
 * 
 * Joshua is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free
 * Software Foundation, Inc., 59 Temple Place, Suite 330, Boston,
 * MA 02111-1307 USA
 */
package joshua.corpus.suffix_array;

import joshua.corpus.Corpus;
import joshua.corpus.Phrase;
import joshua.corpus.RuleExtractor;
import joshua.corpus.alignment.Alignments;
import joshua.corpus.lexprob.LexicalProbabilities;
import joshua.decoder.ff.tm.Grammar;
import joshua.decoder.ff.tm.GrammarFactory;
import joshua.prefix_tree.HierarchicalRuleExtractor;
import joshua.prefix_tree.PrefixTree;

public class SAGrammarFactory implements GrammarFactory {

	private final Suffixes sourceSuffixArray;
	private final Corpus targetCorpus;
	private final Alignments alignments;
	private final LexicalProbabilities lexProbs;
	
	private final int maxPhraseSpan;
	private final int maxPhraseLength;
	private final int maxNonterminals;
	private final int minNonterminalSpan;
	
	private final RuleExtractor ruleExtractor;
	
//	/** TODO This variable is never read - perhaps it should be removed? */
//	@SuppressWarnings("unused")
//	private final int spanLimit;
	
	/**
	 * Constructs a factory capable of getting a grammar backed by a suffix array.
	 * 
	 * @param sourceSuffixArray
	 * @param targetCorpus
	 * @param alignments
	 * @param maxPhraseSpan
	 * @param maxPhraseLength
	 * @param maxNonterminals
	 */
	public SAGrammarFactory(Suffixes sourceSuffixArray, Corpus targetCorpus, Alignments alignments, LexicalProbabilities lexProbs, int sampleSize, int maxPhraseSpan, int maxPhraseLength, int maxNonterminals, int minNonterminalSpan) {
		this.sourceSuffixArray = sourceSuffixArray;
		this.targetCorpus      = targetCorpus;
		this.alignments        = alignments;
		this.lexProbs          = lexProbs;
		this.maxPhraseSpan     = maxPhraseSpan;
		this.maxPhraseLength   = maxPhraseLength;
		this.maxNonterminals   = maxNonterminals;
		this.minNonterminalSpan = minNonterminalSpan;
//		this.spanLimit         = spanLimit;
		this.ruleExtractor = new HierarchicalRuleExtractor(sourceSuffixArray, targetCorpus, alignments, lexProbs, sampleSize, maxPhraseSpan, maxPhraseLength, maxNonterminals, minNonterminalSpan);
		
	}
	
	
	/** 
	 * Extracts a grammar which contains only those rules
	 * relevant for translating the specified sentence.
	 * 
	 * @param sentence A sentence to be translated
	 * @return a grammar, structured as a trie, that represents
	 *         a set of translation rules
	 */
	public Grammar getGrammarForSentence(Phrase sentence) {
		
		int[] words = new int[sentence.size()];
		for (int i = 0; i < words.length; i++) {
			words[i] = sentence.getWordID(i);
		}
		
		PrefixTree prefixTree = new PrefixTree(sourceSuffixArray, targetCorpus, alignments, sourceSuffixArray.getVocabulary(), lexProbs, ruleExtractor, maxPhraseSpan, maxPhraseLength, maxNonterminals, minNonterminalSpan);
		prefixTree.add(words);
		
		return prefixTree.getRoot();
	}
	
}