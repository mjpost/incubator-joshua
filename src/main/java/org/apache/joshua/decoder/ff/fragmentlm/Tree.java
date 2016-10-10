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
package org.apache.joshua.decoder.ff.fragmentlm;

import java.io.IOException;
import java.io.Serializable;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.apache.joshua.corpus.Vocabulary;
import org.apache.joshua.decoder.ff.fragmentlm.Trees.PennTreeReader;
import org.apache.joshua.decoder.ff.tm.Rule;
import org.apache.joshua.decoder.hypergraph.HGNode;
import org.apache.joshua.decoder.hypergraph.HyperEdge;
import org.apache.joshua.decoder.hypergraph.KBestExtractor.DerivationState;
import org.apache.joshua.util.io.LineReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Represent phrase-structure trees, with each node consisting of a label and a list of children.
 * Borrowed from the Berkeley Parser, and extended to allow the representation of tree fragments in
 * addition to complete trees (the BP requires terminals to be immediately governed by a
 * preterminal). To distinguish terminals from nonterminals in fragments, the former must be
 * enclosed in double-quotes when read in.
 *
 * @author Dan Klein
 * @author Matt Post post@cs.jhu.edu
 */
public class Tree implements Serializable {

  private static final Logger LOG = LoggerFactory.getLogger(Tree.class);
  private static final long serialVersionUID = 1L;

  protected int label;

  /* Marks a frontier node as a terminal (as opposed to a nonterminal). */
  boolean isTerminal = false;

  /*
   * Marks the root and frontier nodes of a fragment. Useful for denoting fragment derivations in
   * larger trees.
   */
  boolean isBoundary = false;

  /* A list of the node's children. */
  List<Tree> children;

  /* The maximum distance from the root to any of the frontier nodes. */
  int depth = -1;

  /* The number of lexicalized items among the tree's frontier. */
  private int numLexicalItems = -1;

  /*
   * This maps the flat right-hand sides of Joshua rules to the tree fragments they were derived
   * from. It is used to lookup the fragment that language model fragments should be match against.
   * For example, if the target (English) side of your rule is
   *
   * [NP,1] said [SBAR,2]
   *
   * we will retrieve the unflattened fragment
   *
   * (S NP (VP (VBD said) SBAR))
   *
   * which presumably was the fronter fragment used to derive the translation rule. With this in
   * hand, we can iterate through our store of language model fragments to match them against this,
   * following tail nodes if necessary.
   */
  public static final HashMap<String, String> rulesToFragmentStrings = new HashMap<>();

  public Tree(String label, List<Tree> children) {
    setLabel(label);
    this.children = children;
  }

  public Tree(String label) {
    setLabel(label);
    this.children = Collections.emptyList();
  }

  public Tree(int label2, ArrayList<Tree> newChildren) {
    this.label = label2;
    this.children = newChildren;
  }

  public void setChildren(List<Tree> c) {
    this.children = c;
  }

  public List<Tree> getChildren() {
    return children;
  }

  public int getLabel() {
    return label;
  }

  /**
   * Computes the depth-one rule rooted at this node. If the node has no children, null is returned.
   *
   * @return string representation of the rule
   */
  public String getRule() {
    if (isLeaf()) {
      return null;
    }
    StringBuilder ruleString = new StringBuilder("(" + Vocabulary.word(getLabel()));
    for (Tree child : getChildren()) {
      ruleString.append(" ").append(Vocabulary.word(child.getLabel()));
    }
    return ruleString.toString();
  }

  /*
   * Boundary nodes are used externally to mark merge points between different fragments. This is
   * separate from the internal ( (substitution point) denotation.
   */
  public boolean isBoundary() {
    return isBoundary;
  }

  public void setBoundary(boolean b) {
    this.isBoundary = b;
  }

  public boolean isTerminal() {
    return isTerminal;
  }

  public boolean isLeaf() {
    return getChildren().isEmpty();
  }

  public boolean isPreTerminal() {
    return getChildren().size() == 1 && getChildren().get(0).isLeaf();
  }

