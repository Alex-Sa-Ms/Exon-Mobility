package haslab.eo;

public class TokenRecord {
	long s, r, time;
	byte[] m;
	String nodeId;
	boolean acked;

	public TokenRecord(String nodeId, long s, long r, byte[] m, long time) {
		this.nodeId = nodeId;
		this.s = s;
		this.r = r;
		this.m = m;
		this.time = time;
		acked = false;
	}
}