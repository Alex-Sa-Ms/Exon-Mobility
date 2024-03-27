package haslab.eo.associations.events;

public class DeletedAssociationEvent extends AssociationEvent{
    public DeletedAssociationEvent(String nodeId) {
        super(nodeId);
    }
}
