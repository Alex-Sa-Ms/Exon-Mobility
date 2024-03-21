package haslab.eo.events;



public class SlotsEvent extends Event {
	public long lastSlotsSendTime;
	
	public SlotsEvent(String nodeId, long time, long lastSlotsSendTime) {
		super(nodeId, time);
		this.lastSlotsSendTime = lastSlotsSendTime;
	}
}