package haslab.eo;

import haslab.eo.msgs.ClientMsg;

public class TokenRecord {
	long s, r, time;
	ClientMsg m;
	String nodeId;
	boolean acked;

	public TokenRecord(String nodeId, long s, long r, ClientMsg m, long time) {
		this.nodeId = nodeId;
		this.s = s;
		this.r = r;
		this.m = m;
		this.time = time;
		acked = false;
	}
}