/*
 * Copyright 2017 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.iceberg.avro;

import com.netflix.iceberg.common.DynClasses;
import com.netflix.iceberg.common.DynConstructors;
import com.netflix.iceberg.io.DelegatingInputStream;
import com.netflix.iceberg.io.SeekableInputStream;
import org.apache.avro.file.SeekableInput;
import java.io.IOException;
import java.io.InputStream;

class AvroIO {
  private AvroIO() {
  }

  private static final Class<?> fsDataInputStreamClass = DynClasses.builder()
      .impl("org.apache.hadoop.fs.FSDataInputStream")
      .orNull()
      .build();

  private static final boolean relocated =
      "org.apache.avro.file.SeekableInput".equals(SeekableInput.class.getName());

  private static final DynConstructors.Ctor<SeekableInput> avroFsInputCtor =
      !relocated && fsDataInputStreamClass != null ?
          DynConstructors.builder(SeekableInput.class)
              .impl("org.apache.hadoop.fs.AvroFSInput", fsDataInputStreamClass, Long.TYPE)
              .build() :
          null;

  static SeekableInput stream(SeekableInputStream stream, long length) {
    if (stream instanceof DelegatingInputStream) {
      InputStream wrapped = ((DelegatingInputStream) stream).getDelegate();
      if (avroFsInputCtor != null && fsDataInputStreamClass != null &&
          fsDataInputStreamClass.isInstance(wrapped)) {
        return avroFsInputCtor.newInstance(wrapped, length);
      }
    }
    return new AvroInputStreamAdapter(stream, length);
  }

  private static class AvroInputStreamAdapter extends SeekableInputStream implements SeekableInput {
    private final SeekableInputStream stream;
    private final long length;

    public AvroInputStreamAdapter(SeekableInputStream stream, long length) {
      this.stream = stream;
      this.length = length;
    }

    @Override
    public void close() throws IOException {
      stream.close();
    }

    @Override
    public long getPos() throws IOException {
      return stream.getPos();
    }

    @Override
    public void seek(long newPos) throws IOException {
      stream.seek(newPos);
    }

    @Override
    public long tell() throws IOException {
      return getPos();
    }

    @Override
    public long length() throws IOException {
      return length;
    }

    @Override
    public int read() throws IOException {
      return stream.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
      return stream.read(b);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
      return stream.read(b, off, len);
    }

    @Override
    public long skip(long n) throws IOException {
      return stream.skip(n);
    }

    @Override
    public int available() throws IOException {
      return stream.available();
    }

    @Override
    public synchronized void mark(int readlimit) {
      stream.mark(readlimit);
    }

    @Override
    public synchronized void reset() throws IOException {
      stream.reset();
    }

    @Override
    public boolean markSupported() {
      return stream.markSupported();
    }
  }
}
