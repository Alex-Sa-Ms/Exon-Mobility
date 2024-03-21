package haslab.eo.msgs;

import java.io.Serializable;



public class NetMsg extends Msg implements Serializable {
	String nodeId;
	public NetMsg(String nodeId) {
		this.nodeId = nodeId;
	}

}
