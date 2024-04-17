package haslab.eo.msgs;

public class ClientMsg extends Msg {
	public final String nodeId;
	public final byte[] msg;
	public final MsgId id;
	
	public ClientMsg(String nodeId, byte[] msg, MsgId id) {
		this.nodeId = nodeId;
		this.msg = msg;
        this.id = id;
    }

	public ClientMsg(String nodeId, byte[] msg) {
		this.nodeId = nodeId;
		this.msg = msg;
		this.id = null;
	}
}