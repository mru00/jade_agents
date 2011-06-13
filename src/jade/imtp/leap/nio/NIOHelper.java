/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package jade.imtp.leap.nio;

//#J2ME_EXCLUDE_FILE

import java.nio.ByteBuffer;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * static helper for ssl/nio related handshaking/input/output
 * @author eduard
 */
public class NIOHelper {
	public static final ByteBuffer EMPTY_BUFFER = ByteBuffer.allocateDirect(0);

	private NIOHelper() {
	}

	private static final Logger log = Logger.getLogger(NIOHelper.class.getName());

	/**
	 * logs info on a bytebuffer at level FINE with name "unknown"
	 * @param b
	 */
	public static void logBuffer(ByteBuffer b) {
		logBuffer(b, "unknown");
	}

	/**
	 * logs info on a bytebuffer at level FINE with name &lt;name&gt;
	 * @param b
	 */
	public static void logBuffer(ByteBuffer b, String name) {
		logBuffer(b, name, Level.FINE);
	}

	/**
	 * logs info on a bytebuffer at level &lt;l&gt; with name &lt;name&gt;
	 * @param b
	 */
	public static void logBuffer(ByteBuffer b, String name, Level l) {
		if (log.isLoggable(l)) {
			log.log(l,"bufferinfo " + name + ": pos " + b.position() + ", rem " + b.remaining() + ", lim " + b.limit());
		}
	}

	/**
	 * copy data from src to dst, as much as fits in dst. src's position() will be moved
	 * when data are copied.
	 * @param src copy from
	 * @param dst copy to
	 * @return number of bytes copied
	 */
	public static int copyAsMuchAsFits( ByteBuffer dst, ByteBuffer src) {
		if (dst.hasRemaining() && src.hasRemaining()) {
			// current position in dst
			int pos = dst.position();

			// read from src as much as fits in dst
			int limit = src.limit();
			if (src.remaining() > dst.remaining()) {
				// data from src does not fit, set limit so that data will fit
				if (log.isLoggable(Level.FINE)) {
					log.fine("setting limit of src buffer to " + (src.position() + dst.remaining()));
				}
				src.limit(src.position() + dst.remaining());
			}

			dst.put(src);

			// reset limit, to make rest of data available to put in payload buffer
			src.limit(limit);

			if (log.isLoggable(Level.FINE)) {
				log.fine("bytes copied to dst " + (dst.position() - pos) + ", bytes left in src " + src.remaining());
				logBuffer(src, "src");
				logBuffer(dst, "dst");
			}
			// return number of data read into dst
			return dst.position() - pos;
		} else {
			return 0;
		}
	}

	/**
	 * returns an enlarged, empty buffer
	 * @param b
	 * @param extraSpace
	 * @return the new enlarged buffer
	 */
	public static ByteBuffer enlargeBuffer(ByteBuffer b, int extraSpace) {
		if (log.isLoggable(Level.FINE)) {
			logBuffer(b,"enlarging buffer with " + extraSpace);
		}
		ByteBuffer bigger = ByteBuffer.allocateDirect(b.capacity() + extraSpace);
		return bigger;
	}

	/**
	 * returns an enlarged, filled with bytes from the buffer argument
	 * @param b
	 * @param extraSpace
	 * @return the new enlarged buffer
	 */
	public static ByteBuffer enlargeAndFillBuffer(ByteBuffer b, int extraSpace) {
		ByteBuffer bigger = enlargeBuffer(b, extraSpace);
		bigger.put(b);
		return bigger;
	}
}
