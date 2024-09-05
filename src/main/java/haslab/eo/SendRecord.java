package haslab.eo;

import haslab.eo.msgs.ClientMsg;

import java.util.*;
import java.util.concurrent.*;

class SendRecord {
	Semaphore sem;
	long sck, rck;
	Queue<ClientMsg> msg = new LinkedList<ClientMsg>();
	Interval envelopes;
	final TreeMap<Long, TokenRecord> tok = new TreeMap<Long, TokenRecord>();
	long reqSlotsTime;
	double RTT = 10;

	public SendRecord(long sck, long rck, ClientMsg m) throws Exception {
		this.sck = sck;
		this.rck = rck;
		msg.add(m);
		envelopes = new Interval(0, 0);
	}

	public String toString() {
		return "sck: " + sck + ", rck: " + rck + ", msg: " + new String(msg.peek().msg);
	}
}