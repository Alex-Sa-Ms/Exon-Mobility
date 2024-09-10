package haslab.eo;

import java.util.*;
import java.util.concurrent.*;

import haslab.eo.associations.DiscoveryNotifier;
import haslab.eo.associations.DiscoveryService;
import haslab.eo.associations.DiscoverySubscriber;
import haslab.eo.associations.IdentifierToAddressBiMapWithLock;
import haslab.eo.associations.events.*;
import haslab.eo.events.*;
import haslab.eo.msgs.*;
import haslab.eo.exceptions.ClosedException;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class EOMiddleware implements DiscoverySubscriber {
	// for transport address and node identifiers associations
	private final String id; // identifier of the node
	private IdentifierToAddressBiMapWithLock assocMap = new IdentifierToAddressBiMapWithLock(); // map of associations. Associates node ids to transport addresses.
	private DiscoveryService assocSrc = null;
	private DiscoveryNotifier assocNotifier = null;

	// for graceful close operation
	private final int RUNNING = 1, CLOSING = 2, CLOSED = 3;
	private int state = RUNNING;
	private ReentrantLock stateLck = new ReentrantLock();
	private final AlgoThread algoThread = new AlgoThread();
	private final ReaderThread readerThread = new ReaderThread();

	// for message identification and receipts
	// 		- receipts are used to inform that a message sent has been received by the destination node.
	private AtomicLong idCounter = new AtomicLong(Long.MIN_VALUE); // counter used to generate message identifiers
	private Queue<MsgId> receiptsQueue = new LinkedList<>(); // queue to store receipts
	private ReentrantLock receiptsLck = new ReentrantLock();
	private Condition receiptsCond = receiptsLck.newCondition();

	// for core algorithm
	private BlockingQueue<AQMsg> algoQueue = new ArrayBlockingQueue<AQMsg>(1000000);
	private Queue<DQMsg> deliveryQueue = new ArrayDeque<>(1000000); // not a blocking queue because a custom lock is used to ensure thread-safe behaviour
	private ReentrantLock deliveryLck = new ReentrantLock();
	private Condition deliveryCond = deliveryLck.newCondition();
	private Map<String, SendRecord> sr = new ConcurrentHashMap<>();
	private Map<String, ReceiveRecord> rr = new ConcurrentHashMap<>();
	private DatagramSocket sk;

	// base window-size for the flow control.
	// The default value is conservative. It assumes a bandwidth of 100Mbits/s,
	// an RTT of 50 ms and an average message size of 1 KB.
	private int P = (int) (100000000f * (50f / 1000f) / (1000f * 8f));
	// N = P * N_Multiplier
	private int N_Multiplier = 4;
	// base number of slots that should be requested
	private int N = P * N_Multiplier;
	private final int maxAcks = 1;
	private final int MTUSize = 1400;
	private final int REQSLOT = 1, SLOT = 2, TOKEN = 3, ACK = 4;
	private final byte[] outData; // byte array used to send messages
	private ByteBuffer bb;
	private final int bbOffset; // marks where new data may start being written to the array. The node identifier and its length is written before this offset.

	/* ***** Initialization & Constructors ***** */

	private EOMiddleware(String identifier, String addr, Integer port, Integer P, Integer N) throws SocketException, UnknownHostException {
		// If an identifier is not provided, a random identifier is created
		this.id = identifier != null ? identifier : System.nanoTime() + "-" + UUID.randomUUID();

		// if no bind address is provided, the wildcard address is used.
		InetAddress address = addr != null ? InetAddress.getByName(addr) : null;

		sk = port != null ? new DatagramSocket(port, address) : new DatagramSocket();
		sk.setReceiveBufferSize(2000000000);
		System.out.println("UDP DatagramSocket Created: " + sk.getLocalAddress().getHostName() + ":" + sk.getLocalPort());

		// Every msg needs to contain the identifier of both the sender
		// and the receiver. Since the sender identifier, for outgoing messages,
		// always matches the identifier of the node itself, the byte array
		// is allocated and the identifier is written in the array when creating
		// the instance of the middleware. By doing this, when sending a message
		// the new content only needs to be written after the calculated offset (bbOffset).
		outData = new byte[MTUSize];
		bb = ByteBuffer.wrap(outData);
		bb.putInt(this.id.length());
		bb.put(this.id.getBytes());
		bbOffset = Integer.BYTES + this.id.getBytes().length;

		// sets P and N values
		if(P != null) {
			if(P <= 0)
				throw new IllegalArgumentException("P must be a 'null' or a positive integer.");
			this.P = P;
		}
		if(N != null) {
			if(N <= 0)
				throw new IllegalArgumentException("N must be a 'null' or a positive integer.");
			this.N = N;
		}
	}

	/**
	 * Creates an instance of the middleware.
	 * @param identifier Identifier of the node itself. Should be globally unique.
	 *                      If 'null' a random identifier is created.
	 * @param address bind address. If 'null' the wildcard address is used.
	 * @param port local port number for the UDP socket. If 'null' the wildcard address
	 *                and an available port are used.
	 * @param P number of messages that can be in transit for each destination node.
	 *             Can be calculated using the following formula: P = bandwidth * RTT / msg_size.
	 *          	Should not be equal or lower than 0. If not provided, a conservative value is used,
	 *          	which assumes a bandwidth of 100Mbits/s and an RTT of 50 ms.
	 * @param N number of
	 * @return instance of the middleware
	 * @throws SocketException
	 * @throws UnknownHostException
	 * @throws IllegalArgumentException if an invalid argument is provided.
	 */
	public static EOMiddleware start(String identifier, String address, Integer port, Integer P, Integer N) throws SocketException, UnknownHostException {
		EOMiddleware eo = new EOMiddleware(identifier, address, port, P, N);
		eo.recoverState(); // recovers state if it exists
		// register itself
		eo.registerAssociation(identifier, new TransportAddress(eo.getLocalAddress(), eo.getLocalPort()));
		eo.algoThread.start();
		eo.readerThread.start();
		return eo;
	}

	public static EOMiddleware start(String identifier, String address, Integer port, Integer P) throws SocketException, UnknownHostException{
		return start(identifier, address, port, P, null);
	}

	public static EOMiddleware start(String identifier, String address, Integer port) throws SocketException, UnknownHostException{
		return start(identifier, address, port, null, null);
	}

	public static EOMiddleware start(String identifier, Integer port, Integer P) throws SocketException, UnknownHostException{
		return start(identifier, null, port, P, null);
	}

	public static EOMiddleware start(String identifier, Integer port) throws SocketException, UnknownHostException{
		return start(identifier, null, port, null, null);
	}

	DatagramSocket getDatagramSocket() {
		return sk;
	}

	/* ***** Identifiers and endpoints ***** */

	/**
	 * Gets the identifier of the node itself.
	 * @return identifier of the node itself.
	 */
	public String getIdentifier(){
		return this.id;
	}

	public InetAddress getLocalAddress(){
		return sk.getLocalAddress();
	}

	public int getLocalPort(){
		return sk.getLocalPort();
	}

	/**
	 * Gets the node identifier associated with the given transport address.
	 * @param taddr transport address
	 * @return the node identifier associated with the given transport address,
	 * or 'null' if there is no such association.
	 */
	public String getIdentifier(TransportAddress taddr){
		String identifier = assocMap.getIdentifier(taddr);
		if(assocSrc != null && identifier == null) {
			identifier = assocSrc.getIdentifier(taddr);

			// if there is no association in the "cache" (assocMap),
			// but an association is found in the association source,
			// updates the assocMap to avoid unnecessary requests
			if(identifier != null) {
				assocMap.put(identifier, taddr);

				// since the association was retrieved from the
				// association source, subscribes to the node notifications
				if(assocNotifier != null)
					assocNotifier.subscribeToNode(this, identifier);
			}
		}
		return identifier;
	}

	/**
	 * Gets the transport address associated with the given node identifier.
	 * @param nodeId node identifier
	 * @return the transport address associated with the given node identifier,
	 * or 'null' if there is no such association.
	 */
	public TransportAddress getTransportAddress(String nodeId){
		TransportAddress taddr = assocMap.getAddress(nodeId);
		if(assocSrc != null && taddr == null) {
			taddr = assocSrc.getTransportAddress(nodeId);

			// if there is no association in the "cache" (assocMap),
			// but an association is found in the association source,
			// updates the assocMap to avoid unnecessary requests
			if(taddr != null) {
				assocMap.put(nodeId, taddr);

				// since the association was retrieved from the
				// association source, subscribes to the node notifications
				if(assocNotifier != null)
					assocNotifier.subscribeToNode(this, nodeId);
			}
		}
		return taddr;
	}

	/**
	 * Register, locally, an association between the given node id and a transport address.
	 * Any existing associations related to the node id or the transport address,
	 * are deleted before creating the new association.
	 * If an association source that supports subscribing events related to the associations,
	 * the events related to the specified node are subscribed.
	 * @param nodeId identifier of a node
	 * @param taddr transport address of a node
	 */
	public void registerAssociation(String nodeId, TransportAddress taddr) {
		// Creates the association, and removes any existent associations
		this.assocMap.put(nodeId, taddr);

		// subscribes to the node's association events
		// if the source of associations supports it
		if(this.assocNotifier != null)
			this.assocNotifier.subscribeToNode(this, nodeId);
	}

	/**
	 * Sets source of associations. May be 'null'.
	 * The association source is consulted when an
	 * association is not found locally.
	 * @param source source of associations
	 */
	public void setAssociationSource(DiscoveryService source){
		// remove all subscriptions from the previous notifier
		if(this.assocNotifier != null)
			for (String nodeId : this.assocMap.getIdentifiers())
				assocNotifier.unsubscribeFromNode(this,nodeId);

		// sets new association source
		this.assocSrc = source;

		// sets new association notifier
		this.assocNotifier = source.getAssociationNotifier();

		// create the subscriptions in the new association notifier
		if(this.assocNotifier != null)
			for (String nodeId : this.assocMap.getIdentifiers())
				assocNotifier.subscribeToNode(this,nodeId);
	}

	/* ***** Association Subscriber functionality ***** */

	@Override
	public void notify(AssociationEvent ev) {
		switch (ev){
			case UpdatedAssociationEvent uae -> {
				// creates/updates local association
				this.assocMap.put(uae.nodeId, uae.taddr);
				System.out.println("Updated Association: " + uae.nodeId + "<->" + uae.taddr);
			}
			/*
			// At the moment only the "updated association" event is supported.

			// The online event is useful to update the association transport address,
			//	and resume sending messages only when the node is detected as online
			case OnlineEvent one -> {

			}
			// The offline event is useful to seize the action of sending messages
			// temporarily while the node is seen as offline.
			case OfflineEvent offe -> {

			}
			*/
			default -> {}
		}
	}

	@Override
	public int hashCode() {
		return id.hashCode();
	}

	/* ***** Graceful Close ***** */

	/**
	 * Initiates the closing process. Signals threads that they should
	 * finish their work and waits for them to terminate
	 */
	public void close() throws InterruptedException {
		int currState;
		try{
			stateLck.lock();
			if(state == RUNNING){
				// sets the state to 'closing'
				state = CLOSING;
				// Creates a CloseMsg to inform the algoThread that it should finish
				// its tasks, terminate the ReaderThread and then return
				algoQueue.add(new AQMsg(null, new CloseMsg()));
			}
			// Saves the current state because to avoid a deadlock the lock must be
			// released before waiting for the algoThread to terminate.
			currState = state;
		}finally { stateLck.unlock(); }

		if(currState != CLOSED) {
			// Wait for the algoThread to return
			algoThread.join();
		}
	}

	/**
	 * Initiates the closing process.
	 * Signals threads that they should finish their work
	 * and returns immediately without waiting for them to terminate.
	 */
	public void closeNoWait(){
		try{
			stateLck.lock();
			if(state == RUNNING){
				// sets the state to 'closing'
				state = CLOSING;
				// Creates a CloseMsg to inform the algoThread that it should finish
				// its tasks, terminate the ReaderThread and then return
				algoQueue.add(new AQMsg(null, new CloseMsg()));
			}
		}finally { stateLck.unlock(); }
	}

	private void setState(int newState){
		try{
			stateLck.lock();
			state = newState;
		}finally {
			stateLck.unlock();
		}
	}

	private int getState(){
		try{
			stateLck.lock();
			return state;
		}finally {
			stateLck.unlock();
		}
	}

	public boolean isClosed() {
		try{
			stateLck.lock();
			return state == CLOSED;
		}finally { stateLck.unlock(); }
	}

	public boolean allowsReceiving(){
		try{
			stateLck.lock();
			return state != CLOSED;
		}finally { stateLck.unlock(); }
	}

	public boolean allowsSending(){
		try{
			stateLck.lock();
			return state == RUNNING;
		}finally { stateLck.unlock(); }
	}

	private String getStateBackupFileName(){
		return id + ".eomback";
	}

	/**
	 * Creates a state backup file, and persits the state of the instance.
	 * Currently, as the instance is only closed when there are no send and receive
	 * records, then the only value that needs to be saved is the clock.
	 * Must be executed by the algoThread.
	 */
	private void persistState() throws IOException {
		try {
			DataOutputStream out = new DataOutputStream(new FileOutputStream(getStateBackupFileName(), false));
			out.writeLong(algoThread.ck);
			out.flush();
			out.close();
			System.out.println("State persisted in backup file: \"" + getStateBackupFileName() + "\"");
		}catch (IOException ioe){
			System.out.println("Could not persist state. State clock: " + algoThread.ck);
		}
	}

	/**
	 * If there is a state backup file, recovers the state. Must be executed before starting the algoThread.
	 * @return 'true' if state was recovered from the backup file.
	 * 'false' if the file does not exist, or if the file is not valid.
	 */
	private void recoverState() {
        try {
			DataInputStream in = new DataInputStream(new FileInputStream(getStateBackupFileName()));
			algoThread.ck = in.readLong();
			in.close();
			System.out.println("Recovered state through the backup file.");
		} catch (FileNotFoundException e) {
			System.out.println("State backup file not found. No state was recovered.");
        } catch (IOException e) {
			System.out.println("Error: Could not read state backup file. No state was recovered.");
        }
    }

	/* ***** Core functionality ***** */

	/**
	 * Creates message identifier for an outgoing message.
	 * @return message identifier
	 */
	private MsgId createOutgoingMsgId(){
		return new MsgId(idCounter.getAndIncrement());
	}

	/**
	 * Sends the provided payload. The send operation can block due to the flow control mechanism.
	 * @param nodeId identifier of the destination node
	 * @param msg payload
	 * @param receipt 'true' if a receipt is desired. 'false' otherwise.
	 * @return The identifier of the message that can be used to track
	 * the reception state if a receipt was requested.
	 * @throws InterruptedException
	 * @throws IOException
	 */
	public MsgId send(String nodeId, byte[] msg, boolean receipt) throws InterruptedException, IOException {
		SendRecord c = sr.get(nodeId);
		boolean acquired = false;
		if (c != null) {
			c.sem.acquire();
			acquired = true;
		}

		try{
			stateLck.lock();
			if(state != RUNNING) {
				// release semaphore since a message won't be sent
				if(acquired) c.sem.release();
				// throws runtime closed exception if an attempt of sending a message
				// is performed after invoking the close() method
				throw new RuntimeException(new ClosedException("EOMiddleware is closed."));
			}
			// generates message identifier
			MsgId msgId = createOutgoingMsgId();
			// the identifier is only attached to the client message if a receipt was requested
			AQMsg aqm = new AQMsg(nodeId, new ClientMsg(nodeId, msg, receipt ? msgId : null));
			algoQueue.put(aqm);
			return msgId;
		}finally {
			stateLck.unlock();
		}
	}

	/**
	 * Sends the provided payload. The send operation can block due to the flow control mechanism.
	 * @param nodeId identifier of the destination node
	 * @param msg payload
	 * @throws InterruptedException
	 * @throws IOException
	 */
	public void send(String nodeId, byte[] msg) throws InterruptedException, IOException {
		send(nodeId,msg,false);
	}

	/**
	 * Sends the provided payload. As the send operation can block due to the flow control mechanism,
	 * a timeout may be provided to determine the maximum amount of time that can be waited before
	 * giving up on queuing the message. If the operation times out, a 'null' is returned.
	 * @param nodeId identifier of the destination node
	 * @param msg payload
	 * @param receipt 'true' if a receipt is desired. 'false' otherwise
	 * @param timeout maximum time (in milliseconds) to wait for the message to be
	 *                   queued for a sending operation.
	 * @return The identifier of the message or 'null' if the time expired.
	 * @throws InterruptedException
	 * @throws IOException
	 */
	public MsgId send(String nodeId, byte[] msg, boolean receipt, long timeout) throws InterruptedException, IOException {
		// If a send record exists attempts to acquire a permit.
		// If the operation of acquiring the permit times out,
		//	returns null.
		SendRecord c = sr.get(nodeId);
		boolean acquired = false;
		if (c != null) {
			if (!c.sem.tryAcquire(timeout, TimeUnit.MILLISECONDS))
				return null;
			else
				acquired = true;
		}

		try{
			stateLck.lock();
			if(state != RUNNING) {
				if(acquired) c.sem.release(); // release semaphore since a message won't be sent
				throw new RuntimeException(new ClosedException("EOMiddleware is closed."));
			}
			// generates message identifier
			MsgId msgId = createOutgoingMsgId();
			// the identifier is only attached to the client message if a receipt was requested
			AQMsg aqm = new AQMsg(nodeId, new ClientMsg(nodeId, msg, receipt ? msgId : null));
			algoQueue.put(aqm);
			return msgId;
		}finally {
			stateLck.unlock();
		}
	}

	/**
	 * Sends the provided payload. As the send operation can block due to the flow control mechanism,
	 * a timeout may be provided to determine the maximum amount of time that can be waited before
	 * giving up on queuing the message. If the operation times out, a 'null' is returned.
	 * @param nodeId identifier of the destination node
	 * @param msg payload
	 * @param timeout maximum time (in milliseconds) to wait for the message to be
	 *                   queued for a sending operation.
	 * @return The identifier of the message or 'null' if the time expired.
	 * @throws InterruptedException
	 * @throws IOException
	 */
	public boolean send(String nodeId, byte[] msg, long timeout) throws InterruptedException, IOException {
		return send(nodeId,msg,false,timeout) != null;
	}

	public void debugPrints(){
		System.out.println("----------- Threads -----------");
		System.out.println("algoThread: " + (algoThread.isAlive() ? "alive" : "terminated"));
		System.out.println("readerThread: " + (readerThread.isAlive() ? "alive" : "terminated"));
		System.out.println("----------- AlgoQueue -----------");
		System.out.println(algoQueue);
		System.out.println("----------- EventQueue -----------");
		System.out.println(algoThread.pq);
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
		System.out.println("\n----------- Delivery Queue -----------");
		System.out.println("size: " + deliveryQueue.size()); // + "\n" + deliveryQueue);
		System.out.flush();
	}

	private boolean netSend(String destId, NetMsg m) throws IOException, InterruptedException {
		// gets the transport address from the association map
		TransportAddress taddr = getTransportAddress(destId);
		if(taddr == null)
			return false;

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
			//System.out.println("Sent REQSLOTS (s=" + rsm.s + ", n=" + rsm.n +", l=" + rsm.l + ", rtt=" + rsm.RTT + ") to " + destId);
		} else if (m instanceof SlotsMsg) {
			SlotsMsg sm = (SlotsMsg) m;
			bb.putInt(SLOT).putLong(sm.s).putLong(sm.r).putLong(sm.n);
			//System.out.println("Sent SLOTS (s=" + sm.s + ", r=" + sm.r +", n=" + sm.n + ") to " + destId);
		} else if (m instanceof TokenMsg) {
			TokenMsg tm = (TokenMsg) m;
			bb.putInt(TOKEN).putLong(tm.s).putLong(tm.r).put(tm.payload);
			//System.out.println("Sent TOKEN (s=" + tm.s + ", r=" + tm.r +", payload=" + StandardCharsets.UTF_8.decode(ByteBuffer.wrap(tm.payload)) + ") to " + destId);
		} else if (m instanceof AcksMsg) {
			AcksMsg am = (AcksMsg) m;
			bb.putInt(ACK);
			bb.putLong(am.r);
			//String print = "Sent ACK (r=" + am.r;
			for (int i = 0; i < am.acks.size(); i++) {
				bb.putLong(am.acks.get(i));
				//print += ", " + am.acks.get(i);
			}
			//print += ") to " + destId;
			//System.out.println(print);
		}

		DatagramPacket sendPacket = new DatagramPacket(outData, bb.position(), taddr.addr, taddr.port);
		sk.send(sendPacket);
		return true;
	}

	public ClientMsg receive() throws InterruptedException {
		try {
			deliveryLck.lock();
			while(deliveryQueue.isEmpty()) {
				// Throws a runtime exception if an attempt of receiving a message is performed
				// after the middleware is closed.
				if(getState() == CLOSED)
					throw new RuntimeException(new ClosedException("EOMiddleware is closed."));
				deliveryCond.await();
			}
			DQMsg m = deliveryQueue.poll();
			return new ClientMsg(m.nodeId, m.msg);
		} finally {
			deliveryLck.unlock();
		}
	}

	/**
	 *
	 * @param timeout Interval of time, in milliseconds,
	 *                   that the caller is willing to wait
	 *                   for a message to be received.
	 * @return message or 'null' if the time expired before a
	 * 				message could have been received
	 * @throws InterruptedException
	 */
	public ClientMsg receive(long timeout) throws InterruptedException {
		try {
			long stopTime = System.currentTimeMillis() + timeout;
			deliveryLck.lock();
			while(deliveryQueue.isEmpty() && timeout > 0) {
				// Throws a runtime exception if an attempt of receiving a message is performed
				// after the middleware is closed.
				if(getState() == CLOSED)
					throw new RuntimeException(new ClosedException("EOMiddleware is closed."));
				deliveryCond.await(timeout, TimeUnit.MILLISECONDS);
				timeout = stopTime - System.currentTimeMillis();
			}
			DQMsg m = deliveryQueue.poll();
			return m != null ? new ClientMsg(m.nodeId, m.msg) : null;
		} finally {
			deliveryLck.unlock();
		}
	}

	/* ***** Check receipts ***** */

	/**
	 * Emits a receipt, i.e., adds a receipt to the receipts queue.
	 * @param receipt receipt to be added to the queue
	 */
	private void emitReceipt(MsgId receipt){
		try{
			receiptsLck.lock();
			receiptsQueue.add(receipt);
			receiptsCond.signal();
		}finally {
			receiptsLck.unlock();
		}
	}

	/**
	 * Waits for a receipt to be available and returns it.
	 * @return a receipt, i.e., an identifier of a message
	 * that has been received by the destination node.
	 * @throws InterruptedException
	 */
	public MsgId takeReceipt() throws InterruptedException {
		try{
			receiptsLck.lock();
			while (receiptsQueue.isEmpty()){
				// Throws a runtime exception if an attempt of polling a receipt is performed
				// after the middleware is closed.
				if(getState() == CLOSED)
					throw new RuntimeException(new ClosedException("EOMiddleware is closed."));
				receiptsCond.await();
			}
			return receiptsQueue.poll();
		}finally {
			receiptsLck.unlock();
		}
	}

	/**
	 * This method checks if there's a receipt available.
	 * If there is, returns it immediately; otherwise, returns null.
	 * @return a receipt, i.e., an identifier of a message
	 * that has been received by the destination node.
	 */
	public MsgId pollReceipt(){
		try{
			receiptsLck.lock();
			return receiptsQueue.poll();
		}finally {
			receiptsLck.unlock();
		}
	}

	/**
	 * Waits a given amount time for a receipt to be available.
	 * If the time expires without a receipt becoming available, returns a 'null'.
	 * @return a receipt, i.e., an identifier of a message
	 * that has been received by the destination node.
	 * @throws InterruptedException
	 */
	public MsgId pollReceipt(long timeout) throws InterruptedException {
		try{
			long stopTime = System.currentTimeMillis() + timeout;
			receiptsLck.lock();
			while (receiptsQueue.isEmpty() && timeout > 0){
				// Throws a runtime exception if an attempt of polling a receipt is performed
				// after the middleware is closed.
				if(getState() == CLOSED)
					throw new RuntimeException(new ClosedException("EOMiddleware is closed."));
				receiptsCond.await(timeout, TimeUnit.MILLISECONDS);
				timeout = stopTime - System.currentTimeMillis();
			}
			return receiptsQueue.poll();
		}finally {
			receiptsLck.unlock();
		}
	}

	/**
	 * This method checks if the specified receipt is present in the receipts queue.
	 * If it is, removes the receipt from the queue and returns 'true'; otherwise, returns 'false'.
	 * @param receipt the receipt of interest
	 * @return 'true' if the specified receipt was present in the queue; 'false' otherwise.
	 */
	public boolean pollSpecificReceipt(MsgId receipt){
		try{
			receiptsLck.lock();
			return receiptsQueue.remove(receipt);
		}finally {
			receiptsLck.unlock();
		}
	}

	/* ***** Algorithm Thread ***** */

	class AlgoThread extends Thread {
		private long ck = 0;
		private String j; // remote peer node identifier
		// private Map<String, ReceiveRecord> rr = new HashMap<>(); // TODO - after debugging uncomment here and remove the concurrent version present in the middleware instance
		private PriorityQueue<Event> pq = new PriorityQueue<Event>(100000, new TimeComparator());
		private long timeout, currentTime, closeTimeout = 1000;
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
								SendRecord s = new SendRecord(ck, 0, eom);
								s.sem = new Semaphore(P - 1);
								sr.put(j, s);
								requestSlots(j);
							} else {
								if (c.envelopes.size() != 0) {
									long e = c.envelopes.dequeue();
									if (c.envelopes.size() == (N - 1))
										requestSlots(j);
									TokenMsg tm = new TokenMsg(id, j, e, c.rck, msg);
									TokenRecord tr = new TokenRecord(j, e, c.rck, eom, currentTime);
									c.tok.put(e, tr);
									pq.add(new TokenEvent(j, tr, msgTimeout(currentTime, c.RTT, tokenMultiplier)));
									netSend(j, tm);
								} else
									c.msg.add(eom);
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
									ClientMsg eom = c.msg.poll();
									TokenMsg tm = new TokenMsg(id, j, e, c.rck, eom.msg);
									TokenRecord tr = new TokenRecord(j, e, c.rck, eom, currentTime);
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
							//System.out.println("Token Received: "+ rm + "| ReceiveRecord: (" + c + ")");
							if ((c != null) && (r == c.rck)) {
								if (c.slt.contains(s)) {
									try {
										deliveryLck.lock();
										if (deliveryQueue.offer(new DQMsg(j, msg))) { // deliver(msg)
											//System.out.println("Message added to delivery queue.");
											c.slt.remove(s);
											sendAck(j, c, s, r, msgTimeout(currentTime, receiverRTT, acksMultiplier));
											deliveryCond.signal(); // signal a client thread that a message is available to be received
										}
									}finally {
										deliveryLck.unlock();
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
										// if the id of the message is not null, emit a receipt
										if(tr.m.id != null)
											emitReceipt(tr.m.id);
									}
								}
							}
						} else if (m.msg instanceof CloseMsg){
							// Adds a close event that should execute immediately
							pq.add(new CloseEvent(currentTime));
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
									//System.out.println("c.rck:" + c.rck + " | tr.r" + tr.r + " | c.rck == tr.r : " + (c.rck == tr.r));
									if ((c.rck == tr.r) && (c.tok.containsKey(tr.s))) {
										//if(retransmit % 20 == 0)
										//	System.out.println("Re-transmitting: " + retransmit);
										retransmit++;
										pq.add(new TokenEvent(j, tr,
												msgTimeout(currentTime, c.RTT, tokenMultiplier * 3)));
										netSend(j, new TokenMsg(id, j, tr.s, tr.r, tr.m.msg));
									}
								} //else System.out.println("c == null");
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
						} else if (eve instanceof CloseEvent){
							// if there are no records and there are no messages to process
							// than the middleware can close
							if (rr.isEmpty() && sr.isEmpty() && algoQueue.isEmpty()) {
								// remove all subscriptions from the notifier
								if(EOMiddleware.this.assocNotifier != null)
									for (String nodeId : EOMiddleware.this.assocMap.getIdentifiers())
										EOMiddleware.this.assocNotifier.unsubscribeFromNode(EOMiddleware.this,nodeId);

								try {
									deliveryLck.lock();
									receiptsLck.lock();
									stateLck.lock();
									// sets the closed state
									state = CLOSED;
									// Closes the socket. Throws SocketException for
									// the ReaderThread to stop waiting for a datagram.
									sk.close();
									// Signals all possible client threads
									// waiting for a client message or a receipt.
									deliveryCond.signalAll();
									receiptsCond.signalAll();
								}finally {
									stateLck.unlock();
									receiptsLck.unlock();
									deliveryLck.unlock();
								}

								// interrupts the reader thread and
								// waits for it before returning
								readerThread.interrupt();
								readerThread.join();

								// close the association source
								if(assocSrc != null)
									assocSrc.close();

								// persists state
								persistState();
								return;
							}else pq.add(new CloseEvent(currentTime + closeTimeout));
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
				// There are no messages and the number of envelopes equals N,
				// which is the base number of slots that should be requested
				else if (c.tok.size() == 0 && c.msg.size() == 0) {
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
				while (EOMiddleware.this.getState() != CLOSED) {
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
						if(!taddr.equals(getTransportAddress(srcId)))
							assocMap.put(srcId, taddr);

						if (msgType == REQSLOT) {
							ReqSlotsMsg rsm = new ReqSlotsMsg(srcId, destId, b.getLong(), b.getLong(), b.getLong(), b.getDouble());
							m = rsm;
							// System.out.println("Received REQSLOTS (s=" + rsm.s + ", n=" + rsm.n + ", l=" + rsm.l + ", rtt=" + rsm.RTT + ") from " + srcId);
						} else if (msgType == SLOT) {
							SlotsMsg sm = new SlotsMsg(srcId, destId, b.getLong(), b.getLong(), b.getLong());
							m = sm;
							// System.out.println("Received SLOTS (s=" + sm.s + ", r=" + sm.r + ", n=" + sm.n + ") from " + srcId);
						} else if (msgType == TOKEN) {
							long s = b.getLong();
							long r = b.getLong();
							byte[] payload = new byte[b.remaining()];
							b.get(payload);
							TokenMsg tm = new TokenMsg(srcId, destId, s, r, payload);
							m = tm;
							// System.out.println("Received TOKEN (s=" + tm.s + ", r=" + tm.r + ", payload=" + StandardCharsets.UTF_8.decode(ByteBuffer.wrap(tm.payload)) + ") from " + srcId);
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
							// System.out.println(print);
						}
						AQMsg aqm = new AQMsg(srcId, m);
						algoQueue.put(aqm);
					}
				}
			} catch (SocketException e) {
				if(EOMiddleware.this.isClosed()) return;
				else e.printStackTrace();
			} catch (Exception e){
				e.printStackTrace();
			}

			System.out.println("ReaderThread: Acabei!");
			System.out.flush();
		}
	}
}

class TimeComparator implements Comparator<Event> {
	public int compare(Event e1, Event e2) {
		return (int) (e1.time - e2.time);
	}
}
