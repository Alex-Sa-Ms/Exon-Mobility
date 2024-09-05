package haslab.eo.msgs;


public class ReqSlotsMsg extends NetMsg {
	public final long s, n, l;
	public double RTT;

	public ReqSlotsMsg(String srcId, String destId, long s, long n, long l, double RTT) {
		super(srcId, destId);
		this.s = s;
		this.n = n;
		this.l = l;
		this.RTT = RTT;
	}
}