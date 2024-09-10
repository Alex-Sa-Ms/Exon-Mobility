package haslab.eo.associations;

import haslab.eo.TransportAddress;

/* TODO - (NOT FOR NOW) Create a simple association source with notifier capabilities
    1. Create simple presence service using UDP.
        -> Initialized using a CSV file.
        -> Should allow nodes to be registered using the globally unique identifier
            and a password. The registration should only be allowed if the identifier
            has not been registered yet. Since duplicates may occur, if the password
            differs from the existing registration, the service should reply with an error.
            The transport address should be updated if it differs between the requests,
            as the node may change ip address before receiving a reply indicating that
            the registration was successful.
        -> Should work using pings. The pings should contain the identifier of the node.
        -> After a timeout (start with a high timeout, then use RTT to calculate?)
            without hearing from the client, the node should be set as "offline",
            and trigger the "offline" event notification.
        -> After receiving a ping from a node (e.g. after a network partition
            recovers or after changing to a new address), if the node presence value is:
                - "offline" then it passes to "online" and triggers the "online" event notification.
                - "online" and a new transport address is detected, then a "change address" event notification
                    should be triggered.
        -> Pings received from unknown addresses with an registered identifier, should trigger the
            request of authentication to confirm the new address. This handshake avoids the ping pong
            effect when a node changes to another network but its ping, sent at the new network, reaches
            the presence service before a ping sent on the previous network.
 */


/**
 * Interface that any source of associations must implement.
 */
public interface DiscoveryService {
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
    DiscoveryNotifier getAssociationNotifier();

    /**
     * To shut down the association source if required.
     */
    default void close(){}
}