#!/usr/bin/env python
# -*- coding: utf-8 -*-
#
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
"""
Combine a set of Joshua configuration and resources into a portable
directory tree.
"""
from __future__ import print_function
import argparse
from collections import namedtuple
import logging
import os
import shutil
import signal
import stat
from subprocess import CalledProcessError, Popen, PIPE
import sys

EXAMPLE = r"""
Example invocation:

$JOSHUA/scripts/language-pack/copy_model.py \
  --force \
  --verbose \
  --copy-config-options \
    '-top-n 1 -output-format %S -mark-oovs false' \
  /path/to/origin/directory/test/model/joshua.config \
  /path/to/destination/directory

Note: The options included in the value string for the --copy-config-options
argument can either be Joshua options or options for the
$JOSHUA/scripts/copy-config.pl script. 
"""

JOSHUA_PATH = os.environ.get('JOSHUA')
default_normalizer = os.path.join(JOSHUA_PATH, "scripts/preparation/normalize.pl")
default_tokenizer = os.path.join(JOSHUA_PATH, "scripts/preparation/tokenize.pl")
FILE_TYPE_TOKENS = ['lm', 'tm']
FILE_TYPE_OPTIONS = ['-path', '-lm_file']

OUTPUT_CONFIG_FILE_NAME = 'joshua.config'

def bundle_runner_text(mem):
    text = """#!/bin/bash
# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Joshua decoder invocation script.
# 
# This script takes care of passing arguments to Java and to the
# Joshua decoder. It changes to the current directory so that paths in 
# the config file are relative to the current directory. Usage:
#
# joshua [-m memory] [Joshua arguments]
#
# The default amount of memory is 4gb.

NUM_ARGS=0
E_OPTERROR=1

## memory usage; default is 4 GB
mem=%s

if [[ $1 == "-m" ]]; then
    mem=$2
    shift
    shift
fi

set -u

bundledir=$(dirname $0)
cd $bundledir   # relative paths are now safe....

exec java -mx${mem} \
    -Dfile.encoding=utf8 \
    -Djava.library.path=./lib \
    -cp ./target/joshua-*-jar-with-dependencies.jar \
    org.apache.joshua.decoder.JoshuaDecoder -c joshua.config -v 0 "$@"
""" % mem

    return text


LineParts = namedtuple('LineParts', ['config', 'comment'])


class PathException(Exception):
    """Error involving a specified path"""
    pass


class PackingError(Exception):
    """Error packing a grammar"""
    pass


def error_quit(message):
    logging.error(message)
    sys.exit(2)


def extract_line_parts(line):
    """
    Builds a LineParts object containing tokenized config and comment
    portions of a config line
    """
    config, hash_char, comment = line.partition('#')
    return LineParts(config=config, comment=comment)


def filter_through_copy_config_script(config_text, copy_configs):
    """
    Run the config_text through the 'copy-config.pl' script, applying
    the copy_configs options
    """
    cmd = os.path.join(JOSHUA_PATH, "scripts/copy-config.pl") + ' ' + copy_configs
    logging.info(
        'Running the copy-config.pl script with the command: ' + cmd
    )
    p = Popen(cmd, shell=True, stdin=PIPE, stdout=PIPE)
    result, err = p.communicate(config_text)
    if p.returncode != 0:
        raise CalledProcessError(
            'Encountered an error running the copy-config.pl script.\n'
            '  command: %s\n'
            '  error: %s'
            % (cmd, err or '')
        )
    return result


def line_specifies_path(line):
    """
    Return True if the line matches the format of a joshua.config line
    that specifies a file or directory path, and False otherwise.

    >>> line_specifies_path('tm = thrax glue -1 1/data/tune/grammar.glue')
    True
    >>> line_specifies_path('tm = moses -owner pt -maxspan 0 -path phrase-table.packed -max-source-len 5')
    True
    >>> line_specifies_path('tm = moses pt 0 phrase-table.packed')
    True
    >>> line_specifies_path('feature-function = WordPenalty')
    False
    >>> line_specifies_path('feature_function = Distortion')
    False
    >>> line_specifies_path('feature-function = StateMinimizingLanguageModel -lm_type kenlm -lm_order 5 -lm_file expts/systems/es-en/1/lm.kenlm')
    True
    >>> line_specifies_path('# Foo')
    False
    """
    line_parts = extract_line_parts(line)
    if not line_parts.config:
        return False

    config_tokens = line_parts.config.split()
    if not config_tokens:
        return False

    if config_tokens[0] in FILE_TYPE_TOKENS:
        # The first token is the type of config that would specify a
        # path.
        return True

    # Look for tokens that match options indicating a path
    # using intersection of sets
    if set(config_tokens) & set(FILE_TYPE_OPTIONS):
        return True

    return False


def validate_path(path):
    """
    If the specified path does not exist, quit with an nonzero return
    code, and log an error
    """
    if not os.path.exists(path):
        raise PathException(
            'The path "%s" does not exist. Cannot proceed.' % path
        )


