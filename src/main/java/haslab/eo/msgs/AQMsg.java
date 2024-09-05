package haslab.eo.msgs;

public class AQMsg {
	public final String nodeId;
	public final Msg msg;

	public AQMsg(String nodeId, Msg msg) {
		this.nodeId = nodeId;
		this.msg = msg;
	}
}