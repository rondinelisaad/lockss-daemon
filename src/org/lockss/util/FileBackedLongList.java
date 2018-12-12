/*

Copyright (c) 2000-2018, Board of Trustees of Leland Stanford Jr. University
All rights reserved.

Redistribution and use in source and binary forms, with or without
modification, are permitted provided that the following conditions are met:

1. Redistributions of source code must retain the above copyright notice,
this list of conditions and the following disclaimer.

2. Redistributions in binary form must reproduce the above copyright notice,
this list of conditions and the following disclaimer in the documentation
and/or other materials provided with the distribution.

3. Neither the name of the copyright holder nor the names of its contributors
may be used to endorse or promote products derived from this software without
specific prior written permission.

THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
POSSIBILITY OF SUCH DAMAGE.

*/

package org.lockss.util;

import java.io.*;
import java.nio.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileChannel.MapMode;

import org.apache.commons.collections.primitives.RandomAccessLongList;
import org.apache.commons.io.IOUtils;

/**
 * <p>
 * A concrete implementation of {@link RandomAccessLongList} (which mimics
 * {@link List} but where the element type is the native {@code long}) that can
 * address up to {@link Integer#MAX_VALUE}/8 elements and is backed by a
 * memory-mapped file rather than main memory.
 * </p>
 * <p>
 * The underlying implementation uses a {@link LongBuffer} around a
 * {@link MemoryByteBuffer} around a {@link FileChannel} around a
 * {@link CountingRandomAccessFile} (this is similar to using an
 * {@link ObjectInputStream} around a {@link BufferedInputStream} around a
 * {@link FileInputStream}). {@link LongBuffer} is a convenient
 * {@code long}-based adapter around {@link Buffer}; {@link MemoryByteBuffer} is
 * where the efficient paging of data to and from disk occurs; and
 * {@link CountingRandomAccessFile} provides a slight performance improvement
 * over a standard {@RandomAccessFile}.
 * </p>
 * 
 * @since 1.75
 */
public class FileBackedLongList extends RandomAccessLongList {

  /**
   * <p>
   * The number of elements in this list.
   * </p>
   * 
   * @since 1.75
   */
  protected int size;

  /**
   * <p>
   * The {@link File} backing this list.
   * </p>
   * 
   * @since 1.75
   */
  protected File file;

  /**
   * <p>
   * Whether the file backing this list must be deleted when the list is
   * garbage-collected or {@link #release()} is called; should be false when
   * the list was instantiated with a user-provided file and true when
   * instantiated with a constructed-provided temporary file.
   * </p>
   * 
   * @since 1.75
   * @see #file
   */
  protected boolean deleteFile;
  
  /**
   * <p>
   * The {@link CountingRandomAccessFile} backing this list.
   * </p>
   * 
   * @since 1.75
   * @see CountingRandomAccessFile
   * @see #file
   */
  protected CountingRandomAccessFile craf;
  
  /**
   * <p>
   * The {@link FileChannel} backing this list.
   * </p>
   * 
   * @since 1.75
   * @see #craf
   */
  protected FileChannel chan;
  
  /**
   * <p>
   * The {@link MappedByteBuffer} backing this list.
   * </p>
   * 
   * @since 1.75
   * @see #chan
   */
  protected MappedByteBuffer mbbuf;

  /**
   * <p>
   * The {@link LongBuffer} backing this list.
   * </p>
   * 
   * @since 1.75
   * @see #mbbuf
   */
  protected LongBuffer lbuf;

  /**
   * <p>
   * Makes a new list of {@code long}s, backed a freshly created temporary file,
   * with the default initial element capacity.
   * </p>
   * 
   * @throws FileNotFoundException
   *           If some error occurs while opening or creating
   *           the temporary file.
   * @throws IOException
   *           If the file once opened cannot be truncated to zero bytes.
   * @since 1.75
   * @see #FileBackedLongList(File, int)
   * @see #createTempFile()
   * @see #DEFAULT_INITIAL_CAPACITY
   */
  public FileBackedLongList()
      throws FileNotFoundException, IOException {
    this(createTempFile(),
         DEFAULT_INITIAL_CAPACITY);
    this.deleteFile = true;
  }

