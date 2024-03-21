package haslab.eo;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;


public class IdentifierToAddressBiMapWithLock {

    private Map<String, TransportAddress> idToAddrMap;
    private Map<TransportAddress, String> addrToIdMap;
    private ReadWriteLock lck;

    public IdentifierToAddressBiMapWithLock() {
        this.idToAddrMap = new HashMap<>();
        this.addrToIdMap = new HashMap<>();
        this.lck = new ReentrantReadWriteLock();
    }

    public String getIdentifier(TransportAddress taddr) {
        try{
            this.lck.readLock().lock();
            return addrToIdMap.get(taddr);
        }finally {
            this.lck.readLock().unlock();
        }
    }

    public TransportAddress getAddress(String nodeId) {
        try{
            this.lck.readLock().lock();
            return idToAddrMap.get(nodeId);
        }finally {
            this.lck.readLock().unlock();
        }
    }

    public void put(String newId, TransportAddress newAddr) {
        try{
            this.lck.writeLock().lock();

            // remove previous associations if existent
            String oldId = addrToIdMap.get(newAddr);
            if(oldId != null)
                idToAddrMap.remove(oldId);

            TransportAddress oldAddr = idToAddrMap.get(newId);
            if(oldAddr != null)
                addrToIdMap.remove(oldAddr);

            // creates the association
            idToAddrMap.put(newId, newAddr);
            addrToIdMap.put(newAddr, newId);
        }finally {
            this.lck.writeLock().unlock();
        }
    }

    public void removeId(String nodeId){
        try{
            this.lck.writeLock().lock();
            TransportAddress taddr = idToAddrMap.get(nodeId);
            idToAddrMap.remove(nodeId);
            addrToIdMap.remove(taddr);
        }finally {
            this.lck.writeLock().unlock();
        }
    }

    public void removeAddress(TransportAddress taddr){
        try{
            this.lck.writeLock().lock();
            String nodeId = addrToIdMap.get(taddr);
            addrToIdMap.remove(taddr);
            idToAddrMap.remove(nodeId);
        }finally {
            this.lck.writeLock().unlock();
        }
    }

    /*
    public void put(String nodeId, TransportAddress taddr) {
        idToAddrMap.put(nodeId, taddr);
        addrToIdMap.put(taddr, nodeId);
    }

    public void putNodeId(String nodeId, TransportAddress taddr){
        if(nodeId == null)
            throw new RuntimeException(new NullPointerException("Cannot register a null node identifier."));
        if(taddr == null)
            throw new RuntimeException(new NullPointerException("Cannot associate a node identifier to a null transport address."));

        TransportAddress oldAddr = idToAddrMap.get(nodeId);
        // if there is an address associated with identifier,
        // then the transport address should be replaced.
        // Else, it may be simply inserted.
        if(oldAddr != null)
            replaceAddress(taddr, nodeId);
        else
            put(nodeId, taddr);
    }

    public void putAddress(String nodeId, TransportAddress taddr){
        if(nodeId == null)
            throw new RuntimeException(new NullPointerException("Cannot associate an address to a null node identifier."));
        if(taddr == null)
            throw new RuntimeException(new NullPointerException("Cannot register a null transport address."));

        String oldId = addrToIdMap.get(taddr);
        // if there is an identifier associated with address,
        // then the identifier should be replaced.
        // Else, it may be simply inserted.
        if(oldId != null)
            replaceIdentifier(taddr, nodeId);
        else
            put(nodeId, taddr);
    }

    public void replaceIdentifier(String oldId, String newId){
        if(newId == null) return;
        TransportAddress taddr = idToAddrMap.get(oldId);
        if(taddr != null){
            idToAddrMap.remove(oldId);
            idToAddrMap.put(newId, taddr);
            addrToIdMap.put(taddr, newId);
        }
    }

    public void replaceIdentifier(TransportAddress taddr, String newId){
        if(newId == null) return;
        String oldId = addrToIdMap.get(taddr);
        if(oldId != null){
            idToAddrMap.remove(oldId);
            idToAddrMap.put(newId, taddr);
            addrToIdMap.put(taddr, newId);
        }
    }

    public void replaceAddress(TransportAddress oldAddr, TransportAddress newAddr){
        if(newAddr == null) return;
        String id = addrToIdMap.get(oldAddr);
        if(id != null){
            addrToIdMap.remove(oldAddr);
            addrToIdMap.put(newAddr, id);
            idToAddrMap.put(id, newAddr);
        }
    }

    public void replaceAddress(TransportAddress newAddr, String id){
        if(newAddr == null) return;
        TransportAddress oldAddr = idToAddrMap.get(id);
        if(oldAddr != null){
            addrToIdMap.remove(oldAddr);
            addrToIdMap.put(newAddr, id);
            idToAddrMap.put(id, newAddr);
        }
    }

     */
}
