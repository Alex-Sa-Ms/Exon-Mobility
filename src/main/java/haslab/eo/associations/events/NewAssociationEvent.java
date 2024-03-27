package haslab.eo.associations.events;

import haslab.eo.TransportAddress;

public class NewAssociationEvent extends AssociationEvent{
    public final TransportAddress taddr;

    public NewAssociationEvent(String nodeId, TransportAddress taddr) {
        super(nodeId);
        this.taddr = taddr;
    }
}
