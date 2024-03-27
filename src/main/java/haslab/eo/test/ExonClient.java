package haslab.eo.test;

import haslab.eo.EOMiddleware;
import haslab.eo.TransportAddress;
import haslab.eo.msgs.ClientMsg;


import java.io.IOException;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;

public class ExonClient {
    public static void main(String[] args) throws SocketException, UnknownHostException, InterruptedException {
        Scanner scanner = new Scanner(System.in);
        String id;
        int port;
        if (args.length == 2) {
            id = args[0];
            port = Integer.parseInt(args[1]);
        } else {
            System.out.print("Write local id: ");
            id = scanner.nextLine();
            System.out.print("Write local port: ");
            port = scanner.nextInt();
            scanner.nextLine(); // ignore new line that comes from submitting the port number
        }

        EOMiddleware eoMiddleware = EOMiddleware.start(id, null, port);
        new Thread(() -> {
            while (true) {
                System.out.print("Command? ");
                String line = scanner.nextLine();
                if (line.equals("debug"))
                    eoMiddleware.debugPrints();
                else if (line.equals("association")) {
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
                }
            }
        }).start();

        while (true) {
            ClientMsg cMsg = eoMiddleware.receive();
            String msg = String.valueOf(StandardCharsets.UTF_8.decode(ByteBuffer.wrap(cMsg.msg)));
            System.out.println("Received '" + msg + "' from '" + cMsg.nodeId);
        }
    }
}
