package haslab.eo.events;



public class AcksEvent extends Event {
	public long oldestAck;

	public AcksEvent(String nodeId, long oldestAck, long time) {
		super(nodeId, time);
		this.oldestAck = oldestAck;
	}
}
