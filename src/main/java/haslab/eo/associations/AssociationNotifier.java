package haslab.eo.associations;

public interface AssociationNotifier {
    /* ***** Events ***** */

    /*
        The associations' source may not support the mechanism of notifying events related to nodes.
        However, if it does, the following are the events that must be implemented:
            -> "NEW_ASSOCIATION": A new association was registered. Must inform the node identifier
                                  and the transport address that form the association.
            -> "UPDATED_ASSOCIATION": The association of a node was updated. Must inform the node
                                      identifier and the new transport address.
            -> "ONLINE": The node has become online. May carry a transport address if the node changed location.
                         (if the presence mechanism is supported)
            -> "OFFLINE": The node has become offline. (if the present mechanism is supported)

         When subscribing to a specific node, if the notifier supports presence verification,
          an "ONLINE" or "OFFLINE" notification should be sent immediately.
     */

    /**
     * Allows subscribing to the events related to a specific node.
     * The association source must notify all events related to
     * the node identifier.
     * @param sub subscriber instance
     * @param nodeId identifier of the interested node
     * @return 'true' if a subscription was created successfully.
     *         'false' otherwise (if a node is not registered it cannot be subscribed).
     */
    boolean subscribeToNode(AssociationSubscriber sub, String nodeId);

    /**
     * Allows cancelling a previously made node subscription.
     * @param sub subscriber instance
     * @param nodeId node identifier
     */
    void unsubscribeFromNode(AssociationSubscriber sub, String nodeId);

    /**
     * Allows subscribing to all events.
     * This should create a global subscription.
     * The association source must notify all events
     * regardless of the node identifier.
     * It's important to notice that a subscriber
     * with a global subscription and a specific subscription
     * will receive a notification twice for the node specified
     * in the specific subscription.
     * @param sub subscriber instance
     */
    void subscribeToAll(AssociationSubscriber sub);

    /**
     * Allows cancelling a previously made global subscription.
     * This does not remove the node-specific subscriptions.
     * @param sub subscriber instance
     */
    void unsubscribeFromAll(AssociationSubscriber sub);

    void notifySubscribers();
}
