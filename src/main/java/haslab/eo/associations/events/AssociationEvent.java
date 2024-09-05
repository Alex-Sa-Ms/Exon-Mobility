package haslab.eo.associations.events;

public abstract class AssociationEvent {
    public final String nodeId;

    public AssociationEvent(String nodeId) {
        this.nodeId = nodeId;
    }
}
