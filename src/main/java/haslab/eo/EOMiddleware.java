package haslab.eo;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.*;

import haslab.eo.events.*;
import haslab.eo.msgs.*;

import java.io.*;
import java.net.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.PriorityQueue;
import java.nio.ByteBuffer;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class EOMiddleware {
	private final String id; // identifier of the node
	private IdentifierToAddressBiMapWithLock assocMap = new IdentifierToAddressBiMapWithLock(); // map of associations. Associates node ids to transport addresses.
	// private ReadWriteLock assocLck = new ReentrantReadWriteLock(); // lock for operations related to associations
	private BlockingQueue<AQMsg> algoQueue = new ArrayBlockingQueue<AQMsg>(1000000);
	private BlockingQueue<DQMsg> deliveryQueue = new ArrayBlockingQueue<DQMsg>(1000000);
	private ConcurrentHashMap<String, SendRecord> sr = new ConcurrentHashMap<>();
	private ConcurrentHashMap<String, ReceiveRecord> rr = new ConcurrentHashMap<>(); // TODO - move to the reader thread as a normal hash map
	private DatagramSocket sk;
	int N, P;
	private final int maxAcks = 1;
	private ByteBuffer bb;
	private final int MTUSize = 1400;
	private final int REQSLOT = 1, SLOT = 2, TOKEN = 3, ACK = 4;
	private final byte[] outData; // byte array used to send messages
	private final int bbOffset; // marks where new data may start being written to the array. The node identifier and its length is written before this offset.
	private boolean sendFirstTime = true, receiveFirstTime = true;
	// for P calculations
	private int tcpPort = 12121;
	private int bandwidthIterations = 10000;
	private int rttIterations = 100;
	private int leng = 1024;
	private int N_Multiplier = 4;

	/* ***** Initialization & Constructors ***** */

	private EOMiddleware(String identifier, String addr, int port) throws SocketException, UnknownHostException {
		// TODO - find a better alternative that has an even smaller chance of collision
		// If an identifier is not provided, a random UUID is created
		this.id = identifier != null ? identifier : UUID.randomUUID().toString();

		// if no bind address is provided, the wildcard address is used.
		InetAddress address = addr != null ? InetAddress.getByName(addr) : null;

		sk = new DatagramSocket(port, address);
		sk.setReceiveBufferSize(2000000000);
		System.out.println("UDP DatagramSocket Created: " + sk.getLocalAddress().getHostName() + ":" + sk.getLocalPort());

		// bb = ByteBuffer.allocate(MTUSize); // TODO - why is this needed?
		// allocates byte array and writes the identifier of the node (avoiding the need to write this for every msg)
		outData = new byte[MTUSize];
		bb = ByteBuffer.wrap(outData);
		bb.putInt(this.id.length());
		bb.put(this.id.getBytes());
		bbOffset = Integer.BYTES + this.id.getBytes().length;
	}

	/**
	 * Creates an instance of the middleware.
	 * @param identifier Identifier of the node. Should be globally unique. If 'null' a random identifier is created.
	 * @param address bind address. If 'null' the wildcard address is used.
	 * @param port port number for the UDP socket.
	 * @return instance of the middleware
	 * @throws SocketException
	 * @throws UnknownHostException
	 */
	public static EOMiddleware start(String identifier, String address, int port) throws SocketException, UnknownHostException {
		EOMiddleware eo = new EOMiddleware(identifier, address, port);
		eo.new AlgoThread().start();
		eo.new ReaderThread().start();
		return eo;
	}

	/**
	 * Creates an instance of the middleware.
	 * A random identifier is created for the socket (can be retrieved through the 'getIdentifier()' method)
	 * @param port port number for the UDP socket.
	 * @return instance of the middleware
	 * @throws SocketException
	 * @throws UnknownHostException
	 */
	public static EOMiddleware start(int port) throws SocketException, UnknownHostException {
		return start(null, null, port);
	}

	/* ***** Identifiers and endpoints ***** */
	
	// TODO - create API to register and find associations
	//		-> Create an interface for a source of associations (like a directory service)
	//			-> May have a subscribe method with a callback associated that should be
	//				executed, when an event occurs. Some possible events: "NEW_ASSOCIATION",
	//				"UPDATED_ASSOCIATION", "REMOVED_ASSOCIATION", "ONLINE", "OFFLINE".

	/**
	 * Gets the identifier of the node itself.
	 * @return identifier of the node itself.
	 */
	public String getIdentifier(){
		return this.id;
	}

	/**
	 * Gets the node identifier associated with the given transport address.
	 * @param taddr transport address
	 * @return the node identifier associated with the given transport address,
	 * or 'null' if there is no such association.
	 */
	public String getIdentifier(TransportAddress taddr){
		return assocMap.getIdentifier(taddr);
	}

	/**
	 * Gets the transport address associated with the given node identifier.
	 * @param nodeId node identifier
	 * @return the transport address associated with the given node identifier,
	 * or 'null' if there is no such association.
	 */
	public TransportAddress getTransportAddress(String nodeId){
		return assocMap.getAddress(nodeId);
	}
	
	/* // method when a lock external to the association map is used 
	public void registerAssociation(String nodeId, TransportAddress taddr){
		try{
			this.assocLck.writeLock().lock();

			// finds previous associations
			String prevId = assocMap.getIdentifier(taddr);
			TransportAddress prevAddr = assocMap.getAddress(nodeId);

			// Finds records associated with the address's current identifier,
			// and replaces the identifier of those records.
			if(prevId != null){
				ReceiveRecord rcvRec = rr.remove(prevId);
				if(rcvRec != null)
					rr.put(nodeId, rcvRec);

				SendRecord sndRec = sr.remove(prevId);
				if(sndRec != null)
					sr.put(nodeId, sndRec);
			}

			// Creates the association, and removes any existent associations
			assocMap.put(nodeId, taddr);
		}finally {
			this.assocLck.writeLock().unlock();
		}
	}
	 */

	/**
	 * Creates an association between the given node id and a transport address.
	 * Any existing associations related to the node id or the transport address,
	 * are deleted before creating the new association.
	 * @param nodeId identifier of a node
	 * @param taddr transport address of a node
	 */
	public void registerAssociation(String nodeId, TransportAddress taddr) {
		// finds previous associations
		String prevId = assocMap.getIdentifier(taddr);
		TransportAddress prevAddr = assocMap.getAddress(nodeId);

		// Finds records associated with the address's current identifier,
		// and replaces the identifier of those records.
		if (prevId != null) {
			ReceiveRecord rcvRec = rr.remove(prevId);
			if (rcvRec != null)
				rr.put(nodeId, rcvRec);

			SendRecord sndRec = sr.remove(prevId);
			if (sndRec != null)
				sr.put(nodeId, sndRec);
		}

		// Creates the association, and removes any existent associations
		assocMap.put(nodeId, taddr);
	}
	
	/* ***** Core functionality ***** */
	
	public MsgId send(String nodeId, byte[] msg) throws InterruptedException, IOException {
		if (sendFirstTime) {
			sendFirstTime = false;
			P = calculatePSender(nodeId);
			N = P * N_Multiplier;
			System.out.println("P= " + P + ", N=" + N);
			System.out.println("----------------------------------- \n");
		}
		SendRecord c = sr.get(nodeId);
		if (c != null)
			c.sem.acquire();

		AQMsg aqm = new AQMsg(nodeId, new ClientMsg(nodeId, msg));
		algoQueue.put(aqm);
		return null;  // does not return message id yet
	}

	public void debugPrints(){
		System.out.println("----------- SendRecords -----------");
		for (var entry : sr.entrySet()) {
			System.out.println("receiver: " + entry.getKey());
			System.out.println("msg queue: " + entry.getValue().msg);
			System.out.println("tokens: " + entry.getValue().tok);
		}
		System.out.println("\n----------- ReceiveRecords -----------");
		for (var entry : rr.entrySet()) {
			System.out.println("sender: " + entry.getKey());
			System.out.println("record: " + entry.getValue());
			System.out.println("slots is empty?:" + entry.getValue().slt.isEmpty());
		}
	}

	private boolean netSend(String destId, NetMsg m) throws IOException, InterruptedException {
		// Wraps outData array, and sets the offset and length assuming
		//  that the node identifier and its length are already present in the array.
		bb = ByteBuffer.wrap(outData, bbOffset, MTUSize - bbOffset);

		// writes the identifier of the destination node
		byte[] destIdBytes = destId.getBytes();
		bb.putInt(destIdBytes.length);
		bb.put(destIdBytes);

		// Writes the data related to each specific message
		if (m instanceof ReqSlotsMsg) {
			ReqSlotsMsg rsm = (ReqSlotsMsg) m;
			bb.putInt(REQSLOT).putLong(rsm.s).putLong(rsm.n).putLong(rsm.l).putDouble(rsm.RTT);
			System.out.println("Sent REQSLOTS (s=" + rsm.s + ", n=" + rsm.n +", l=" + rsm.l + ", rtt=" + rsm.RTT + ") to " + destId);
		} else if (m instanceof SlotsMsg) {
			SlotsMsg sm = (SlotsMsg) m;
			bb.putInt(SLOT).putLong(sm.s).putLong(sm.r).putLong(sm.n);
			System.out.println("Sent SLOTS (s=" + sm.s + ", r=" + sm.r +", n=" + sm.n + ") to " + destId);
		} else if (m instanceof TokenMsg) {
			TokenMsg tm = (TokenMsg) m;
			bb.putInt(TOKEN).putLong(tm.s).putLong(tm.r).put(tm.payload);
			System.out.println("Sent TOKEN (s=" + tm.s + ", r=" + tm.r +", payload=" + StandardCharsets.UTF_8.decode(ByteBuffer.wrap(tm.payload)) + ") to " + destId);
		} else if (m instanceof AcksMsg) {
			String print;
			AcksMsg am = (AcksMsg) m;
			bb.putInt(ACK);
			bb.putLong(am.r);
			print = "Sent ACK (r=" + am.r;
			for (int i = 0; i < am.acks.size(); i++) {
				bb.putLong(am.acks.get(i));
				print += ", " + am.acks.get(i);
			}
			print += ") to " + destId;
			System.out.println(print);
		}

		// gets the transport address from the association map
		TransportAddress taddr = assocMap.getAddress(destId);
		if(taddr == null)
			return false;

		DatagramPacket sendPacket = new DatagramPacket(outData, bb.position(), taddr.addr, taddr.port);
		sk.send(sendPacket);
		return true;
	}

	public ClientMsg receive() throws InterruptedException {
		if (receiveFirstTime) {
			receiveFirstTime = false;
			try {
				P = calculatePReceiver();
				N = P * N_Multiplier;
				System.out.println("P= " + P + ", N=" + N);
				System.out.println("----------------------------------- \n");
			} catch (IOException e) {
				e.printStackTrace();
			}
		}
		DQMsg m = deliveryQueue.take();
		return new ClientMsg(m.nodeId, m.msg);
	}

	public void close() {
		//TODO - close middleware
		// terminate threads and close the socket
	}

	// TODO - receive with timeout
	// public Msg receive(long timeout){
	// return null;
	// }

	class AlgoThread extends Thread {
		private long ck = 0;
		private String j;
		private PriorityQueue<Event> pq = new PriorityQueue<Event>(100000, new TimeComparator());
		private long timeout, currentTime;
		private int slotsTimeout = 50000;
		final double ALPHA = 0.2, BETA = 2;
		final double LBOUND = 100, UBOUND = 1000;
		int retransmit = 0;
		double receiverRTT;
		final double reqSlotsMultiplier = 1.5;
		final double tokenMultiplier = 2;
		final double acksMultiplier = 0.25;

		public void run() {
			try {
				currentTime = System.currentTimeMillis();
				while (true) {
					Event eve = pq.peek();
					if (eve == null)
						timeout = Long.MAX_VALUE;
					else
						timeout = eve.time - currentTime;

					AQMsg m = (AQMsg) algoQueue.poll(timeout, TimeUnit.MILLISECONDS);
					currentTime = System.currentTimeMillis();

					if (m != null) {
						j = m.nodeId;

						if (m.msg instanceof ClientMsg) {// Client message received
							ClientMsg eom = (ClientMsg) m.msg;
							byte[] msg = eom.msg;
							SendRecord c = sr.get(j);
							if (c == null) {
								SendRecord s = new SendRecord(ck, 0, msg);
								s.sem = new Semaphore(P);
								sr.put(j, s);
								requestSlots(j);
							} else {
								if (c.envelopes.size() != 0) {
									long e = c.envelopes.dequeue();
									if (c.envelopes.size() == (N - 1))
										requestSlots(j);
									TokenMsg tm = new TokenMsg(id, j, e, c.rck, msg);
									TokenRecord tr = new TokenRecord(j, e, c.rck, msg, currentTime);
									c.tok.put(e, tr);
									pq.add(new TokenEvent(j, tr, msgTimeout(currentTime, c.RTT, tokenMultiplier)));
									netSend(j, tm);
								} else
									c.msg.add(msg);
							}
						} else if (m.msg instanceof ReqSlotsMsg) {// ReqSlots Received
							ReqSlotsMsg rm = (ReqSlotsMsg) m.msg;
							long s = rm.s;
							long n = rm.n;
							long l = rm.l;
							receiverRTT = rm.RTT;

							ReceiveRecord c = rr.get(j);
							if (c == null) {
								c = new ReceiveRecord(s, ck);
								rr.put(j, c);
								ck += 1;
								pq.add(new SlotsEvent(j, currentTime + slotsTimeout, currentTime));
							}

							if (n > 0) {
								if ((s + n) > c.sck) {
									c.slt.extendTo(s + n);
									c.sck = s + n;
								}
								c.lastSlotsSendTime = currentTime;
								netSend(j, new SlotsMsg(id, j, s, c.rck, n));
							} else {
								c.slt.removeSmallerThan(l);
							}

							if (c.slt.size() == 0)
								rr.remove(j);

						} else if (m.msg instanceof SlotsMsg) {// Slots Received
							SlotsMsg rm = (SlotsMsg) m.msg;
							long s = rm.s;
							long r = rm.r;
							long n = rm.n;
							SendRecord c = sr.get(j);

							if (c == null) {
								netSend(j, new ReqSlotsMsg(id, j, ck, 0, ck, 0));
							} else if (s == c.sck) {
								// calculating RTT
								long newRTT = currentTime - c.reqSlotsTime;
								c.RTT = (ALPHA * c.RTT) + ((1 - ALPHA) * newRTT);
								c.rck = r;
								c.envelopes.append(s + n);
								c.sck = s + n;
								while ((c.envelopes.size() != 0) && (c.msg.size() != 0)) {
									long e = c.envelopes.dequeue();
									byte[] msg = c.msg.poll();
									TokenMsg tm = new TokenMsg(id, j, e, c.rck, msg);
									TokenRecord tr = new TokenRecord(j, e, c.rck, msg, currentTime);
									c.tok.put(e, tr);
									pq.add(new TokenEvent(j, tr, msgTimeout(currentTime, c.RTT, tokenMultiplier)));
									netSend(j, tm);
								}
								requestSlots(j);
							}
						} else if (m.msg instanceof TokenMsg) {// Token Received
							TokenMsg rm = (TokenMsg) m.msg;
							long s = rm.s;
							long r = rm.r;
							byte[] msg = rm.payload;
							ReceiveRecord c = rr.get(j);
							System.out.println("Token Received: "+ rm + "| ReceiveRecord: (" + c + ")");
							if ((c != null) && (r == c.rck)) {
								if (c.slt.contains(s)) {
									if (deliveryQueue.offer(new DQMsg(j, msg))) { // deliver(msg)
										System.out.println("Message added to delivery queue.");
										c.slt.remove(s);
										sendAck(j, c, s, r, msgTimeout(currentTime, receiverRTT, acksMultiplier));
									}
								} else {
									sendAck(j, c, s, r, msgTimeout(currentTime, receiverRTT, acksMultiplier));
								}
							}
						} else if (m.msg instanceof AcksMsg) {// Ack Received
							AcksMsg rm = (AcksMsg) m.msg;
							ArrayList<Long> acks = rm.acks;
							long r = rm.r;
							SendRecord c = sr.get(j);
							if ((c != null) && (r == c.rck)) {
								for (int i = 0; i < acks.size(); i++) {
									TokenRecord tr = c.tok.get(acks.get(i));
									if (tr != null) {
										c.tok.remove(acks.get(i));
										c.sem.release();
										tr.acked = true;
									}
								}
							}
						}
					}
					// Periodically
					while (true) {
						eve = pq.peek();
						if (eve == null || eve.time > currentTime)
							break;

						pq.poll();
						String j = eve.nodeId;

						if (eve instanceof ReqSlotsEvent) {
							ReqSlotsEvent rse = (ReqSlotsEvent) eve;
							SendRecord c = sr.get(j);
							if (c != null)
								if (c.reqSlotsTime == rse.lastReqSlotsSendTime)
									requestSlots(j);
						} else if (eve instanceof SlotsEvent) {
							SlotsEvent se = (SlotsEvent) eve;
							ReceiveRecord c = rr.get(j);
							if (c != null) {
								if (se.lastSlotsSendTime != c.lastSlotsSendTime) {
									c.lastSlotsSendTime = currentTime;
									netSend(j, new SlotsMsg(id, j, c.sck, c.rck, 0));
								}
								pq.add(new SlotsEvent(j, currentTime + slotsTimeout, c.lastSlotsSendTime));
							}
						} else if (eve instanceof TokenEvent) {
							TokenEvent te = (TokenEvent) eve;
							TokenRecord tr = te.t;
							if (!tr.acked) {
								SendRecord c = sr.get(j);
								if (c != null) {
									System.out.println("c.rck:" + c.rck + " | tr.r" + tr.r + " | c.rck == tr.r : " + (c.rck == tr.r));
									if ((c.rck == tr.r) && (c.tok.containsKey(tr.s))) {
										if(retransmit % 20 == 0)
											System.out.println("Re-transmitting: " + retransmit);
										retransmit++;
										pq.add(new TokenEvent(j, tr,
												msgTimeout(currentTime, c.RTT, tokenMultiplier * 3)));
										netSend(j, new TokenMsg(id, j, tr.s, tr.r, tr.m));
									}
								}else System.out.println("c == null");
							}
						} else if (eve instanceof AcksEvent) {
							AcksEvent ae = (AcksEvent) eve;
							ReceiveRecord c = rr.get(j);
							if (c != null) {
								if ((c.oldestAck == ae.oldestAck) && (!c.acks.isEmpty())) {
									netSend(j, new AcksMsg(id, j, c.acks, c.rck));
									c.acks.clear();
								}
							}
						}
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		public void requestSlots(String j) {
			SendRecord c = sr.get(j);
			long n = N + c.msg.size() - c.envelopes.size();

			try {
				if (n > 0) {
					long e;
					if (c.tok.size() != 0)
						e = c.tok.firstKey();
					else if (c.envelopes.size() != 0)
						e = c.envelopes.first();
					else
						e = c.sck;

					c.reqSlotsTime = currentTime;
					pq.add(new ReqSlotsEvent(j, msgTimeout(currentTime, c.RTT, reqSlotsMultiplier), currentTime));
					netSend(j, new ReqSlotsMsg(id, j, c.sck, n, e, c.RTT));
				}
				// There are no messages and the number of envelopes equals N (base number of slots that should be requested)
				else if (c.tok.size() == 0 && c.msg.size() == 0) {
					// todo - This msg that "nullifies" all slots at the receiver, is only sent once,
					//  therefore if it does not arrives, the receiver record will hang forever.
					netSend(j, new ReqSlotsMsg(id, j, c.sck, 0, c.sck, c.RTT));
					ck = Math.max(ck, c.sck);
					sr.remove(j);
				}
			} catch (Exception ee) {
				ee.printStackTrace();
			}
		}

		public long msgTimeout(long currentTime, double RTT, double multiplier) {
			return (long) Math.min(UBOUND, Math.max(LBOUND, (multiplier * RTT))) + currentTime;
		}

		public void sendAck(String j, ReceiveRecord c, long s, long r, long acksTimeout)
				throws IOException, InterruptedException {
			if (c != null) {
				if (c.acks.isEmpty()) {
					pq.add(new AcksEvent(j, currentTime, acksTimeout));
					c.oldestAck = currentTime;
				}
				c.acks.add(s);
				if (c.acks.size() == maxAcks) {
					netSend(j, new AcksMsg(id, j, c.acks, r));
					c.acks.clear();
				}
			} else {
				ArrayList<Long> ack = new ArrayList<Long>();
				ack.add(s);
				netSend(j, new AcksMsg(id, j, ack, r));
			}
		}
	}

	private String readStringFromBuffer(ByteBuffer buff){
		if(buff == null || buff.remaining() < Integer.BYTES)
			return null;
		int strLen = buff.getInt();
		if(buff.remaining() < strLen)
			return null;
		byte[] strArray = new byte[strLen];
		buff.get(strArray);
		return new String(strArray);
	}

	class ReaderThread extends Thread {
		public void run() {
			ByteBuffer b = ByteBuffer.allocate(MTUSize);
			byte[] incomingData = new byte[MTUSize];
			try {
				while (true) {
					Msg m = null;
					DatagramPacket in_pkt = new DatagramPacket(incomingData, incomingData.length);
					sk.receive(in_pkt);
					b = ByteBuffer.wrap(incomingData, 0, in_pkt.getLength());

					String srcId = readStringFromBuffer(b);
					String destId = readStringFromBuffer(b);
					int msgType = b.getInt();

					// Discards message if the destination id does not match the node id
					// or if the source identifier is null
					if(id.equals(destId) && srcId != null) {
						// updates id to transport address association (required for messages to be forwarded correctly)
						TransportAddress taddr = new TransportAddress(in_pkt.getAddress().getHostAddress(), in_pkt.getPort());
						// update is only performed if needed, as to not slow other threads by using a write lock
						if(!taddr.equals(assocMap.getAddress(srcId)))
							registerAssociation(srcId, taddr);

						if (msgType == REQSLOT) {
							ReqSlotsMsg rsm = new ReqSlotsMsg(srcId, destId, b.getLong(), b.getLong(), b.getLong(), b.getDouble());
							m = rsm;
							System.out.println("Received REQSLOTS (s=" + rsm.s + ", n=" + rsm.n + ", l=" + rsm.l + ", rtt=" + rsm.RTT + ") from " + srcId);
						} else if (msgType == SLOT) {
							SlotsMsg sm = new SlotsMsg(srcId, destId, b.getLong(), b.getLong(), b.getLong());
							m = sm;
							System.out.println("Received SLOTS (s=" + sm.s + ", r=" + sm.r + ", n=" + sm.n + ") from " + srcId);
						} else if (msgType == TOKEN) {
							long s = b.getLong();
							long r = b.getLong();
							byte[] payload = new byte[b.remaining()];
							b.get(payload);
							TokenMsg tm = new TokenMsg(srcId, destId, s, r, payload);
							m = tm;
							System.out.println("Received TOKEN (s=" + tm.s + ", r=" + tm.r + ", payload=" + StandardCharsets.UTF_8.decode(ByteBuffer.wrap(tm.payload)) + ") from " + srcId);
						} else if (msgType == ACK) {
							ArrayList<Long> acks = new ArrayList<Long>();
							long r = b.getLong();
							int numAcks = b.remaining() / 8;
							for (int i = 0; i < numAcks; i++)
								acks.add(b.getLong());
							AcksMsg am = new AcksMsg(srcId, destId, acks, r);
							m = am;

							String print = "Received ACK (r=" + am.r;
							for (int i = 0; i < am.acks.size(); i++) {
								print += ", " + am.acks.get(i);
							}
							print += ") from " + srcId;
							System.out.println(print);
						}
						AQMsg aqm = new AQMsg(srcId, m);
						algoQueue.put(aqm);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	// todo - correr noutra thread para dar update aos valores dos slots e controlo de fluxo?
	public int calculatePReceiver() throws IOException, InterruptedException {
		long startTime = System.currentTimeMillis();
		int p = 0;
		try (ServerSocket serverSocket = new ServerSocket(tcpPort)) {
			System.out.println("Testing the network...");
			Socket socket = serverSocket.accept();
			InputStream input = socket.getInputStream();
			BufferedReader reader = new BufferedReader(new InputStreamReader(input));
			OutputStream output = socket.getOutputStream();
			PrintWriter writer = new PrintWriter(output, true);

			// Calculating RTT
			for (int i = 0; i < rttIterations; i++) {
				String m = reader.readLine();
				writer.println(m);
			}
			double TCP_RTT = Double.parseDouble(reader.readLine());// receive TCP_RTT

			// Calculating bandwidth, and then P
			long start = System.currentTimeMillis();
			for (int i = 0; i < bandwidthIterations; i++) {
				reader.readLine();
			}
			long duration = System.currentTimeMillis() - start;
			double mps = bandwidthIterations / (duration / 1000.0f);
			double bandwidth = mps * leng * 8 / 1000000;
			System.out.println("Bandwidth: " + bandwidth + ", mps: " + mps + ", TCP_RTT: " + TCP_RTT);
			p = (int) ((bandwidth * 1000000 / 8) * (TCP_RTT / 1000)) / leng;
			writer.println(p);
		} catch (IOException ex) {
			System.out.println("Server exception: " + ex.getMessage());
			ex.printStackTrace();
		}
		long time = System.currentTimeMillis() - startTime;
		System.out.println("Time to calculate P: " + time + "ms.");
		return p;
	}

	public int calculatePSender(String nodeId) {
		long startTime = System.currentTimeMillis();
		String m = new String(new char[leng]).replace('\0', ' ');
		int p = 0;

		TransportAddress taddr = assocMap.getAddress(nodeId);
		assert taddr != null;

		try (Socket socket = new Socket(taddr.addr.getHostAddress(), tcpPort)) {
			System.out.println("Testing the network...");
			OutputStream output = socket.getOutputStream();
			PrintWriter writer = new PrintWriter(output, true);
			InputStream input = socket.getInputStream();
			BufferedReader reader = new BufferedReader(new InputStreamReader(input));

			// Calculating RTT
			long start = System.currentTimeMillis();
			for (int i = 0; i < rttIterations; i++) {
				writer.println(" ");
				reader.readLine();
			}
			long duration = System.currentTimeMillis() - start;
			double tcpRTT = ((double) duration) / ((double) rttIterations);
			writer.println(tcpRTT); // send TCP_RTT

			// Calculating bandwidth, and then P
			for (int i = 0; i < bandwidthIterations; i++) {
				writer.println(m);
			}
			p = Integer.parseInt(reader.readLine());
		} catch (UnknownHostException ex) {
			System.out.println("Server not found: " + ex.getMessage());
		} catch (IOException ex) {
			System.out.println("I/O error: " + ex.getMessage());
		}

		long time = System.currentTimeMillis() - startTime;
		System.out.println("Time to calculate P: " + time + "ms.");
		return p;
	}
}

class TimeComparator implements Comparator<Event> {
	public int compare(Event e1, Event e2) {
		return (int) (e1.time - e2.time);
	}
}
