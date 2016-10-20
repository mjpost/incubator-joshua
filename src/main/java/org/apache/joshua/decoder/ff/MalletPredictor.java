package org.apache.joshua.decoder.ff;

import java.io.Serializable;
import java.io.StringReader;
import java.util.ArrayList;

import cc.mallet.classify.Classification;
import cc.mallet.classify.Classifier;
import cc.mallet.classify.ClassifierTrainer;
import cc.mallet.classify.MaxEntTrainer;
import cc.mallet.pipe.Pipe;
import cc.mallet.pipe.SerialPipes;
import cc.mallet.pipe.SvmLight2FeatureVectorAndLabel;
import cc.mallet.pipe.Target2Label;
import cc.mallet.pipe.iterator.CsvIterator;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MalletPredictor implements Serializable {
    
    private static final long serialVersionUID = 1L;

    private static final Logger LOG = LoggerFactory.getLogger(MalletPredictor.class);

    private SerialPipes pipes = null;
    private InstanceList instances = null;
    private String sourceWord = null;
    private ArrayList<String> examples = null;
    private Classifier classifier = null;
    
    public MalletPredictor(String word, ArrayList<String> examples, boolean preTrain) {
      this.sourceWord = word;
      this.examples = examples;
      
      if (preTrain)
        train();
    }
    
    public MalletPredictor(String word, ArrayList<String> examples) {
      this(word, examples, true);
    }

    /**
       * Returns a Classification object a list of features. Uses "which" to determine which classifier
       * to use.
       *   
       * @param which the classifier to use
       * @param features the set of features
       * @return
       */
    public Classification predict(String outcome, String features) {
      Instance instance = new Instance(features, outcome, null, null);
//      SYSTEM.ERR.PRINTLN("PREDICT TARGETWORD = " + (STRING) INSTANCE.GETTARGET());
//      SYSTEM.ERR.PRINTLN("PREDICT FEATURES = " + (STRING) INSTANCE.GETDATA());

      if (classifier == null)
        train();

      Classification result = (Classification) classifier.classify(pipes.instanceFrom(instance));
      return result;
    }

    public void train() {
      ArrayList<Pipe> pipeList = new ArrayList<Pipe>();

      // I don't know if this is needed
      pipeList.add(new Target2Label());
      // Convert custom lines to Instance objects (svmLight2FeatureVectorAndLabel not versatile enough)
      pipeList.add(new SvmLight2FeatureVectorAndLabel());
      // Validation
//      pipeList.add(new PrintInputAndTarget());
      
      // name: english word
      // data: features (FeatureVector)
      // target: foreign inflection
      // source: null

      pipes = new SerialPipes(pipeList);
      instances = new InstanceList(pipes);
      
      /* I know, this is *terrible*, but I need it to work *now* */
      String exampleList = "";
      for (String example: examples)
        exampleList += example + "\n";
      StringReader reader = new StringReader(exampleList);

      // Constructs an instance with everything shoved into the data field
      instances.addThruPipe(new CsvIterator(reader, "(\\S+)\\s+(.*)", 2, -1, 1));

      long startTime = System.currentTimeMillis();
      LOG.info("MalletPredictor: Training a model for '{}' from {} examples",
          sourceWord, examples.size());
      System.err.flush();

      ClassifierTrainer trainer = new MaxEntTrainer();
      classifier = trainer.train(instances);
      
      String outcomes = "";
      for (Object outcome: pipes.getTargetAlphabet().toArray()) {
        outcomes += outcome.toString() + " ";
      }
      LOG.info("-> {} outcomes in {}s: ", getNumOutcomes(),(System.currentTimeMillis() - startTime) / 1000.0, outcomes );
      System.err.flush();

      // Decoder.LOG(1, String.format("MalletPredictor: Trained model for '%s' over %d outcomes from %d examples in %.1fs", 
      //     sourceWord, getNumOutcomes(), examples.size(), (System.currentTimeMillis() - startTime) / 1000.0 ));
    }

    /**
     * Returns the number of distinct outcomes. Requires the model to have been trained!
     * 
     * @return
     */
    public int getNumOutcomes() {
      if (classifier == null)
        train();
      return pipes.getTargetAlphabet().size();
    }
  }

