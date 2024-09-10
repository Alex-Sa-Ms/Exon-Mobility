package haslab.eo.associations;

import haslab.eo.TransportAddress;

public interface DiscoveryManager {
    String getNodeIdentifier(TransportAddress taddr);
    TransportAddress getNodeTransportAddress(String nodeId);
    void registerNode(String nodeId, TransportAddress taddr);
    void unregisterNode(String nodeId);
    void setDiscoveryService(DiscoveryService source);
}
