package haslab.eo.msgs;



public class ReqSlotsMsg extends NetMsg {
	public final long s, n, l;
	public double RTT;

	public ReqSlotsMsg(String nodeId, long s, long n, long l, double RTT) {
		super(nodeId);
		this.s = s;
		this.n = n;
		this.l = l;
		this.RTT = RTT;
	}
}