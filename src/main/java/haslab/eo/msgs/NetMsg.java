package haslab.eo.msgs;

import java.io.Serializable;



public class NetMsg extends Msg implements Serializable {
	String srcId;
	String destId;
	public NetMsg(String srcId, String destId) {
		this.srcId = srcId;
	}
}
