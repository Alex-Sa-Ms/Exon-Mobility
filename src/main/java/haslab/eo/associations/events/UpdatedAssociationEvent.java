package haslab.eo.associations.events;

import haslab.eo.TransportAddress;

public class UpdatedAssociationEvent extends AssociationEvent{
    public final TransportAddress taddr;
    public UpdatedAssociationEvent(String nodeId, TransportAddress taddr) {
        super(nodeId);
        this.taddr = taddr;
    }
}
