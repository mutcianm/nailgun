/*

Copyright 2004-2015, Martian Software, Inc.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

*/
package com.martiansoftware.nailgun;

import com.sun.jna.LastErrorException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.ByteBuffer;

/**
 * Implements a {@link Socket} backed by a native Unix domain socket.
 *
 * <p>Instances of this class always return {@code null} for {@link Socket#getInetAddress()}, {@link
 * Socket#getLocalAddress()}, {@link Socket#getLocalSocketAddress()}, {@link
 * Socket#getRemoteSocketAddress()}.
 */
public class NGUnixDomainSocket extends Socket {
  private final ReferenceCountedFileDescriptor fd;
  private final InputStream is;
  private final OutputStream os;

  /** Creates a Unix domain socket backed by a native file descriptor. */
  public NGUnixDomainSocket(int fd) {
    this.fd = new ReferenceCountedFileDescriptor(fd);
    this.is = new NGUnixDomainSocketInputStream();
    this.os = new NGUnixDomainSocketOutputStream();
  }

  public InputStream getInputStream() {
    return is;
  }

  public OutputStream getOutputStream() {
    return os;
  }

  public void shutdownInput() throws IOException {
    doShutdown(NGUnixDomainSocketLibrary.SHUT_RD);
  }

  public void shutdownOutput() throws IOException {
    doShutdown(NGUnixDomainSocketLibrary.SHUT_WR);
  }

  private void doShutdown(int how) throws IOException {
    try {
      int socketFd = fd.acquire();
      if (socketFd != -1) {
        NGUnixDomainSocketLibrary.shutdown(socketFd, how);
      }
    } catch (LastErrorException e) {
      throw new IOException(e);
    } finally {
      fd.release();
    }
  }

  public void close() throws IOException {
    super.close();
    try {
      // This might not close the FD right away. In case we are about
      // to read or write on another thread, it will delay the close
      // until the read or write completes, to prevent the FD from
      // being re-used for a different purpose and the other thread
      // reading from a different FD.
      fd.close();
    } catch (LastErrorException e) {
      throw new IOException(e);
    }
  }

  private class NGUnixDomainSocketInputStream extends InputStream {
    public int read() throws IOException {
      ByteBuffer buf = ByteBuffer.allocate(1);
      int result;
      if (doRead(buf) == 0) {
        result = -1;
      } else {
        // Make sure to & with 0xFF to avoid sign extension
        result = 0xFF & buf.get();
      }
      return result;
    }

    public int read(byte[] b, int off, int len) throws IOException {
      if (len == 0) {
        return 0;
      }
      ByteBuffer buf = ByteBuffer.wrap(b, off, len);
      int result = doRead(buf);
      if (result == 0) {
        result = -1;
      }
      return result;
    }

    private int doRead(ByteBuffer buf) throws IOException {
      try {
        int fdToRead = fd.acquire();
        if (fdToRead == -1) {
          return -1;
        }
        return NGUnixDomainSocketLibrary.read(fdToRead, buf, buf.remaining());
      } catch (LastErrorException e) {
        throw new IOException(e);
      } finally {
        fd.release();
      }
    }
  }

  private class NGUnixDomainSocketOutputStream extends OutputStream {

    public void write(int b) throws IOException {
      ByteBuffer buf = ByteBuffer.allocate(1);
      buf.put(0, (byte) (0xFF & b));
      doWrite(buf);
    }

    public void write(byte[] b, int off, int len) throws IOException {
      if (len == 0) {
        return;
      }
      ByteBuffer buf = ByteBuffer.wrap(b, off, len);
      doWrite(buf);
    }

    private void doWrite(ByteBuffer buf) throws IOException {
      try {
        int fdToWrite = fd.acquire();
        if (fdToWrite == -1) {
          return;
        }
        int ret = NGUnixDomainSocketLibrary.write(fdToWrite, buf, buf.remaining());
        if (ret != buf.remaining()) {
          // This shouldn't happen with standard blocking Unix domain sockets.
          throw new IOException(
              "Could not write "
                  + buf.remaining()
                  + " bytes as requested "
                  + "(wrote "
                  + ret
                  + " bytes instead)");
        }
      } catch (LastErrorException e) {
        throw new IOException(e);
      } finally {
        fd.release();
      }
    }
  }
}
