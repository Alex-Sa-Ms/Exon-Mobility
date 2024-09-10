package haslab.eo.associations;

import haslab.eo.TransportAddress;
import haslab.eo.associations.events.AssociationEvent;
import haslab.eo.associations.events.NewAssociationEvent;
import haslab.eo.associations.events.UpdatedAssociationEvent;

import java.io.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Static associations' source.
 * This source extracts associations from a CSV file.
 * Expected line format: <node_id><delimiter><ip_address><delimiter><port_number>
 *     Example: nodeA;192.168.1.88;12345
 */
public class CsvAutoRefreshableDiscoveryService implements DiscoveryService, DiscoveryNotifier {

    private IdentifierToAddressBiMap assocMap = new IdentifierToAddressBiMap();
    private final ReadWriteLock assocLck = new ReentrantReadWriteLock();
    private String filepath;
    private String delimiter;
    private Long fileLastModified;
    private Thread refresher;

    /* Notifier attributes */
    private Map<String, Set<DiscoverySubscriber>> subscriptions = new ConcurrentHashMap<>(); // node specific subscriptions
    private Map<DiscoverySubscriber, DiscoverySubscriber> globalSubscriptions = new ConcurrentHashMap<>(); // global subscriptions
    private Queue<AssociationEvent> assocEvents = new ArrayDeque<>();

    private CsvAutoRefreshableDiscoveryService() {}

