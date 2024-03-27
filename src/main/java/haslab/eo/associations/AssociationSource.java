package haslab.eo.associations;

import haslab.eo.TransportAddress;

/**
 * Interface that any source of associations must implement.
 */
public interface AssociationSource {
    /**
     * Gets the transport address associated with the given node identifier.
     * @param nodeId node identifier
     * @return transport address associated with the given node identifier,
     * or 'null' if no association is found.
     */
    TransportAddress getTransportAddress(String nodeId);

    /**
     * Gets the node identifier associated with the given transport address.
     * @param taddr transport address
     * @return node identifier associated with the given transport address,
     * or 'null' if no association is found.
     */
    String getIdentifier(TransportAddress taddr);

    /**
     * Gets the association notifier. The associations' source may not support the mechanism of notifying events related to nodes.
     * @return association notifier, or 'null' if notifying events is not supported.
     */
    AssociationNotifier getAssociationNotifier();
}