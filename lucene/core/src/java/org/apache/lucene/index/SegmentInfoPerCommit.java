package org.apache.lucene.index;

/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.IOException;
import java.util.Collection;
import java.util.HashSet;

import org.apache.lucene.store.Directory;

/** Embeds a [read-only] SegmentInfo and adds per-commit
 *  fields.
 *
 *  @lucene.experimental */

public class SegmentInfoPerCommit {

  /** The {@link SegmentInfo} that we wrap. */
  public final SegmentInfo info;

  // How many deleted docs in the segment:
  private int delCount;

  // Generation number of the live docs file (-1 if there
  // are no deletes yet):
  private long delGen;

  private volatile long sizeInBytes = -1;

  /** Sole constructor.
   * @param info {@link SegmentInfo} that we wrap
   * @param delCount number of deleted documents in this segment
   * @param delGen deletion generation number (used to name
             deletion files)
   **/
  public SegmentInfoPerCommit(SegmentInfo info, int delCount, long delGen) {
    this.info = info;
    this.delCount = delCount;
    this.delGen = delGen;
  }

  void advanceDelGen() {
    if (delGen == -1) {
      delGen = 1;
    } else {
      delGen++;
    }
    sizeInBytes = -1;
  }

  /** Returns total size in bytes of all files for this
   *  segment. */
  public long sizeInBytes() throws IOException {
    if (sizeInBytes == -1) {
      long sum = 0;
      for (final String fileName : files()) {
        sum += info.dir.fileLength(fileName);
      }
      sizeInBytes = sum;
    }

    return sizeInBytes;
  }

  /** Returns all files in use by this segment. */
  public Collection<String> files() throws IOException {
    Collection<String> files = new HashSet<String>(info.files());

    // Must separately add any live docs files:
    info.getCodec().liveDocsFormat().files(this, files);

    return files;
  }

  // NOTE: only used in-RAM by IW to track buffered deletes;
  // this is never written to/read from the Directory
  private long bufferedDeletesGen;
  
  long getBufferedDeletesGen() {
    return bufferedDeletesGen;
  }

  void setBufferedDeletesGen(long v) {
    bufferedDeletesGen = v;
    sizeInBytes =  -1;
  }
  
  void clearDelGen() {
    delGen = -1;
    sizeInBytes =  -1;
  }

  /**
   * Sets the generation number of the live docs file.
   * @see #getDelGen()
   */
  public void setDelGen(long delGen) {
    this.delGen = delGen;
    sizeInBytes =  -1;
  }

  /** Returns true if there are any deletions for the 
   * segment at this commit. */
  public boolean hasDeletions() {
    return delGen != -1;
  }

  /**
   * Returns the next available generation number
   * of the live docs file.
   */
  public long getNextDelGen() {
    if (delGen == -1) {
      return 1;
    } else {
      return delGen + 1;
    }
  }

  /**
   * Returns generation number of the live docs file 
   * or -1 if there are no deletes yet.
   */
  public long getDelGen() {
    return delGen;
  }
  
  /**
   * Returns the number of deleted docs in the segment.
   */
  public int getDelCount() {
    return delCount;
  }

  void setDelCount(int delCount) {
    this.delCount = delCount;
    assert delCount <= info.getDocCount();
  }

  /** Returns a description of this segment. */
  public String toString(Directory dir, int pendingDelCount) {
    return info.toString(dir, delCount + pendingDelCount);
  }

  @Override
  public String toString() {
    String s = info.toString(info.dir, delCount);
    if (delGen != -1) {
      s += ":delGen=" + delGen;
    }
    return s;
  }

  @Override
  public SegmentInfoPerCommit clone() {
    return new SegmentInfoPerCommit(info, delCount, delGen);
  }
}