  public List<Tree> getNonterminalYield() {
    List<Tree> yield = new ArrayList<>();
    appendNonterminalYield(this, yield);
    return yield;
  }

  public List<Tree> getYield() {
    List<Tree> yield = new ArrayList<>();
    appendYield(this, yield);
    return yield;
  }

  public List<Tree> getTerminals() {
    List<Tree> yield = new ArrayList<>();
    appendTerminals(this, yield);
    return yield;
  }

  private static void appendTerminals(Tree tree, List<Tree> yield) {
    if (tree.isLeaf()) {
      yield.add(tree);
      return;
    }
    for (Tree child : tree.getChildren()) {
      appendTerminals(child, yield);
    }
  }

  /**
   * Clone the structure of the tree.
   *
   * @return a cloned tree
   */
  public Tree shallowClone() {
    ArrayList<Tree> newChildren = new ArrayList<>(children.size());
    for (Tree child : children) {
      newChildren.add(child.shallowClone());
    }

    Tree newTree = new Tree(label, newChildren);
    newTree.setIsTerminal(isTerminal());
    newTree.setBoundary(isBoundary());
    return newTree;
  }

  private void setIsTerminal(boolean terminal) {
    isTerminal = terminal;
  }

  private static void appendNonterminalYield(Tree tree, List<Tree> yield) {
    if (tree.isLeaf() && !tree.isTerminal()) {
      yield.add(tree);
      return;
    }
    for (Tree child : tree.getChildren()) {
      appendNonterminalYield(child, yield);
    }
  }

  private static void appendYield(Tree tree, List<Tree> yield) {
    if (tree.isLeaf()) {
      yield.add(tree);
      return;
    }
    for (Tree child : tree.getChildren()) {
      appendYield(child, yield);
    }
  }

  public List<Tree> getPreTerminalYield() {
    List<Tree> yield = new ArrayList<>();
    appendPreTerminalYield(this, yield);
    return yield;
  }

  private static void appendPreTerminalYield(Tree tree, List<Tree> yield) {
    if (tree.isPreTerminal()) {
      yield.add(tree);
      return;
    }
    for (Tree child : tree.getChildren()) {
      appendPreTerminalYield(child, yield);
    }
  }

  /**
   * A tree is lexicalized if it has terminal nodes among the leaves of its frontier. For normal
   * trees this is always true since they bottom out in terminals, but for fragments, this may or
   * may not be true.
   *
   * @return true if the tree is lexicalized
   */
  public boolean isLexicalized() {
    if (this.numLexicalItems < 0) {
      if (isTerminal())
        this.numLexicalItems = 1;
      else {
        this.numLexicalItems = 0;
        children.stream().filter(child -> child.isLexicalized())
            .forEach(child -> this.numLexicalItems += 1);
      }
    }

    return (this.numLexicalItems > 0);
  }

  /**
   * The depth of a tree is the maximum distance from the root to any of the frontier nodes.
   *
   * @return the tree depth
   */
  public int getDepth() {
    if (this.depth >= 0)
      return this.depth;

    if (isLeaf()) {
      this.depth = 0;
    } else {
      int maxDepth = 0;
      for (Tree child : children) {
        int depth = child.getDepth();
        if (depth > maxDepth)
          maxDepth = depth;
      }
      this.depth = maxDepth + 1;
    }
    return this.depth;
  }

  public List<Tree> getAtDepth(int depth) {
    List<Tree> yield = new ArrayList<>();
    appendAtDepth(depth, this, yield);
    return yield;
  }

  private static void appendAtDepth(int depth, Tree tree, List<Tree> yield) {
    if (depth < 0)
      return;
    if (depth == 0) {
      yield.add(tree);
      return;
    }
    for (Tree child : tree.getChildren()) {
      appendAtDepth(depth - 1, child, yield);
    }
  }

  public void setLabel(String label) {
    if (label.length() >= 3 && label.startsWith("\"") && label.endsWith("\"")) {
      this.isTerminal = true;
      label = label.substring(1, label.length() - 1);
    }

    this.label = Vocabulary.id(label);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    toStringBuilder(sb);
    return sb.toString();
  }

