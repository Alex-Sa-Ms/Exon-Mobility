package haslab.eo.tests;

import haslab.eo.EOMiddleware;
import haslab.eo.TransportAddress;
import haslab.eo.msgs.ClientMsg;
import haslab.eo.msgs.MsgId;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetAddress;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

public class ReceiptsTests {

    @Test
    public void testTakeReceipt() throws IOException, InterruptedException {
        int serverPort = 10001;
        EOMiddleware server = EOMiddleware.start("server", "localhost", serverPort, null),
                     client = EOMiddleware.start("client", "localhost", 10002, null);

        // Issues sending of message. Since the address of the server is not yet known,
        // the message will not reach its destination.
        MsgId msgId = client.send("server", "Hello, World!".getBytes(), true);

        // sets atomic bool for the thread to say if all tests were passed
        AtomicBoolean threadPassed = new AtomicBoolean(false);

        // starts client thread that will wait for the receipt
        Thread t = new Thread(() -> {
            try {
                MsgId receipt = client.takeReceipt();
                assert msgId != null && receipt != null && msgId.equals(receipt);
                threadPassed.set(true);
                System.out.println("msgId: " + msgId + " | receipt: " + receipt);
                System.out.flush();
            } catch (InterruptedException ignored) {}
        });
        t.start();

        // sleeps 1 second then registers the server to allow the message to be sent
        Thread.sleep(1000);
        client.registerAssociation("server", new TransportAddress("localhost", serverPort));

        // wait for thread to finish and check if it passed the tests
        t.join();
        assert threadPassed.get() == true;
    }

    @Test
    public void testPollReceipt() throws IOException, InterruptedException {
        int serverPort = 10003;
        EOMiddleware server = EOMiddleware.start("server", "localhost", serverPort, null),
                     client = EOMiddleware.start("client", "localhost", 10004, null);

        // Issues sending of message. Since the address of the server is not yet known,
        // the message will not reach its destination.
        MsgId msgId = client.send("server", "Hello, World!".getBytes(), true);

        // sleep just to prove that some time was waited for an
        // attempt of sending the message to occur
        Thread.sleep(1000);

        // Server is not yet registered so the message cannot have been received,
        // therefore the return value of the poll operation must be 'null'
        assert client.pollReceipt() == null;

        // sets atomic bool for the thread to say if all tests were passed
        AtomicBoolean threadPassed = new AtomicBoolean(false);

        // starts client thread that will wait for the receipt
        Thread t = new Thread(() -> {
            MsgId receipt = null;
            // active polling until the receipt is available
            while(receipt == null)
                receipt = client.pollReceipt();
            // if a receipt is available it must match the id of the message sent
            assert msgId != null && receipt != null && msgId.equals(receipt);
            // there should be no more receipts available
            assert client.pollReceipt() == null;
            threadPassed.set(true);
            System.out.println("msgId: " + msgId + " | receipt: " + receipt);
            System.out.flush();
        });
        t.start();

        // sleeps 1 second then registers the server to allow the message to be sent
        Thread.sleep(1000);
        client.registerAssociation("server", new TransportAddress("localhost",serverPort));

        // wait for thread to finish and check if it passed the tests
        t.join();
        assert threadPassed.get() == true;
    }

    @Test
    public void testPollReceiptWithTimeout() throws IOException, InterruptedException {
        int serverPort = 10005;
        EOMiddleware server = EOMiddleware.start("server", "localhost", serverPort, null),
                client = EOMiddleware.start("client", "localhost", 10006, null);

        // Issues sending of message. Since the address of the server is not yet known,
        // the message will not reach its destination.
        MsgId msgId = client.send("server", "Hello, World!".getBytes(), true);

        // sleep just to prove that some time was waited for an
        // attempt of sending the message to occur
        Thread.sleep(1000);

        long startTime = System.currentTimeMillis();
        // Server is not yet registered so the message cannot have been received,
        // therefore the return value of the poll operation must be 'null'.
        // Also, at least 1000 ms must have elapsed before the assertion test occurs
        assert client.pollReceipt(1000) == null
                && System.currentTimeMillis() - startTime >= 1000;

        // sets atomic bool for the thread to say if all tests were passed
        AtomicBoolean threadPassed = new AtomicBoolean(false);

        // starts client thread that will wait for the receipt
        Thread t = new Thread(() -> {
            MsgId receipt = null;
            // active polling until the receipt is available
            while(receipt == null) {
                try {
                    receipt = client.pollReceipt(50);
                } catch (InterruptedException ignored) {}
            }
            // if a receipt is available it must match the id of the message sent
            assert msgId != null && receipt != null && msgId.equals(receipt);
            // there should be no more receipts available
            assert client.pollReceipt() == null;
            threadPassed.set(true);
            System.out.println("msgId: " + msgId + " | receipt: " + receipt);
            System.out.flush();
        });
        t.start();

        // sleeps 1 second then registers the server to allow the message to be sent
        Thread.sleep(1000);
        client.registerAssociation("server", new TransportAddress("localhost",serverPort));

        // wait for thread to finish and check if it passed the tests
        t.join();
        assert threadPassed.get() == true;
    }

