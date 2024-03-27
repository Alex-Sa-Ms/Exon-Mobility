package haslab.eo.associations.events;

import haslab.eo.TransportAddress;

public class OnlineEvent extends AssociationEvent{
    public final TransportAddress taddr;
    public OnlineEvent(String nodeId, TransportAddress taddr) {
        super(nodeId);
        this.taddr = taddr;
    }
}
