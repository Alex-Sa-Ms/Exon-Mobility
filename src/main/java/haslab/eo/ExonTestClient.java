package haslab.eo;

import haslab.eo.EOMiddleware;
import haslab.eo.TransportAddress;
import haslab.eo.associations.AssociationSource;
import haslab.eo.associations.CsvAutoRefreshableAssociationSource;
import haslab.eo.msgs.ClientMsg;


import java.io.IOException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class ExonTestClient {
    public static void main(String[] args) throws IOException, InterruptedException {
        Scanner scanner = new Scanner(System.in);
        String id;
        int port;
        if (args.length >= 2) {
            id = args[0];
            port = Integer.parseInt(args[1]);
        } else {
            System.out.print("Write local id: ");
            id = scanner.nextLine();
            System.out.print("Write local port: ");
            port = scanner.nextInt();
            scanner.nextLine(); // ignore new line that comes from submitting the port number
        }

        EOMiddleware eoMiddleware = EOMiddleware.start(id, null, port, null);
        try {
            String assocSrcFP = "staticTopology.csv";
            if(args.length >= 3)
                assocSrcFP = args[2];
            AssociationSource assocSrc = CsvAutoRefreshableAssociationSource.create(assocSrcFP, ";");
            eoMiddleware.setAssociationSource(assocSrc);
        }catch (Exception e) {
            System.out.println("Middleware initialized without an association source.");
        }

        new Thread(() -> {
            while (true) {
                System.out.print("Command? ");
                String line = scanner.nextLine();
                if (line.equals("debug"))
                    eoMiddleware.debugPrints();
                else if (line.equals("association")) {
                    System.out.print("Options:\n\t-> manual\n\t-> source\nAssociation command? ");
                    line = scanner.nextLine();
                    if(line.equals("manual")){
                        System.out.println("Node Id:");
                        String nodeId = scanner.nextLine();
                        System.out.println("Address:");
                        String newAddr = scanner.nextLine();
                        System.out.println("Port:");
                        int newPort = Integer.parseInt(scanner.nextLine());
                        try {
                            eoMiddleware.registerAssociation(nodeId, new TransportAddress(newAddr, newPort));
                        } catch (UnknownHostException e) {
                            throw new RuntimeException(e);
                        }
                    } else if (line.equals("source")) {
                        System.out.println("Filepath:");
                        String filepath = scanner.nextLine();
                        try {
                            CsvAutoRefreshableAssociationSource src = CsvAutoRefreshableAssociationSource.create(filepath, ";");
                            eoMiddleware.setAssociationSource(src);
                        } catch (IOException ignored) {}
                    }
                } else if (line.equals("send")) {
                    System.out.print("Destination Id: ");
                    String nodeId = scanner.nextLine();
                    System.out.print("Message: ");
                    String msg = scanner.nextLine();
                    try {
                        eoMiddleware.send(nodeId, msg.getBytes(StandardCharsets.UTF_8));
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                } else if(line.equals("receive")){
                    ClientMsg cMsg = null;
                    try {
                        cMsg = eoMiddleware.receive(0L);
                        if(cMsg != null) {
                            String msg = String.valueOf(StandardCharsets.UTF_8.decode(ByteBuffer.wrap(cMsg.msg)));
                            System.out.println("Received '" + msg + "' from '" + cMsg.nodeId);
                        }
                        else System.out.println("There are no msgs to be received.");
                    } catch (InterruptedException ie) {
                        System.out.println("There are no msgs to be received.");
                    }
                }
            }
        }).start();

        while (true) {
            ClientMsg cMsg = eoMiddleware.receive(1000L);
            if(cMsg != null) {
                String msg = String.valueOf(StandardCharsets.UTF_8.decode(ByteBuffer.wrap(cMsg.msg)));
                System.out.println("Received '" + msg + "' from '" + cMsg.nodeId);
            }
        }
    }
}
