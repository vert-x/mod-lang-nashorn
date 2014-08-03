package org.vertx.java.platform.impl;

/*
 * Copyright 2011-2012 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
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
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;

public class ModuleWrapper extends URLStreamHandler {

    private static final class ContentWrapper {

        private final byte[] content;
        private int offset = 0;

        private ContentWrapper(final byte[] content) {
            this.content = content;
        }

        private boolean hasMore() {
            return offset < content.length;
        }

        private int read() {
            return content[offset++];
        }

        public int read(final byte[] b, final int o, final int len) {
            int off = o;
            int currentOffset = offset;
            if (len >= offset) {
                while (offset < content.length) {
                    b[off] = content[offset];
                    off++;
                    offset++;
                }
            } else {
                for (int i = 0; i < len; i++) {
                    b[off] = content[offset];
                    off++;
                    offset++;
                    if (offset == content.length - 1) {
                        break;
                    }
                }
            }
            return offset - currentOffset;
        }
    }

    protected static final class WrapperInputStream extends InputStream {
        private static final byte[] WRAP_PREFIX = VertxRequire.MODULE_HEADER.getBytes();
        private static final byte[] WRAP_SUFFIX = VertxRequire.MODULE_FOOTER.getBytes();
        private static final int WRAP_LENGTH = WRAP_PREFIX.length + WRAP_SUFFIX.length;

        private final ContentWrapper prefix = new ContentWrapper(WRAP_PREFIX);
        private final ContentWrapper suffix = new ContentWrapper(WRAP_SUFFIX);
        private final InputStream wrapped;
        private boolean wrappedHasMore = true;

        public WrapperInputStream(final InputStream wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public int read() throws IOException {
            if (prefix.hasMore()) {
                return prefix.read();
            } else if (wrappedHasMore) {
                final int read = wrapped.read();
                if (read >= 0) {
                    return read;
                } else {
                    wrappedHasMore = false;
                    return suffix.read();
                }
            } else {
                if (suffix.hasMore()) {
                    return suffix.read();
                } else {
                    return -1;
                }
            }
        }

        @Override
        public int read(final byte[] b, final int off, final int len) throws IOException {
            if (prefix.hasMore()) {
                return prefix.read(b, off, len);
            } else if (wrappedHasMore) {
                final int read = wrapped.read(b, off, len);
                if (read >= 0) {
                    return read;
                } else {
                    wrappedHasMore = false;
                    return suffix.read(b, off, len);
                }
            } else {
                if (suffix.hasMore()) {
                    return suffix.read(b, off, len);
                } else {
                    return -1;
                }
            }
        }
    }

    private final class WrappedURLConnection extends URLConnection {
        private final URLConnection wrapped;

        WrappedURLConnection(final URL url, final URLConnection wrapped) {
            super(url);
            this.wrapped = wrapped;
        }

        @Override
        public void connect() throws IOException {
            //NOOP
        }

        @Override
        public InputStream getInputStream() throws IOException {
            return new WrapperInputStream(wrapped.getInputStream());
        }

        @Override
        public int getContentLength() {
            return wrapped.getContentLength() + WrapperInputStream.WRAP_LENGTH;
        }

        @Override
        public long getLastModified() {
            return wrapped.getLastModified();
        }
    }

    @Override
    protected URLConnection openConnection(URL u) throws IOException {
        final URL withoutHandler = new URL(u.toExternalForm());
        return new WrappedURLConnection(withoutHandler, withoutHandler.openConnection());
    }
}
