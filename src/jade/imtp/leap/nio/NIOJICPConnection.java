package jade.imtp.leap.nio;

//#J2ME_EXCLUDE_FILE
import jade.imtp.leap.ICPException;
import jade.imtp.leap.JICP.*;

import java.io.IOException;
import java.io.EOFException;
import java.io.ByteArrayOutputStream;
import java.nio.*;
import java.nio.channels.*;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
@author Giovanni Caire - TILAB
 */
public class NIOJICPConnection extends Connection {
	// type+info+session+recipient-length+recipient(255)+payload-length(4)
	public static final int MAX_HEADER_SIZE = 263;
	// TODO 5k, why? configurable?
	public static final int INITIAL_BUFFER_SIZE = 5120;
	// TODO 5k, why? configurable?
	public static final int INCREASE_STEP = 5120;
	private SocketChannel myChannel;
	private ByteBuffer socketData = ByteBuffer.allocateDirect(INITIAL_BUFFER_SIZE);
	private ByteBuffer payloadBuf = ByteBuffer.allocateDirect(INITIAL_BUFFER_SIZE);

	private byte type;
	private byte info;
	private byte sessionID;
	private String recipientID;

	private boolean headerReceived = false;
	private boolean closed = false; 

	private List<BufferTransformerInfo> transformers = new LinkedList<BufferTransformerInfo>();

	private static final Logger log = Logger.getLogger(NIOJICPConnection.class.getName());

	public NIOJICPConnection() {
	}

	/**
    Read a JICPPacket from the connection.
    The method is synchronized since we reuse the same Buffer object
    for reading the packet header.
    It should be noted that the packet data may not be completely
    available when the embedded channel is ready for a READ operation.
    In that case a PacketIncompleteException is thrown to indicate
    that successive calls to this method must occur in order to
    fully read the packet.
	 */
	public final synchronized JICPPacket readPacket() throws IOException {
		read();
		ByteBuffer jicpData = transformAfterRead(socketData);
		if (jicpData.hasRemaining()) {
			// JICP data actually available after transformations
			if (!headerReceived) {
				// Note that, since we require that a JICP-Header is never split, we 
				// are sure that at least all header bytes are available
				//System.out.println("Read "+jicpData.remaining()+" bytes");
				headerReceived = true;
				type = jicpData.get();
				//System.out.println("type = "+type);
				info = jicpData.get();
				//System.out.println("info = "+info);
				sessionID = -1;
				if ((info & JICPProtocol.SESSION_ID_PRESENT_INFO) != 0) {
					sessionID = jicpData.get();
					//System.out.println("SessionID = "+sessionID);
				}
				if ((info & JICPProtocol.RECIPIENT_ID_PRESENT_INFO) != 0) {
					byte recipientIDLength = jicpData.get();
					byte[] bb = new byte[recipientIDLength];
					jicpData.get(bb);
					recipientID = new String(bb);
					//System.out.println("RecipientID = "+recipientID);
				}
				if ((info & JICPProtocol.DATA_PRESENT_INFO) != 0) {
					int b1 = (int) jicpData.get();
					int b2 = (int) jicpData.get();
					int payloadLength = ((b2 << 8) & 0x0000ff00) | (b1 & 0x000000ff);
					int b3 = (int) jicpData.get();
					int b4 = (int) jicpData.get();
					payloadLength |= ((b4 << 24) & 0xff000000) | ((b3 << 16) & 0x00ff0000);

					if (payloadLength > JICPPacket.MAX_SIZE) {
						throw new IOException("Packet size greater than maximum allowed size. " + payloadLength);
					}

					resizePayloadBuffer(payloadLength);

					// jicpData may already contain some payload bytes --> copy them into the payload buffer
					NIOHelper.copyAsMuchAsFits(payloadBuf, jicpData);

					if (payloadBuf.hasRemaining()) {
						// Payload not completely received. Wait for next round 
						throw new PacketIncompleteException();
					} else {
						return buildPacket();
					}
				} else {
					return buildPacket();
				}
			}
			else {
				// We are in the middle of reading the payload of a packet (the previous call to readPacket() resulted in a PacketIncompleteException)
				NIOHelper.copyAsMuchAsFits(payloadBuf, jicpData);
				if (payloadBuf.hasRemaining()) {
					// Payload not completely received. Wait for next round 
					throw new PacketIncompleteException();
				} else {
					return buildPacket();
				}
			}
		}
		else {
			// No JICP data available at this round. Wait for next 
			throw new PacketIncompleteException();
		}
	}

