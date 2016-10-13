#!/bin/bash

# Trains a mallet model on source-annotated data of the form
#
# source_word target_word feat:val feat:val feat:val

if [[ -z $2 ]]; then
  echo "Usage: train-mallet.sh [-d train_data] [-m model] [-t test_data]"
  echo "If -d is given, a model will be trained, and written out if -m is given."
  echo "If -m is given without -t, the model will be loaded."
  echo "Use -t to specify a test file"
  exit
fi

LOG_PROPERTIES=$JOSHUA/lib/mallet.properties

java -mx16g -cp $JOSHUA/target/joshua-*-jar-with-dependencies.jar -Djava.util.logging.config.file=$LOG_PROPERTIES org.apache.joshua.decoder.ff.LexicalSharpener "$@"
