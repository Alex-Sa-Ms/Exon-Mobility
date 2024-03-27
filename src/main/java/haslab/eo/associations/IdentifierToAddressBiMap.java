package haslab.eo.associations;

import haslab.eo.TransportAddress;

import java.util.HashMap;
import java.util.Map;

/**
 * Should be used alongside locks to ensure consistency.
 */
public class IdentifierToAddressBiMap {

    private Map<String, TransportAddress> idToAddrMap;
    private Map<TransportAddress, String> addrToIdMap;

    public IdentifierToAddressBiMap() {
        this.idToAddrMap = new HashMap<>();
        this.addrToIdMap = new HashMap<>();
    }

    public String getIdentifier(TransportAddress taddr) {
        return addrToIdMap.get(taddr);
    }

    public TransportAddress getAddress(String nodeId) {
        return idToAddrMap.get(nodeId);
    }

    public void put(String newId, TransportAddress newAddr) {
        // remove previous associations if existent
        String oldId = addrToIdMap.get(newAddr);
        if (oldId != null)
            idToAddrMap.remove(oldId);

        TransportAddress oldAddr = idToAddrMap.get(newId);
        if (oldAddr != null)
            addrToIdMap.remove(oldAddr);

        // creates the association
        idToAddrMap.put(newId, newAddr);
        addrToIdMap.put(newAddr, newId);
    }

    public void removeId(String nodeId) {
        TransportAddress taddr = idToAddrMap.get(nodeId);
        idToAddrMap.remove(nodeId);
        addrToIdMap.remove(taddr);
    }

    public void removeAddress(TransportAddress taddr) {
        String nodeId = addrToIdMap.get(taddr);
        addrToIdMap.remove(taddr);
        idToAddrMap.remove(nodeId);
    }
}