	private void read() throws IOException {
		socketData.clear();
		readFromChannel(socketData);
		while (!socketData.hasRemaining()) {
			// We read exactly how many bytes how socketData can contain. VERY likely there are 
			// more bytes to read from the channel --> Enlarge socketData and read again
			socketData.flip();
			socketData = NIOHelper.enlargeAndFillBuffer(socketData, INCREASE_STEP);
			try {
				readFromChannel(socketData);
			}
			catch (EOFException eofe) {
				// Return the bytes read so far
				break;
			}
		}
		socketData.flip();
		if (log.isLoggable(Level.FINE)) 
			log.fine("------- READ "+socketData.remaining()+" bytes from the network");
	}


	/**
	 * reads data from the socket into a buffer
	 * @param b
	 * @return number of bytes read
	 * @throws IOException
	 */
	private final int readFromChannel(ByteBuffer b) throws IOException {
		int n = myChannel.read(b);
		if (n == -1) {
			throw new EOFException("Channel closed");
		}
		return n;
	}

	private ByteBuffer transformAfterRead(ByteBuffer incomingData) throws IOException {
		// Let BufferTransformers process incoming data
		ByteBuffer transformationInput = incomingData;
		ByteBuffer transformationOutput = transformationInput;
		for (ListIterator<BufferTransformerInfo> it = transformers.listIterator(transformers.size()); it.hasPrevious();) {
			BufferTransformerInfo info = it.previous();
			BufferTransformer btf = info.getTransformer();

			// In case there were unprocessed data at previous round, append them before the data to be processed at this round 
			transformationInput = info.attachUnprocessedData(transformationInput);

			if (log.isLoggable(Level.FINER)) 
				log.finer("--------- Passing "+transformationInput.remaining()+" bytes to Transformer "+btf.getClass().getName());
			transformationOutput = btf.postprocessBufferRead(transformationInput);
			if (log.isLoggable(Level.FINER))
				log.finer("--------- Transformer "+btf.getClass().getName()+" did not transform " +transformationInput.remaining()+" bytes");

			// In case the transformer did not process all input data, store unprocessed data for next round
			info.storeUnprocessedData(transformationInput);

			// Output of transformer N becomes input of transformer N-1 (transformers are scanned in reverse order when managing incoming data)
			transformationInput = transformationOutput;

			if (!transformationInput.hasRemaining() && it.hasPrevious()) {
				// No bytes for next transformation --> no need to continue scanning transformers
				break;
			}
		}
		return transformationOutput;
	}


	private void resizePayloadBuffer(int payloadLength) {
		if (payloadLength > payloadBuf.capacity()) {
			payloadBuf = NIOHelper.enlargeBuffer(payloadBuf, payloadLength - payloadBuf.capacity());
		} else {
			payloadBuf.limit(payloadLength);
		}
	}

	private JICPPacket buildPacket() {
		payloadBuf.flip();
		byte[] payload = new byte[payloadBuf.remaining()];
		payloadBuf.get(payload, 0, payload.length);
		JICPPacket pkt = new JICPPacket(type, info, recipientID, payload);
		pkt.setSessionID(sessionID);

		// Reset internal fields to properly manage next JICP packet
		headerReceived = false;
		recipientID = null;
		payloadBuf.clear();
		return pkt;
	}


