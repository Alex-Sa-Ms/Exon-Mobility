package haslab.eo.associations;

import haslab.eo.associations.events.AssociationEvent;

public interface AssociationSubscriber {
    void notify(AssociationEvent ev);
}
