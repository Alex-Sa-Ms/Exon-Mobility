package haslab.eo.associations;

import haslab.eo.TransportAddress;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
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

    public boolean hasIdentifier(String nodeId){
        try {
            this.lck.readLock().lock();
            return idToAddrMap.containsKey(nodeId);
        }finally {
            this.lck.readLock().unlock();
        }
    }

    public boolean hasAddress(TransportAddress taddr){
        try {
            this.lck.readLock().lock();
            return addrToIdMap.containsKey(taddr);
        }finally {
            this.lck.readLock().unlock();
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

    public Set<Map.Entry<String, TransportAddress>> entrySet(){
        try{
            this.lck.readLock().lock();
            return idToAddrMap.entrySet();
        }finally {
            this.lck.readLock().unlock();
        }
    }

    public Set<String> getIdentifiers(){
        try{
            this.lck.readLock().lock();
            return idToAddrMap.keySet();
        }finally {
            this.lck.readLock().unlock();
        }
    }

    public Set<TransportAddress> getAddresses(){
        try{
            this.lck.readLock().lock();
            return addrToIdMap.keySet();
        }finally {
            this.lck.readLock().unlock();
        }
    }

    public IdentifierToAddressBiMap clone(){
        try{
            this.lck.readLock().lock();
            IdentifierToAddressBiMap newMap = new IdentifierToAddressBiMap();
            for(Map.Entry<String,TransportAddress> e : this.idToAddrMap.entrySet())
                newMap.put(e.getKey(), e.getValue());
            return newMap;
        }finally {
            this.lck.readLock().unlock();
        }
    }
}
