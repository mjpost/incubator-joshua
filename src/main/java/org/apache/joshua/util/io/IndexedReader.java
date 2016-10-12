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
package org.apache.joshua.util.io;

import java.io.IOException;
import java.util.Iterator;
import java.util.NoSuchElementException;


/**
 * Wraps a reader with "line" index information.
 *
 * @author wren ng thornton wren@users.sourceforge.net
 * @version $LastChangedDate: 2009-03-26 15:06:57 -0400 (Thu, 26 Mar 2009) $
 */
public class IndexedReader<E> implements Reader<E> {
  /** A name for the type of elements the reader produces. */
  private final String elementName;

  /** The number of elements the reader has delivered so far. */
  private int lineNumber;

  /** The underlying reader. */
  private final Reader<E> reader;

  public IndexedReader(String elementName, Reader<E> reader) {
    this.elementName = elementName;
    this.lineNumber = 0;
    this.reader = reader;
  }

  /**
   * Return the number of elements delivered so far.
   * @return integer representing the number of elements delivered so far
   */
  public int index() {
    return this.lineNumber;
  }


  /**
   * Wrap an IOException's message with the index when it occured.
   * @param oldError the old {@link java.io.IOException} we wish to wrap
   * @return the new wrapped {@link java.io.IOException}
   */
  public IOException wrapIOException(IOException oldError) {
    IOException newError =
        new IOException("At " + this.elementName + " " + this.lineNumber + ": "
            + oldError.getMessage());
    newError.initCause(oldError);
    return newError;
  }

  // ===============================================================
  // Reader
  // ===============================================================

  /**
   * Delegated to the underlying reader.
   * @return true if the reader is ready
   * @throws IOException if there is an error determining readiness
   */
  @Override
  public boolean ready() throws IOException {
    try {
      return this.reader.ready();
    } catch (IOException oldError) {
      throw wrapIOException(oldError);
    }
  }


  /**
   * Delegated to the underlying reader. Note that we do not have a <code>finalize()</code> method;
   * however, when we fall out of scope, the underlying reader will too, so its finalizer may be
   * called. For correctness, be sure to manually close all readers.
   */
  @Override
  public void close() throws IOException {
    try {
      this.reader.close();
    } catch (IOException oldError) {
      throw wrapIOException(oldError);
    }
  }


  /** Delegated to the underlying reader. */
  @Override
  public E readLine() throws IOException {
    E line;
    try {
      line = this.reader.readLine();
    } catch (IOException oldError) {
      throw wrapIOException(oldError);
    }
    ++this.lineNumber;
    return line;
  }


  // ===============================================================
  // Iterable -- because sometimes Java can be very stupid
  // ===============================================================

  /** Return self as an iterator. */
  @Override
  public Iterator<E> iterator() {
    return this;
  }


  // ===============================================================
  // Iterator
  // ===============================================================

  /** Delegated to the underlying reader. */
  @Override
  public boolean hasNext() {
    return this.reader.hasNext();
  }


  /** Delegated to the underlying reader. */
  @Override
  public E next() throws NoSuchElementException {
    E line = this.reader.next();
    // Let exceptions out, we'll wrap any errors a closing time.

    ++this.lineNumber;
    return line;
  }


  /**
   * If the underlying reader supports removal, then so do we. Note that the {@link #index()} method
   * returns the number of elements delivered to the client, so removing an element from the
   * underlying collection does not affect that number.
   */
  @Override
  public void remove() throws UnsupportedOperationException {
    this.reader.remove();
  }
}