	/**
	 * Write a JICPPacket on the connection, first calls {@link #preprocessBufferToWrite(java.nio.ByteBuffer) }.
	 * When the buffer returned by {@link #preprocessBufferToWrite(java.nio.ByteBuffer) }, no write will be performed.
	 * @return number of application bytes written to the socket
	 */
	public final synchronized int writePacket(JICPPacket pkt) throws IOException {
		ByteArrayOutputStream os = new ByteArrayOutputStream();
		int n = pkt.writeTo(os);
		if (log.isLoggable(Level.FINE)) {
			log.fine("writePacket: number of bytes before preprocessing: " + n);
		}
		ByteBuffer toSend = ByteBuffer.wrap(os.toByteArray());
		ByteBuffer bb = transformBeforeWrite(toSend);
		if (toSend.hasRemaining() && transformers.size() > 0) {
			// for direct JICPConnections the data from the packet are used directly
			// for subclasses the subsequent transformers must transform all data from the packet before sending
			throw new IOException("still need to transform: " + toSend.remaining());
		}
		int m = 0;
		if (bb.hasRemaining()) {
			int toWrite = bb.remaining();
			m = writeToChannel(bb);
			if (log.isLoggable(Level.FINE)) {
				log.fine("writePacket: bytes written " + m + ", needed to write: " + toWrite);
			}
			if (toWrite!=m) {
				throw new IOException("writePacket: bytes written " + m + ", needed to write: " + toWrite);
			}
		}
		return m;
	}

	private ByteBuffer transformBeforeWrite(ByteBuffer data) throws IOException {
		for (BufferTransformerInfo info : transformers) {
			BufferTransformer btf = info.getTransformer();
			data = btf.preprocessBufferToWrite(data);
		}
		return data;
	}

	/**
	 * writes data to the channel
	 * @param bb
	 * @return the number of bytes written to the channel
	 * @throws IOException
	 */
	public final int writeToChannel(ByteBuffer bb) throws IOException {
		return myChannel.write(bb);
	}


	/**
    Close the connection
	 */
	public void close() throws IOException {
		closed = true;
		myChannel.close();
	}

	// In some cases we may receive some data (often a socket closed by peer indication) while 
	// closing the channel locally. Trying to read such data results in an Exception. To avoid printing 
	// this Exception it is possible to check this method
	public boolean isClosed() {
		return closed;
	}

	public String getRemoteHost() {
		return myChannel.socket().getInetAddress().getHostAddress();
	}

	/**
	 * sets the channel for this connection
	 * @param channel
	 * @throws ICPException
	 */
	void init(SocketChannel channel) throws ICPException {
		this.myChannel = (SocketChannel) channel;
	}


	public void addBufferTransformer(BufferTransformer transformer) {
		transformers.add(new BufferTransformerInfo(transformer));
	}


	/**
	 * Inner class BufferTransformerInfo
	 * This class keeps together a BufferTransformer and a ByteBuffer holding data already received 
	 * but not yet processed by that transformer. Such data will be used (together with newly received data)
	 * at next transformation attempt
	 */
	private class BufferTransformerInfo {
		private BufferTransformer transformer;
		private ByteBuffer unprocessedData;

		BufferTransformerInfo(BufferTransformer transformer) {
			this.transformer = transformer;
		}

		BufferTransformer getTransformer() {
			return transformer;
		}

		public void storeUnprocessedData(ByteBuffer transformationInput) {
			if (transformationInput.hasRemaining()) {
				//System.out.println("######## Storing "+transformationInput.remaining()+" bytes for next round");
				unprocessedData = ByteBuffer.allocateDirect(transformationInput.remaining());
				NIOHelper.copyAsMuchAsFits(unprocessedData, transformationInput);
				unprocessedData.flip();
			}
			else {
				// No un-processed data to store
				unprocessedData = null;
			}
		}

		public ByteBuffer attachUnprocessedData(ByteBuffer transformationInput) {
			ByteBuffer actualTransformationInput = transformationInput;
			if (unprocessedData != null && unprocessedData.hasRemaining()) {
				//System.out.println("######## Attaching "+unprocessedData.remaining()+" unprocessed bytes");
				actualTransformationInput = NIOHelper.enlargeAndFillBuffer(unprocessedData, transformationInput.remaining());
				NIOHelper.copyAsMuchAsFits(actualTransformationInput, transformationInput);
				actualTransformationInput.flip();
			}
			return actualTransformationInput;
		}
	} // END of inner class BufferTransformerInfo


}

