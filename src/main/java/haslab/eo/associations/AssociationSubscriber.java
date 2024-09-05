package haslab.eo.associations;

import haslab.eo.associations.events.AssociationEvent;

public interface AssociationSubscriber {
    void notify(AssociationEvent ev);

    /**
     * Returns the hash code of the subscriber.
     * @return hash code of the subscriber.
     */
    int hashCode();
}
