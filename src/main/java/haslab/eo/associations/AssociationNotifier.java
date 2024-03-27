package haslab.eo.associations;

public interface AssociationNotifier {
    /* ***** Events ***** */

    // TODO - when subscribing to a specific node, an "ONLINE" or "OFFLINE" notification could be sent immediately.
    /*
        The associations' source may not support the mechanism of notifying events related to nodes.
        However, if it does, the following are the events that must be implemented:
            -> "NEW_ASSOCIATION": A new association was registered. Must inform the node identifier
                                  and the transport address that form the association.
            -> "UPDATED_ASSOCIATION": The association of a node was updated. Must inform the node
                                      identifier and the new transport address.
            -> "DELETED_ASSOCIATION": The association of a node was deleted. Must inform the identifier
                                       of the node that had its association deleted. Since the association
                                       was deleted, the subscriptions related to the node will be canceled.
            -> "ONLINE": The node has become online. May carry a transport address if the node changed location.
                         (if the presence mechanism is supported)
            -> "OFFLINE": The node has become offline. (if the present mechanism is supported)
     */

    /**
     * Allows subscribing to the events related to a specific node.
     * The association source must notify all events related to
     * the node identifier.
     * @param sub subscriber instance
     * @param nodeId identifier of the interested node
     * @throws NotSupportedException if the source does not support the subscription mechanism.
     * @return 'true' if a subscription was created successfully.
     *         'false' otherwise (if a node is not registered it cannot be subscribed).
     */
    boolean subscribeToNode(AssociationSubscriber sub, String nodeId) throws NotSupportedException;

    /**
     * Allows cancelling a previously made node subscription.
     * @param sub subscriber instance
     * @param nodeId node identifier
     */
    void unsubscribeFromNode(AssociationSubscriber sub, String nodeId) throws NotSupportedException;

    /**
     * Allows subscribing to all events.
     * The association source must notify all events regardless of the node identifier.
     * @param sub subscriber instance
     * @throws NotSupportedException if the source does not support the subscription mechanism.
     */
    void subscribeToAll(AssociationSubscriber sub) throws NotSupportedException;

    /**
     * Allows cancelling a previously made global subscription.
     * @param sub subscriber instance
     */
    void unsubscribeFromAll(AssociationSubscriber sub) throws NotSupportedException;
}