    public static CsvAutoRefreshableDiscoveryService create(String filepath, String delimiter) throws IOException {
        CsvAutoRefreshableDiscoveryService src = new CsvAutoRefreshableDiscoveryService();
        src.assocMap = readMappingsFromFile(filepath, delimiter);
        src.filepath = filepath;
        src.delimiter = delimiter;
        src.fileLastModified = new File(filepath).lastModified();
        src.refresher = new Thread(() -> {
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    src.refresh();
                    Thread.sleep(2500);
                }
            }catch (InterruptedException ignored){}
        });
        src.refresher.start();
        return src;
    }

    public void close(){
        try {
            this.refresher.interrupt();
            this.refresher.join();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

     /*
    private static IdentifierToAddressBiMap readMappingsFromFile(String filepath, String delimiter) throws IOException {
        if(filepath == null)
            throw new RuntimeException("File path cannot be null");
        if(delimiter == null)
            throw new RuntimeException("Delimiter cannot be null");

        BufferedReader reader = new BufferedReader(new FileReader(filepath));
        IdentifierToAddressBiMap itoaMap = new IdentifierToAddressBiMap();

        String line;
        String[] tokens;
        int port;
        TransportAddress taddr;
        while((line = reader.readLine()) != null){
            tokens = line.split(delimiter);
            if(tokens.length >= 3){
                // nodeId = tokens[0]
                // ipAddress = tokens[1]
                // port = tokens[2]
                try {
                    port = Integer.parseInt(tokens[2]);
                    taddr = new TransportAddress(tokens[1], port);
                    itoaMap.put(tokens[0], taddr);
                } catch (Exception ignored){}
            }
        }

        reader.close();
        return itoaMap;
    }
     */

    private static IdentifierToAddressBiMap readMappingsFromFile(String filepath, String delimiter) throws IOException {
        if (filepath == null)
            throw new RuntimeException("File path cannot be null");
        if (delimiter == null)
            throw new RuntimeException("Delimiter cannot be null");

        IdentifierToAddressBiMap itoaMap = new IdentifierToAddressBiMap();


        File file = new File(filepath);
        FileInputStream inputStream = new FileInputStream(file);
        FileChannel channel = inputStream.getChannel();
        FileLock lock = channel.lock(0, 0, true); // Lock the file
        Scanner scanner = new Scanner(inputStream);

        String line;
        String[] tokens;
        int port;
        TransportAddress taddr;
        while (scanner.hasNextLine()) {
            line = scanner.nextLine();
            tokens = line.split(delimiter);
            if (tokens.length >= 3) {
                // nodeId = tokens[0]
                // ipAddress = tokens[1]
                // port = tokens[2]
                try {
                    port = Integer.parseInt(tokens[2]);
                    taddr = new TransportAddress(tokens[1], port);
                    itoaMap.put(tokens[0], taddr);
                } catch (Exception ignored) {
                }
            }
        }

        lock.release();
        scanner.close();
        channel.close();
        inputStream.close();

        return itoaMap;
    }

    /**
     * Reads a file, and updates the associations
     * using the associations present on the file.
     */
    private void refresh(String filepath, String delimiter){


        // reads the associations present on the file
        IdentifierToAddressBiMap fileMap;
        try {
            // only refreshes if the filepath differs from the
            // previously read, and if the file has been modified
            // since the last read operation
            if(Objects.equals(filepath, this.filepath) &&
                    this.fileLastModified == new File(filepath).lastModified())
                return;

            fileMap = readMappingsFromFile(filepath, delimiter);
        } catch (IOException ignored) {
            // if an exception occurs, the map stays intact.
            return;
        }

        try{
            this.assocLck.writeLock().lock();

            IdentifierToAddressBiMap newMap = this.assocMap.clone();
            for(Map.Entry<String,TransportAddress> e : fileMap.entrySet()) {
                // gets current transport address associated with the node id
                TransportAddress currAddr =
                        this.assocMap.getAddress(e.getKey());

                // if the current address is null then the association is new
                if(currAddr == null){
                    this.assocEvents.add(new NewAssociationEvent(e.getKey(), e.getValue()));
                    newMap.put(e.getKey(), e.getValue());
                }
                // if the current address differs from the address read from the file
                else if (!currAddr.equals(e.getValue())) {
                    this.assocEvents.add(new UpdatedAssociationEvent(e.getKey(), e.getValue()));
                    newMap.put(e.getKey(), e.getValue());
                }
            }

            this.assocMap = newMap;
        }finally {
          this.assocLck.writeLock().unlock();
        }

        // notifies subscribers
        notifySubscribers();
    }

    /**
     * Reads the last read file again, and updates the entries
     * using the entries present on the file.
     */
    private void refresh(){
        if(this.filepath != null && this.delimiter != null)
            refresh(this.filepath, this.delimiter);
    }

    @Override
    public TransportAddress getTransportAddress(String nodeId) {
        return this.assocMap.getAddress(nodeId);
    }

    @Override
    public String getIdentifier(TransportAddress taddr) {
        return this.assocMap.getIdentifier(taddr);
    }

    @Override
    public DiscoveryNotifier getAssociationNotifier() {
        return this;
    }


    /* ***** Association Notifier Methods ***** */

    @Override
    public boolean subscribeToNode(DiscoverySubscriber sub, String nodeId) {
        // subscriber instance cannot be null
        if(sub == null)
            return false;

        // Adds subscriber to the set of the specified node.
        // If the set does not exist, creates it.
        Set<DiscoverySubscriber> subsIds = subscriptions.get(nodeId);
        if(subsIds == null) {
            subsIds = new HashSet<>();
            subscriptions.put(nodeId, subsIds);
        }
        subsIds.add(sub);

        return true;
    }

    @Override
    public void unsubscribeFromNode(DiscoverySubscriber sub, String nodeId) {
        Set<DiscoverySubscriber> subsIds = subscriptions.get(nodeId);
        if(subsIds != null)
            subsIds.remove(sub);
    }

    @Override
    public void subscribeToAll(DiscoverySubscriber sub) {
        // subscriber instance cannot be null
        if(sub == null)
            return;
        globalSubscriptions.put(sub, sub);
    }

    @Override
    public void unsubscribeFromAll(DiscoverySubscriber sub) {
        globalSubscriptions.remove(sub);
    }

    /**
     * Shouldn't be called from outside the class.
     */
    @Override
    public void notifySubscribers() {
        AssociationEvent ev;

        // while there are events to notify
        while(!assocEvents.isEmpty()) {
            ev = assocEvents.poll();
            if(ev != null) {
                // gets node-specific subscriptions and notifies the subscribers
                Set<DiscoverySubscriber> subsIds = subscriptions.get(ev.nodeId);
                if(subsIds != null)
                    for (DiscoverySubscriber sub : subsIds)
                        sub.notify(ev);

                // gets global subscriptions and notifies the subscribers
                for (DiscoverySubscriber sub : this.globalSubscriptions.keySet())
                    sub.notify(ev);
            }
        }
    }
}
