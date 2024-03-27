package haslab.eo.associations;

import haslab.eo.TransportAddress;

import java.io.*;
import java.net.InetAddress;

/**
 * Static associations' source.
 * This source extracts associations from a CSV file.
 * Expected line format: <node_id><delimiter><ip_address><delimiter><port_number>
 *     Example: nodeA;192.168.1.88;12345
 */
public class CsvAssociationSource implements AssociationSource{

    private final IdentifierToAddressBiMap map = new IdentifierToAddressBiMap();
    private final String filepath;
    private final String delimiter;

    private CsvAssociationSource(String filepath, String delimiter) {
        this.filepath = filepath;
        this.delimiter = delimiter;
    }

    public static CsvAssociationSource create(String filepath, String delimiter) throws IOException {
        if(filepath == null)
            throw new RuntimeException("File path cannot be null");
        if(delimiter == null)
            throw new RuntimeException("Delimiter cannot be null");

        CsvAssociationSource src = new CsvAssociationSource(filepath, delimiter);
        BufferedReader reader = new BufferedReader(new FileReader(filepath));

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
                    src.map.put(tokens[0], taddr);
                } catch (Exception ignored){}
            }
        }

        reader.close();
        return src;
    }

    public String getFilepath() {
        return filepath;
    }

    @Override
    public TransportAddress getTransportAddress(String nodeId) {
        return this.map.getAddress(nodeId);
    }

    @Override
    public String getIdentifier(TransportAddress taddr) {
        return this.map.getIdentifier(taddr);
    }

    @Override
    public AssociationNotifier getAssociationNotifier() {
        return null;
    }
}