  /**
   * <p>
   * Makes a new list of {@code long}s, backed by the given file, with the default
   * initial element capacity.
   * </p>
   * 
   * @param file
   *          The file backing the list. <b>If the file already exists, the file
   *          will be overwritten with an empty file.</b>
   * @throws FileNotFoundException
   *           If the given file object does not denote an existing, writable
   *           regular file and a new regular file of that name cannot be
   *           created, or if some other error occurs while opening or creating
   *           the file.
   * @throws IOException
   *           If the given file once opened cannot be truncated to zero bytes.
   * @since 1.75
   * @see #FileBackedLongList(File, int)
   * @see #DEFAULT_INITIAL_CAPACITY
   */
  public FileBackedLongList(File file)
      throws FileNotFoundException, IOException {
    this(file,
         DEFAULT_INITIAL_CAPACITY);
  }

  /**
   * <p>
   * Makes a new list of {@code long}s, backed by the given file, with the given
   * initial element capacity.
   * </p>
   * 
   * @param file
   *          The file backing the list. <b>If the file already exists, the file
   *          will be overwritten with an empty file.</b>
   * @param initialCapacity
   *          An initial element capacity.
   * @throws FileNotFoundException
   *           If the given file object does not denote an existing, writable
   *           regular file and a new regular file of that name cannot be
   *           created, or if some other error occurs while opening or creating
   *           the file.
   * @throws IOException
   *           If the given file once opened cannot be truncated to zero bytes.
   * @since 1.75
   * @see CountingRandomAccessFile#CountingRandomAccessFile(File, String, boolean)
   */
  public FileBackedLongList(File file,
                            int initialCapacity)
      throws FileNotFoundException, IOException {
    this.size = 0;
    this.file = file;
    this.deleteFile = false; // reset by some constructors
    this.craf = new CountingRandomAccessFile(file, CountingRandomAccessFile.MODE_READ_WRITE, false);
    this.chan = craf.getChannel();
    this.mbbuf = chan.map(MapMode.READ_WRITE, 0L, initialCapacity * BYTES);
    this.lbuf = mbbuf.asLongBuffer();
  }

  /**
   * <p>
   * Makes a new list of {@code long}s, backed a freshly created temporary file,
   * with the given initial element capacity.
   * </p>
   * 
   * @param initialCapacity
   *          An initial element capacity.
   * @throws FileNotFoundException
   *           If some error occurs while opening or creating
   *           the temporary file.
   * @throws IOException
   *           If the given file once opened cannot be truncated to zero bytes.
   * @since 1.75
   * @see #FileBackedLongList(File, int)
   * @see #createTempFile()
   */
  public FileBackedLongList(int initialCapacity)
      throws FileNotFoundException, IOException {
    this(createTempFile(),
         initialCapacity);
    this.deleteFile = true;
  }
  
  /**
   * <p>
   * Makes a new list of {@code long}s, backed by the file with the given name,
   * with the default initial element capacity.
   * </p>
   * 
   * @param name
   *          The name of the file backing the list. <b>If the file already
   *          exists, the file will be overwritten with an empty file.</b>
   * @throws FileNotFoundException
   *           If the given file object does not denote an existing, writable
   *           regular file and a new regular file of that name cannot be
   *           created, or if some other error occurs while opening or creating
   *           the file.
   * @throws IOException
   *           If the given file once opened cannot be truncated to zero bytes.
   * @since 1.75
   * @see #FileBackedLongList(File, int)
   * @see #DEFAULT_INITIAL_CAPACITY
   */
  public FileBackedLongList(String name)
      throws FileNotFoundException, IOException {
    this(new File(name),
         DEFAULT_INITIAL_CAPACITY);
  }
  
  /**
   * <p>
   * Makes a new list of {@code long}s, backed by the file with the given name,
   * with the given initial element capacity.
   * </p>
   * 
   * @param name
   *          The name of the file backing the list. <b>If the file already
   *          exists, the file will be overwritten with an empty file.</b>
   * @param initialCapacity
   *          An initial element capacity.
   * @throws FileNotFoundException
   *           If the given file object does not denote an existing, writable
   *           regular file and a new regular file of that name cannot be
   *           created, or if some other error occurs while opening or creating
   *           the file.
   * @throws IOException
   *           If the given file once opened cannot be truncated to zero bytes.
   * @since 1.75
   * @see #FileBackedLongList(File, int)
   */
  public FileBackedLongList(String name,
                            int initialCapacity)
      throws FileNotFoundException, IOException {
    this(new File(name),
         initialCapacity);
  }
  
