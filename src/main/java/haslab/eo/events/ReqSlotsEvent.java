package haslab.eo.events;



public class ReqSlotsEvent extends Event {

	public long lastReqSlotsSendTime;

	public ReqSlotsEvent(String nodeId, long time, long currentTime) {
		super(nodeId, time);
		lastReqSlotsSendTime = currentTime;
	}
}