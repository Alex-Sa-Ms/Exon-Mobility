package haslab.eo.msgs;

import java.util.ArrayList;



public class AcksMsg extends NetMsg {
	public final long r;
	public final ArrayList<Long> acks; 
	
	public AcksMsg(String nodeId, ArrayList<Long> acks, long r) {
		super(nodeId);
		this.acks = acks;
		this.r = r;
	}
}