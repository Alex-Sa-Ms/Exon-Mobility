package haslab.eo.msgs;



public class ClientMsg extends Msg {
	public final String nodeId;
	public final byte[] msg;
	//final MsgId id;
	
	public ClientMsg(String nodeId, byte[] msg) {
		this.nodeId = nodeId;
		this.msg = msg;
	}
}