  /**
   * Removes the quotes around terminals. Note that the resulting tree could not be read back
   * in by this class, since unquoted leaves are interpreted as nonterminals.
   *
   * @return unquoted string
   */
  public String unquotedString() {
    return toString().replaceAll("\"", "");
  }

  public String escapedString() {
    return toString().replaceAll(" ", "_");
  }

  public void toStringBuilder(StringBuilder sb) {
    if (!isLeaf())
      sb.append('(');

    if (isTerminal())
      sb.append(String.format("\"%s\"", Vocabulary.word(getLabel())));
    else
      sb.append(Vocabulary.word(getLabel()));

    if (!isLeaf()) {
      for (Tree child : getChildren()) {
        sb.append(' ');
        child.toStringBuilder(sb);
      }
      sb.append(')');
    }
  }

  /**
   * Get the set of all subtrees inside the tree by returning a tree rooted at each node. These are
   * <i>not</i> copies, but all share structure. The tree is regarded as a subtree of itself.
   *
   * @return the <code>Set</code> of all subtrees in the tree.
   */
  public Set<Tree> subTrees() {
    return (Set<Tree>) subTrees(new HashSet<>());
  }

  /**
   * Get the list of all subtrees inside the tree by returning a tree rooted at each node. These are
   * <i>not</i> copies, but all share structure. The tree is regarded as a subtree of itself.
   *
   * @return the <code>List</code> of all subtrees in the tree.
   */
  public List<Tree> subTreeList() {
    return (List<Tree>) subTrees(new ArrayList<>());
  }

  /**
   * Add the set of all subtrees inside a tree (including the tree itself) to the given
   * <code>Collection</code>.
   *
   * @param n A collection of nodes to which the subtrees will be added
   * @return The collection parameter with the subtrees added
   */
  public Collection<Tree> subTrees(Collection<Tree> n) {
    n.add(this);
    List<Tree> kids = getChildren();
    for (Tree kid : kids) {
      kid.subTrees(n);
    }
    return n;
  }

  /**
   * Returns an iterator over the nodes of the tree. This method implements the
   * <code>iterator()</code> method required by the <code>Collections</code> interface. It does a
   * preorder (children after node) traversal of the tree. (A possible extension to the class at
   * some point would be to allow different traversal orderings via variant iterators.)
   *
   * @return An interator over the nodes of the tree
   */
  public TreeIterator iterator() {
    return new TreeIterator();
  }

  private class TreeIterator implements Iterator<Tree> {

    private final List<Tree> treeStack;

    private TreeIterator() {
      treeStack = new ArrayList<>();
      treeStack.add(Tree.this);
    }

    @Override
    public boolean hasNext() {
      return (!treeStack.isEmpty());
    }

    @Override
    public Tree next() {
      int lastIndex = treeStack.size() - 1;
      Tree tr = treeStack.remove(lastIndex);
      List<Tree> kids = tr.getChildren();
      // so that we can efficiently use one List, we reverse them
      for (int i = kids.size() - 1; i >= 0; i--) {
        treeStack.add(kids.get(i));
      }
      return tr;
    }