  @Override
  public void add(int index, long element) throws IOError {
    if (index < 0 || index > size) {
      throw new IndexOutOfBoundsException(Integer.toString(index));
    }
    try {
      // If the underlying MeMoryByteBuffer capacity has been reached, double it
      if (lbuf.capacity() == size) {
        // Evict old buffer
        MappedByteBuffer oldBuf = mbbuf;
        int len = (2 * oldBuf.capacity() <= Integer.MAX_VALUE) ? 2 * oldBuf.capacity() : Integer.MAX_VALUE;
        oldBuf.force();
        CountingRandomAccessFile.unmap(oldBuf);
        oldBuf = null;
        // Allocate new buffer
        mbbuf = chan.map(MapMode.READ_WRITE, 0, len);
        lbuf = mbbuf.asLongBuffer();
      }
      // Starting from the end, move chunks of BUFFER elements up by one
      // element, except perhaps for a smaller last chunk, working toward the
      // given index
      int pos = size;
      if (pos > index) {
        long[] b = new long[BUFFER];
        while (pos > index) {
          int len = (pos - index > BUFFER) ? BUFFER : pos - index;
          pos -= len;
          lbuf.position(pos);
          lbuf.get(b, 0, len);
          lbuf.position(pos + 1);
          lbuf.put(b, 0, len);
        }
      }
      // Add the element at the given index
      lbuf.put(index, element);
      ++size;
    }
    catch (IOException exc) {
      throw new IOError(exc);
    }
  }
  
  @Override
  protected void finalize() throws Throwable {
    release();
  }

  @Override
  public long get(int index) {
    if (index < 0 || index >= size) {
      throw new IndexOutOfBoundsException(Integer.toString(index));
    }
    // Get the element
    return lbuf.get(index);
  }

  /**
   * <p>
   * Releases all resources associated with this list; accessing the list after
   * releasing it results in unspecified error conditions.
   * </p>
   * 
   * @since 1.75
   */
  public void release() {
    IOUtils.closeQuietly(craf);
    craf = null;
    IOUtils.closeQuietly(chan);
    chan = null;
    mbbuf.force();
    CountingRandomAccessFile.unmap(mbbuf);
    mbbuf = null;
    lbuf = null;
    if (deleteFile) {
      file.delete();
    }
    size = -1;
  }
  
  @Override
  public long removeElementAt(int index) {
    // Remember the old value
    long ret = get(index); // does the bounds check
    // Starting from the given index, move chunks of BUFFER elements down by one
    // element, except perhaps for a smaller last chunk, working toward the end
    int pos = index;
    if (pos < size - 1) {
      long[] b = new long[BUFFER];
      while (pos < size - 1) {
        int len = (size - 1 - pos > BUFFER) ? BUFFER : size - 1 - pos;
        lbuf.position(pos + 1);
        lbuf.get(b, 0, len);
        lbuf.position(pos);
        lbuf.put(b, 0, len);
        pos += len;
      }
    }
    // Decrease the element count and return the old value
    --size;
    return ret;
  }

  @Override
  public long set(int index, long element) {
    long ret = get(index); // does bounds check
    lbuf.put(index, element);
    return ret;
  }
  
  @Override
  public int size() {
    return size;
  }  
    
  /**
   * <p>
   * The number of bytes of the element type ({@value} for {@code long}).
   * </p>
   * 
   * @since 1.75
   */
  protected static final int BYTES = 8;
  
  /**
   * <p>
   * The number of elements buffered when sliding portions of the list up or
   * down during addition or deletion.
   * </p>
   * 
   * @since 1.75
   */
  protected static final int BUFFER = 1024;

  /**
   * <p>
   * Default initial element capacity ({@value}).
   * </p>
   * 
   * @since 1.75
   * @see #FileBackedLongList()
   * @see #FileBackedLongList(File)
   * @see #FileBackedLongList(String)
   */
  protected static final int DEFAULT_INITIAL_CAPACITY = 1024 * BUFFER;

  /**
   * <p>
   * Creates a temporary file.
   * </p>
   * 
   * @return A freshly created temporary file.
   * @throws IOException
   *           If a file could not be created.
   * @since 1.75
   * @see File#createTempFile(String, String)
   */
  protected static File createTempFile() throws IOException {
    File ret = File.createTempFile(FileBackedLongList.class.getSimpleName(), ".bin");
    ret.deleteOnExit();
    return ret;
  }
  
}
