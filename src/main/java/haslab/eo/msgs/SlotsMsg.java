package haslab.eo.msgs;



public class SlotsMsg extends NetMsg {
	public final long s, r, n;

	public SlotsMsg(String nodeId, long s, long r, long n) {
		super(nodeId);
		this.s = s;
		this.r = r;
		this.n = n;
	}
}