    /**
     * Not supported
     */
    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }

  }

  public boolean hasUnaryChain() {
    return hasUnaryChainHelper(this, false);
  }

  private boolean hasUnaryChainHelper(Tree tree, boolean unaryAbove) {
    boolean result = false;
    if (tree.getChildren().size() == 1) {
      if (unaryAbove)
        return true;
      else if (tree.getChildren().get(0).isPreTerminal())
        return false;
      else
        return hasUnaryChainHelper(tree.getChildren().get(0), true);
    } else {
      for (Tree child : tree.getChildren()) {
        if (!child.isPreTerminal())
          result = result || hasUnaryChainHelper(child, false);
      }
    }
    return result;
  }

  /**
   * Inserts the SOS (and EOS) symbols into a parse tree, attaching them as a left (right) sibling
   * to the leftmost (rightmost) pre-terminal in the tree. This facilitates using trees as language
   * models. The arguments have to be passed in to preserve Java generics, even though this is only
   * ever used with String versions.
   *
   * @param sos presumably "&lt;s&gt;"
   * @param eos presumably "&lt;/s&gt;"
   */
  public void insertSentenceMarkers(String sos, String eos) {
    insertSentenceMarker(sos, 0);
    insertSentenceMarker(eos, -1);
  }

  public void insertSentenceMarkers() {
    insertSentenceMarker("<s>", 0);
    insertSentenceMarker("</s>", -1);
  }

  /**
   *
   * @param symbol the marker to insert
   * @param pos the position at which to insert
   */
  private void insertSentenceMarker(String symbol, int pos) {

    if (isLeaf() || isPreTerminal())
      return;

    List<Tree> children = getChildren();
    int index = (pos == -1) ? children.size() - 1 : pos;
    if (children.get(index).isPreTerminal()) {
      if (pos == -1)
        children.add(new Tree(symbol));
      else
        children.add(pos, new Tree(symbol));
    } else {
      children.get(index).insertSentenceMarker(symbol, pos);
    }
  }

  /**
   * This is a convenience function for producing a fragment from its string representation.
   *
   * @param ptbStr input string from which to produce a fragment
   * @return the fragment
   */
  public static Tree fromString(String ptbStr) {
    PennTreeReader reader = new PennTreeReader(new StringReader(ptbStr));
    return reader.next();
  }

  public static Tree getFragmentFromYield(String yield) {
    String fragmentString = rulesToFragmentStrings.get(yield);
    if (fragmentString != null)
      return fromString(fragmentString);

    return null;
  }

  public static void readMapping(String fragmentMappingFile) {
    /* Read in the rule / fragments mapping */
    try (LineReader reader = new LineReader(fragmentMappingFile);) {
      for (String line : reader) {
        String[] fields = line.split("\\s+\\|{3}\\s+");
        if (fields.length != 2 || !fields[0].startsWith("(")) {
          LOG.warn("malformed line {}: {}", reader.lineno(), line);
          continue;
        }

        rulesToFragmentStrings.put(fields[1].trim(), fields[0].trim()); // buildFragment(fields[0]));
      }
    } catch (IOException e) {
      throw new RuntimeException(String.format("* WARNING: couldn't read fragment mapping file '%s'",
          fragmentMappingFile), e);
    }
    LOG.info("FragmentLMFF: Read {} mappings from '{}'", rulesToFragmentStrings.size(),
        fragmentMappingFile);
  }

  /**
   * Builds a tree from the kth-best derivation state. This is done by initializing the tree with
   * the internal fragment corresponding to the rule; this will be the top of the tree. We then
   * recursively visit the derivation state objects, following the route through the hypergraph
   * defined by them.
   *
   * This function is like Tree#buildTree(DerivationState, int),
   * but that one simply follows the best incoming hyperedge for each node.
   *
   * @param rule for which corresponding internal fragment can be used to initialize the tree
   * @param derivationStates array of state objects
   * @param maxDepth of route through the hypergraph
   * @return the Tree
   */
  public static Tree buildTree(Rule rule, DerivationState[] derivationStates, int maxDepth) {
    Tree tree = getFragmentFromYield(rule.getEnglishWords());

    if (tree == null) {
      return null;
    }

    tree = tree.shallowClone();

    if (LOG.isDebugEnabled()) {
      LOG.debug("buildTree({})", tree);
      for (int i = 0; i < derivationStates.length; i++) {
        LOG.debug("  -> {}: {}", i, derivationStates[i]);
      }
    }

    List<Tree> frontier = tree.getNonterminalYield();

    /* The English side of a rule is a sequence of integers. Nonnegative integers are word
     * indices in the Vocabulary, while negative indices are used to nonterminals. These negative
     * indices are a *permutation* of the source side nonterminals, which contain the actual
     * nonterminal Vocabulary indices for the nonterminal names. Here, we convert this permutation
     * to a nonnegative 0-based permutation and store it in tailIndices. This is used to index
     * the incoming DerivationState items, which are ordered by the source side.
     */
    ArrayList<Integer> tailIndices = new ArrayList<>();
    int[] englishInts = rule.getEnglish();
    for (int englishInt : englishInts)
      if (englishInt < 0)
        tailIndices.add(-(englishInt + 1));

    /*
     * We now have the tree's yield. The substitution points on the yield should match the
     * nonterminals of the heads of the derivation states. Since we don't know which of the tree's
     * frontier items are terminals and which are nonterminals, we walk through the tail nodes,
     * and then match the label of each against the frontier node labels until we have a match.
     */
    // System.err.println(String.format("WORDS: %s\nTREE: %s", rule.getEnglishWords(), tree));
    for (int i = 0; i < derivationStates.length; i++) {

      Tree frontierTree = frontier.get(tailIndices.get(i));
      frontierTree.setBoundary(true);

      HyperEdge nextEdge = derivationStates[i].edge;
      if (nextEdge != null) {
        DerivationState[] nextStates = null;
        if (nextEdge.getTailNodes() != null && nextEdge.getTailNodes().size() > 0) {
          nextStates = new DerivationState[nextEdge.getTailNodes().size()];
          for (int j = 0; j < nextStates.length; j++)
            nextStates[j] = derivationStates[i].getChildDerivationState(nextEdge, j);
        }
        Tree childTree = buildTree(nextEdge.getRule(), nextStates, maxDepth - 1);

        /* This can be null if there is no entry for the rule in the map */
        if (childTree != null)
          frontierTree.children = childTree.children;
      } else {
        frontierTree.children = tree.children;
      }
    }

    return tree;
  }

  /**
   * <p>Builds a tree from the kth-best derivation state. This is done by initializing the tree with
   * the internal fragment corresponding to the rule; this will be the top of the tree. We then
   * recursively visit the derivation state objects, following the route through the hypergraph
   * defined by them.</p>
   *
   * @param derivationState array of state objects
   * @param maxDepth of route through the hypergraph
   * @return the Tree
   */
  public static Tree buildTree(DerivationState derivationState, int maxDepth) {
    Rule rule = derivationState.edge.getRule();

    Tree tree = getFragmentFromYield(rule.getEnglishWords());

    if (tree == null) {
      return null;
    }

    tree = tree.shallowClone();

    LOG.debug("buildTree({})", tree);

    if (rule.getArity() > 0 && maxDepth > 0) {
      List<Tree> frontier = tree.getNonterminalYield();

      /* The English side of a rule is a sequence of integers. Nonnegative integers are word
       * indices in the Vocabulary, while negative indices are used to nonterminals. These negative
       * indices are a *permutation* of the source side nonterminals, which contain the actual
       * nonterminal Vocabulary indices for the nonterminal names. Here, we convert this permutation
       * to a nonnegative 0-based permutation and store it in tailIndices. This is used to index
       * the incoming DerivationState items, which are ordered by the source side.
       */
      ArrayList<Integer> tailIndices = new ArrayList<>();
      int[] englishInts = rule.getEnglish();
      for (int englishInt : englishInts)
        if (englishInt < 0)
          tailIndices.add(-(englishInt + 1));

      /*
       * We now have the tree's yield. The substitution points on the yield should match the
       * nonterminals of the heads of the derivation states. Since we don't know which of the tree's
       * frontier items are terminals and which are nonterminals, we walk through the tail nodes,
       * and then match the label of each against the frontier node labels until we have a match.
       */
      // System.err.println(String.format("WORDS: %s\nTREE: %s", rule.getEnglishWords(), tree));
      for (int i = 0; i < rule.getArity(); i++) {

        Tree frontierTree = frontier.get(tailIndices.get(i));
        frontierTree.setBoundary(true);

        DerivationState childState = derivationState.getChildDerivationState(derivationState.edge, i);
        Tree childTree = buildTree(childState, maxDepth - 1);

        /* This can be null if there is no entry for the rule in the map */
        if (childTree != null)
          frontierTree.children = childTree.children;
      }
    }

    return tree;
  }

  /**
   * Takes a rule and its tail pointers and recursively constructs a tree (up to maxDepth).
   *
   * This could be implemented by using the other buildTree() function and using the 1-best
   * DerivationState.
   *
   * @param rule {@link org.apache.joshua.decoder.ff.tm.Rule} to be used whilst building the tree
   * @param tailNodes {@link java.util.List} of {@link org.apache.joshua.decoder.hypergraph.HGNode}'s
   * @param maxDepth to go in the tree
   * @return shallow clone of the Tree object
   */
  public static Tree buildTree(Rule rule, List<HGNode> tailNodes, int maxDepth) {
    Tree tree = getFragmentFromYield(rule.getEnglishWords());

    if (tree == null) {
      tree = new Tree(String.format("(%s %s)", Vocabulary.word(rule.getLHS()), rule.getEnglishWords()));
      // System.err.println("COULDN'T FIND " + rule.getEnglishWords());
      // System.err.println("RULE " + rule);
      // for (Entry<String, Tree> pair: rulesToFragments.entrySet())
      // System.err.println("  FOUND " + pair.getKey());

//      return null;
    } else {
      tree = tree.shallowClone();
    }

    if (tree != null && tailNodes != null && tailNodes.size() > 0 && maxDepth > 0) {
      List<Tree> frontier = tree.getNonterminalYield();

      ArrayList<Integer> tailIndices = new ArrayList<>();
      int[] englishInts = rule.getEnglish();
      for (int englishInt : englishInts)
        if (englishInt < 0)
          tailIndices.add(-1 * englishInt - 1);

      /*
       * We now have the tree's yield. The substitution points on the yield should match the
       * nonterminals of the tail nodes. Since we don't know which of the tree's frontier items are
       * terminals and which are nonterminals, we walk through the tail nodes, and then match the
       * label of each against the frontier node labels until we have a match.
       */
      // System.err.println(String.format("WORDS: %s\nTREE: %s", rule.getEnglishWords(), tree));
      for (int i = 0; i < tailNodes.size(); i++) {

        // String lhs = tailNodes.get(i).getLHS().replaceAll("[\\[\\]]", "");
        // System.err.println(String.format("  %d: %s", i, lhs));
        try {
          Tree frontierTree = frontier.get(tailIndices.get(i));
          frontierTree.setBoundary(true);

          HyperEdge edge = tailNodes.get(i).bestHyperedge;
          if (edge != null) {
            Tree childTree = buildTree(edge.getRule(), edge.getTailNodes(), maxDepth - 1);
            /* This can be null if there is no entry for the rule in the map */
            if (childTree != null)
              frontierTree.children = childTree.children;
          } else {
            frontierTree.children = tree.children;
          }
        } catch (IndexOutOfBoundsException e) {
          LOG.error("ERROR at index {}", i);
          LOG.error("RULE: {}  TREE: {}", rule.getEnglishWords(), tree);
          LOG.error("  FRONTIER:");
          for (Tree kid : frontier) {
            LOG.error("    {}", kid);
          }
          throw new RuntimeException(String.format("ERROR at index %d", i), e);
        }
      }
    }

    return tree;
  }

  public static void main(String[] args) throws IOException {
    try (LineReader reader = new LineReader(System.in);) {
      for (String line : reader) {
        try {
          Tree tree = Tree.fromString(line);
          tree.insertSentenceMarkers();
          System.out.println(tree);
        } catch (Exception e) {
          System.out.println("");
        }
      }
    }

    /*
     * Tree fragment = Tree
     * .fromString("(TOP (S (NP (DT the) (NN boy)) (VP (VBD ate) (NP (DT the) (NN food)))))");
     * fragment.insertSentenceMarkers("<s>", "</s>");
     *
     * System.out.println(fragment);
     *
     * ArrayList<Tree> trees = new ArrayList<Tree>(); trees.add(Tree.fromString("(NN \"mat\")"));
     * trees.add(Tree.fromString("(S (NP DT NN) VP)"));
     * trees.add(Tree.fromString("(S (NP (DT \"the\") NN) VP)"));
     * trees.add(Tree.fromString("(S (NP (DT the) NN) VP)"));
     *
     * for (Tree tree : trees) { System.out.println(String.format("TREE %s DEPTH %d LEX? %s", tree,
     * tree.getDepth(), tree.isLexicalized())); }
     */
  }
}