    @Test
    public void testPollSpecificReceipt() throws IOException, InterruptedException {
        int serverPort = 10007;
        EOMiddleware server = EOMiddleware.start("server", "localhost", serverPort, null),
                client = EOMiddleware.start("client", "localhost", 10008, null);

        // Issues sending of message. Since the address of the server is not yet known,
        // the message will not reach its destination.
        MsgId msgId = client.send("server", "Hello, World!".getBytes(), true);

        // sleep just to prove that some time was waited for an
        // attempt of sending the message to occur
        Thread.sleep(1000);

        // Server is not yet registered so the message cannot have been received,
        // therefore the return value of polling the specific receipt must be 'false'
        assert client.pollSpecificReceipt(msgId) == false;

        // sets atomic bool for the thread to say if all tests were passed
        AtomicBoolean threadPassed = new AtomicBoolean(false);

        // starts client thread that will wait for the receipt
        Thread t = new Thread(() -> {
            boolean receiptEmitted = false;
            // active polling until the receipt is available
            while(!receiptEmitted)
                receiptEmitted = client.pollSpecificReceipt(msgId);
            // if a receipt is available it must match the id of the message sent
            assert msgId != null && receiptEmitted == true;
            // there should be no more receipts available
            assert client.pollReceipt() == null;
            threadPassed.set(true);
        });
        t.start();

        // sleeps 1 second then registers the server to allow the message to be sent
        Thread.sleep(1000);
        client.registerAssociation("server", new TransportAddress("localhost",serverPort));

        // wait for thread to finish and check if it passed the tests
        t.join();
        assert threadPassed.get() == true;
    }

    @Test
    public void testIfAllReceiptsAreEmitted() throws IOException, InterruptedException {
        int nIters = 1000; // number of messages to be sent

        int serverPort = 10009;
        EOMiddleware server = EOMiddleware.start("server", "localhost", serverPort, null),
                client = EOMiddleware.start("client", "localhost", 10010, null);
        client.registerAssociation("server", new TransportAddress("localhost",serverPort));

        // Set of message ids that should get a receipt
        Set<MsgId> clientMsgIds = new HashSet<>(),
                   serverMsgIds = new HashSet<>();

        // creates a random generator,
        // to randomly set which messages should
        // get a receipt and which should not.
        Random random = new Random(2024);
        boolean receipt;
        MsgId msgId;

        // sends a nIters amount of messages to server, and
        // collects randomly the ids of the messages that should
        // have a receipt emitted
        for(int i = 0; i < nIters; i++){
            receipt = random.nextBoolean();
            msgId = client.send("server", Integer.toString(i).getBytes(), receipt);
            if(receipt) clientMsgIds.add(msgId);
        }
        System.out.println("Number of receipts asked by the client: " + clientMsgIds.size());
        System.out.flush();

        // receives all the messages at the sender, and
        // sends a modified version of each message back to the client.
        // It also requests a random amount of receipts.
        ClientMsg msg;
        int counter = 0;
        while((msg = server.receive(5000)) != null){
            counter++;
            receipt = random.nextBoolean();
            msgId = server.send("client", ("Received " + new String(msg.msg)).getBytes(), receipt);
            if(receipt) serverMsgIds.add(msgId);
        }
        assert counter == nIters;
        System.out.println("Number of receipts asked by the server: " + serverMsgIds.size());
        System.out.flush();

        // checks if the polled receipts match the asked receipts
        while((msgId = client.pollReceipt(5000)) != null)
            assert clientMsgIds.remove(msgId);
        assert clientMsgIds.size() == 0;

        while((msgId = server.pollReceipt(5000)) != null)
            assert serverMsgIds.remove(msgId);
        assert serverMsgIds.size() == 0;
    }
}