def parse_path(config_line):
    """
    Given a Joshua config line with no comments, return a path specified
    by the config.

    >>> parse_path('tm = moses -owner pt -maxspan 0 -path phrase-table.packed -max-source-len 5')
    'phrase-table.packed'
    >>> parse_path('tm = moses pt 0 phrase-table.packed')
    'phrase-table.packed'
    """
    config_tokens = config_line.split()
    # Look for -lm_file or -path option tokens indicating a path
    # If one of those options is not found, assume the final path is the
    # final token.
    path_index = -1
    for path_opt in FILE_TYPE_OPTIONS:
        if path_opt in config_tokens:
            path_index = config_tokens.index(path_opt) + 1
            break

    return config_tokens[path_index]


duplicate_name_counts = {}


def get_unique_dest(name):
    """
    If file/dir name was previously seen, rename the destination path
    by incrementing the number if type it has been seen.
    """
    global duplicate_name_counts
    times_seen = duplicate_name_counts.get(name, 0) + 1
    duplicate_name_counts[name] = times_seen
    pre_extension, extension = os.path.splitext(name)
    result = name
    if times_seen > 1:
        result = "{0}.{1}{2}".format(pre_extension, times_seen, extension)
    return result


def recursive_copy(src, dest, symlink = False):
    """
    Copy the src file or recursively copy the directory rooted at src to
    dest
    """
    if symlink:
        os.symlink(src, dest)
    else:
        if os.path.isdir(src):
            shutil.copytree(src, dest, True)
        else:
            shutil.copy(src, dest)


def process_line_containing_path(line, dest_dir, symlink, absolute):
    """
    The line has already been determined to contain a path, so generate
    an operation tuple, and update the config line based on the passed
    orig_dir and dest_dir

    >>> with open('/tmp/lm.kenlm', 'w') as fh:
    ...     fh.write('')
    >>> line = 'feature-function = StateMinimizingLanguageModel -lm_type kenlm -lm_order 5 -lm_file ./lm.kenlm'

    >>> process_line_containing_path(line, '/tmp', '/foobar')
    ... # doctest: +ELLIPSIS +NORMALIZE_WHITESPACE
    ('feature-function = StateMinimizingLanguageModel -lm_type kenlm -lm_order 5 -lm_file lm.kenlm',
     (<function recursive_copy at ...>,
      ('/tmp/lm.kenlm', '/foobar/lm.kenlm'),
      'Making a copy of /tmp/lm.kenlm at /foobar/lm.kenlm'))

    >>> line = 'feature-function = StateMinimizingLanguageModel -lm_type kenlm -lm_order 5 -lm_file /tmp/lm.kenlm'
    >>> process_line_containing_path(line, '/tmp', '/foobar')
    ... # doctest: +ELLIPSIS +NORMALIZE_WHITESPACE
    ('feature-function = StateMinimizingLanguageModel -lm_type kenlm -lm_order 5 -lm_file lm.2.kenlm',
     (<function recursive_copy at ...>,
      ('/tmp/lm.kenlm', '/foobar/lm.2.kenlm'),
      'Making a copy of /tmp/lm.kenlm at /foobar/lm.2.kenlm'))
    """
    #####################
    # Get the source path

    logging.debug('Looking for a path in the line:\n    %s' % line)
    line_parts = extract_line_parts(line)

    src_path = parse_path(line_parts.config)
    logging.debug('* Found path "%s"' % src_path)

    #####################################
    # Determine a unique destination path

    # Get directory name or file name of source path
    src_name = os.path.basename(src_path)
    dest_name = get_unique_dest(src_name)

    #############################################################
    # Generate an operation tuple to copy from orig_dir to dest_dir

    # Coerce the source path to its absolute path if it's relative
    full_src_path = os.path.normpath(src_path)
    validate_path(full_src_path)

    dest_path = os.path.join(dest_dir, 'model', dest_name)
    operation = (
        recursive_copy, (full_src_path, dest_path, symlink),
        'Making a copy of {0} at {1}'.format(full_src_path, dest_path)
    )

    ########################
    # Update the config line
    updated_config = line_parts.config.replace(src_path, dest_path if absolute else os.path.join('model', dest_name))
    if line_parts.comment:
        line = '#'.join([updated_config, line_parts.comment])
    else:
        line = updated_config

    return line, operation


class _PackGrammarPath(str):
    """
    Used when parsing command-line arguments to distinguish a grammar
    to be packed from a grammar to be copied.
    """
    pass


