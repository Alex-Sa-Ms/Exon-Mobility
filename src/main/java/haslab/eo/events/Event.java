package haslab.eo.events;



public abstract class Event {
	public long time;
	public String nodeId;

	public Event(String nodeId, long time) {
		this.nodeId = nodeId;
		this.time = time;
	}
}