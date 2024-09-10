package haslab.eo.tests;

import haslab.eo.EOMiddleware;
import haslab.eo.SocketsCleaner;
import haslab.eo.TransportAddress;
import haslab.eo.exceptions.ClosedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static java.lang.Thread.sleep;

public class CloseOperationTests {
    @BeforeEach
    void closeDatagramSockets(){
        SocketsCleaner.clean();
    }

    @Test
    public void sendAfterClose(){
        try {
            EOMiddleware node = SocketsCleaner.add(EOMiddleware.start("node", "localhost", 22222, null));
            node.closeNoWait();
            node.send("node2", "Hello World!".getBytes());
        } catch (RuntimeException e) {
            if(e.getCause() instanceof ClosedException) {
                // A RuntimeException must be thrown and the
                // ClosedException should be the cause.
                return;
            }
        } catch (Exception e){}
        assert false;
    }

    @TestFactory
    public Stream<DynamicTest> closeDuringConversationTestFactory(){
        System.out.println("WARNING: Set a low closeTimeout (time between each close event) before testing.");
        return List.of(10, 50, 100, 200, 500)
                .stream().map(nMsgs ->
                        DynamicTest.dynamicTest(
                                "Close During Conv (" + nMsgs + ")",
                                () -> closeDuringConversation(nMsgs)));
    }

    private void closeDuringConversation(int nMsgs){
        try {
            Thread ctThread = new Thread(() -> {
                try {
                    EOMiddleware clientEO = SocketsCleaner.add(EOMiddleware.start("client", "localhost", 22222, null));
                    clientEO.registerNode("server", new TransportAddress("localhost", 11111));
                    for(int i = 0; i < nMsgs; i++)
                        clientEO.send("server", Integer.toString(i).getBytes());
                    clientEO.close();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            Thread svThread = new Thread(() -> {
                try {
                    int msgCounter = 0;
                    EOMiddleware serverEO = SocketsCleaner.add(EOMiddleware.start("server", "localhost", 11111, null));
                    serverEO.receive(5000); // wait for the first message to arrive
                    msgCounter++;
                    serverEO.close();

                    boolean rcv_timedout = false;
                    long rcv_timeout = 45 * nMsgs; // 45 ms per message
                    long rcv_stopTime = System.currentTimeMillis() + rcv_timeout;
                    while (!rcv_timedout && msgCounter != nMsgs){
                        var msg = serverEO.receive(rcv_timeout);
                        if(msg != null) msgCounter++;
                        rcv_timeout = rcv_stopTime - System.currentTimeMillis();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            ctThread.start();
            svThread.start();

            long timeout = 10000 /* tolerance for initialization/closing purposes */
                            + nMsgs * 50; // 50 ms per message
            long timeoutTime = System.currentTimeMillis() + timeout;
            svThread.join(timeout);
            timeout = Math.max(0, timeoutTime - System.currentTimeMillis());
            ctThread.join(timeout);

            if(System.currentTimeMillis() >= timeoutTime)
                assert false;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    //@Test
    public void DEBUG_closeDuringConversation(){
        int nMsgs = 500;
        try {
            AtomicReference<EOMiddleware> clientEO = new AtomicReference<>();
            AtomicReference<EOMiddleware> serverEO = new AtomicReference<>();

            Thread ctThread = new Thread(() -> {
                try {
                    clientEO.set(SocketsCleaner.add(EOMiddleware.start("client", "localhost", 22222, null)));
                    clientEO.get().registerNode("server", new TransportAddress("localhost", 11111));
                    for(int i = 0; i < nMsgs; i++)
                        clientEO.get().send("server", Integer.toString(i).getBytes());
                    clientEO.get().close();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            Thread svThread = new Thread(() -> {
                try {
                    int msgCounter = 0;
                    serverEO.set(SocketsCleaner.add(EOMiddleware.start("server", "localhost", 11111, null)));
                    serverEO.get().receive(5000); // wait for the first message to arrive
                    msgCounter++;
                    serverEO.get().close();

                    boolean rcv_timedout = false;
                    long rcv_timeout = 45 * nMsgs; // 45 ms per message
                    long rcv_stopTime = System.currentTimeMillis() + rcv_timeout;
                    while (!rcv_timedout && msgCounter != nMsgs){
                        var msg = serverEO.get().receive(rcv_timeout);
                        if(msg != null) msgCounter++;
                        rcv_timeout = rcv_stopTime - System.currentTimeMillis();
                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });

            ctThread.start();
            svThread.start();

            while(svThread.isAlive() || ctThread.isAlive()) {
                sleep(1000);
                svThread.join(1);
                ctThread.join(1);
                if(serverEO.get() != null) {
                    System.out.println("----------- ServerEO -----------");
                    System.out.println("IsClosed: " + serverEO.get().isClosed());
                    serverEO.get().debugPrints();
                }
                if(clientEO.get() != null) {
                    System.out.println("\n\n\n----------- ClientEO -----------");
                    System.out.println("IsClosed: " + clientEO.get().isClosed());
                    clientEO.get().debugPrints();
                }
                System.out.println("**************************\n\n\n\n");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