def handle_args(clargs):
    """
    Process the command-line options
    """
    class MyParser(argparse.ArgumentParser):
        def error(self, message):
            logging.error('ERROR: %s\n' % message)
            self.print_help()
            print(EXAMPLE)
            sys.exit(2)

    # Parse the command-line arguments.
    parser = MyParser(description='create a Joshua configuration bundle from '
                                  'an existing configuration and set of files')
    parser.add_argument(
        'config', type=argparse.FileType('r'),
        help='path to the origin configuration file. e.g. '
             '/path/to/tune/dir/joshua.config.final'
    )
    parser.add_argument(
        'dest_dir',
        help='destination directory, which should not already exist. But if '
             'it does, it will be removed if -f is used.'
    )
    parser.add_argument(
        '-f', '--force', action='store_true',
        help='extant destination directory will be overwritten'
    )
    parser.add_argument(
        '-o', '--copy-config-options', default='-top-n 0 -output-format %S -mark-oovs false',
        help='optional additional or replacement configuration options for '
             'Joshua, all surrounded by one pair of quotes. Defaults to '
             ' \'-top-n 0 -output-format %%S -mark-oovs false\''
    )
    parser.add_argument(
        '-m', '--mem', default='4g',
        help='default amount of memory for Joshua. Defaults to 4g'
    )
    parser.add_argument(
        '-v', '--verbose', action='store_true',
        help='print informational messages'
    )
    parser.add_argument(
        '--no-comments', dest='suppress_comments', action='store_true',
        help="delete comments and multiple consecutive empty lines")
    parser.add_argument(
        '--symlink', dest='symlink', action='store_true',
        help="symlink (where possible) to TM and LM files, instead of copying them")
    parser.add_argument(
        '--absolute', dest='absolute', action='store_true', default=False,
        help="Use absolute instead of relative paths for model file locations")
    parser.add_argument(
        '--source', dest='source',
        help="Source language two-character code (ISO 639-1)")
    parser.add_argument(
        '--normalizer', default=default_normalizer,
        help="source sentence normalizer that was applied to the model")
    parser.add_argument(
        '--tokenizer', default=default_tokenizer,
        help="source sentence tokenizer that was applied to the model")
    parser.add_argument(
        '-T', dest='tmpdir', default='/tmp',
        help="temp directory")

    return parser.parse_args(clargs)


def write_string_to_file(path, text):
    """
    Write the file at the specified path with the given lines
    """
    with open(path, 'w') as fh:
        fh.write(text)


def collect_operations(opts):
    """
    Produce a list of operations to take.

    Each element in the operations list is in the format:
      (function, (arguments,), 'logging message')
    """
    operations = []

    #######################
    # Destination directory
    if os.path.exists(opts.dest_dir):
        if not opts.force:
            raise Exception(
                'ERROR: The destination directory exists: "%s"\n'
                'Use -f or --force option to overwrite the directory.'
                % opts.dest_dir
            )
        else:
            operations.append(
                (shutil.rmtree, (opts.dest_dir,),
                 'Forcing deletion of existing destination directory "%s"'
                 % opts.dest_dir)
            )

    operations.append(
        (os.makedirs, (os.path.join(opts.dest_dir, 'model'),),
         'Creating destination directory "%s"' % opts.dest_dir)
    )

    ##########################
    # Input joshua.config file
    config_text = opts.config.read()
    if opts.copy_config_options:
        config_text = filter_through_copy_config_script(
            config_text,
            opts.copy_config_options
        )

    config_lines = config_text.split('\n')

    ###############
    # Files to copy
    # Parse the joshua.config and collect copy operations
    result_config_lines = []
    for i, line in enumerate(config_lines):
        line_num = i + 1

        if line_specifies_path(line):
            try:
                line, operation = process_line_containing_path(
                    line, opts.dest_dir, opts.symlink, opts.absolute
                )
            except PathException as e:
                # Prepend the line number to the error message
                message = (
                    'ERROR: Configuration file "{0}" line {1}: {2}'
                    .format(opts.config.name, line_num, e.message)
                )
                e.message = message
                raise e
            operations.append(operation)
        result_config_lines.append(line)

    ###########################
    # Output joshua.config file
    # Create the Joshua configuration file for the package
    path = os.path.join(opts.dest_dir, OUTPUT_CONFIG_FILE_NAME)
    text = '\n'.join(result_config_lines) + '\n'
    operations.append(
        (write_string_to_file, (path, text),
         'Writing the updated joshua.config to %s' % path
         )
    )

    #######################
    # Bundle runner scripts
    # Write the scripts that run Joshua using the configuration and
    # resource in the bundle, and make their mode world-readable, and
    # world-executable.
    for file_name, file_text in [['joshua', bundle_runner_text(opts.mem)],]:
        path = os.path.join(opts.dest_dir, file_name)
        operations.append(
            (write_string_to_file, (path, file_text),
             'Writing the bundle runner file "%s"' % path)
        )
        mode = (stat.S_IREAD | stat.S_IRGRP | stat.S_IROTH |
                stat.S_IEXEC | stat.S_IXGRP | stat.S_IXOTH)
        operations.append(
            (os.chmod, (path, mode),
             'Making the bundle runner file executable')
        )

    return operations


def execute_operations(operations):
    """
    Execute the list of operations.
    """
    for func, args, msg in operations:
        logging.info(msg)
        func(*args)


def main(argv):
    global opts
    opts = handle_args(argv[1:])

    logging.basicConfig(
        level=logging.DEBUG if opts.verbose else logging.WARNING,
        format='* %(message)s'
    )

    try:
#        validate_path(opts.orig_dir)
        operations = collect_operations(opts)
        execute_operations(operations)
    except Exception as e:
        error_quit(e.message)


if __name__ == "__main__":
    try:
        assert JOSHUA_PATH
    except AssertionError:
        error_quit('ERROR: The JOSHUA environment variable must be defined.')

    main(sys.argv)
