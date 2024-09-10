package haslab.eo;

import java.net.DatagramSocket;
import java.util.LinkedList;
import java.util.List;

public class SocketsCleaner {
    private static final List<DatagramSocket> datagramSockets = new LinkedList<>();

    /**
     * Closes all datagram sockets registered and not closed,
     * and then clears the list.
     */
    public static void clean(){
        for (DatagramSocket socket : datagramSockets){
            try { socket.close(); }
            catch (Exception ignored) {}
        }
        datagramSockets.clear();
    }

    /**
     * Adds datagram socket from the Exon instance.
     * @param eom Exon instance
     * @return the provided Exon instance
     */
    public static EOMiddleware add(EOMiddleware eom){
        if(eom != null) datagramSockets.add(eom.getDatagramSocket());
        return eom;
    }
}
