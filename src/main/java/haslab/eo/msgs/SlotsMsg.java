package haslab.eo.msgs;


public class SlotsMsg extends NetMsg {
	public final long s, r, n;

	public SlotsMsg(String srcId, String destId, long s, long r, long n) {
		super(srcId, destId);
		this.s = s;
		this.r = r;
		this.n = n;
	}
}