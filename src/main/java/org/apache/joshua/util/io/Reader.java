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

/**
 * Common interface for Reader type objects.
 *
 * @author wren ng thornton wren@users.sourceforge.net
 * @version $LastChangedDate: 2009-03-26 15:06:57 -0400 (Thu, 26 Mar 2009) $
 */
public interface Reader<E> extends Iterable<E>, Iterator<E>, AutoCloseable {

  /**
   * Close the reader, freeing all resources.
   * @throws IOException if there is an error closing the reader instance
   */
  @Override
  void close() throws IOException;

  /**
   * Determine if the reader is ready to read a line.
   * @return true if it is ready
   * @throws IOException if there is an error whilst determining if the reader if ready
   */
  boolean ready() throws IOException;

  /**
   * Read a "line" and return an object representing it.
   * @return an object representing a single line
   * @throws IOException if there is an error reading lines
   */
  E readLine() throws IOException;
}
