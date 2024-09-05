package haslab.eo.msgs;



public class DQMsg {
	public String nodeId;
	public byte[] msg;

	public DQMsg(String nodeId, byte[] msg) {
		this.nodeId = nodeId;
		this.msg = msg;
	}
}