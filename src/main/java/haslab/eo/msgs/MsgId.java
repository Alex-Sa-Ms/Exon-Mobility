package haslab.eo.msgs;



public class MsgId {
	public final String nodeId;
	public final long clock;

	public MsgId(String nodeId, long clock) {
		this.nodeId = nodeId;
		this.clock = clock;
	}
}