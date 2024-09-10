package haslab.eo.tests;

import haslab.eo.EOMiddleware;
import haslab.eo.TransportAddress;
import haslab.eo.msgs.ClientMsg;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

public class PerformanceTests {
    public static long oneToOneSendReceiveTest(final int nrMsgs, int P, int N) {
        try {
            EOMiddleware sender = EOMiddleware.start("sender", null, null, P, N);
            EOMiddleware receiver = EOMiddleware.start("receiver", null, null, P, N);
            sender.registerNode("receiver",new TransportAddress("localhost",receiver.getLocalPort()));

            Semaphore sendSemaphore = new Semaphore(0);
            AtomicLong time = new AtomicLong(0L);

            Thread receiverThread = new Thread(()-> {
                ClientMsg msg;
                try {
                    time.set(System.currentTimeMillis());
                    sendSemaphore.release();
                    int i = 0;
                    while (i < nrMsgs) {
                        msg = receiver.receive();
                        if(msg != null) i++;
                    }
                    time.updateAndGet(operand -> System.currentTimeMillis() - operand);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            });

            // start receiver thread and wait for semaphore
            receiverThread.start();
            ByteBuffer b = ByteBuffer.allocate(4);
            sendSemaphore.acquire();
            for (int i = 0; i < nrMsgs; i++) {
                b.clear();
                sender.send("receiver", b.putInt(i).array());
            }
            receiverThread.join();
            System.out.println("Time= " + time + "ms");
            System.out.flush();
            return time.get();
        } catch (Exception e){
            return 0;
        }
    }

    static class Triple{
        int nrMsgs;
        int P;
        int N;

        public Triple(int nrMsgs, int p, int n) {
            this.nrMsgs = nrMsgs;
            P = p;
            N = n;
        }
    }

    static void putTest(Map<Integer, Triple> tests, int nrMsgs, int P, int N){
        tests.put(tests.size(), new Triple(nrMsgs, P, N));
    }

    @TestFactory
    public List<DynamicTest> oneToOneSendReceiveTestFactory(){
        Map<Integer, Triple> parameters = new HashMap<>();

        // TESTS
        putTest(parameters, 20000, 10, 50);
        putTest(parameters, 20000, 100, 50);
        putTest(parameters, 20000, 1000, 50);
        putTest(parameters, 20000, 5000, 50);
        putTest(parameters, 20000, 10000, 50);

        putTest(parameters, 20000, 2000, 50);
        putTest(parameters, 20000, 2000, 100);
        putTest(parameters, 20000, 2000, 200);
        putTest(parameters, 20000, 2000, 500);
        putTest(parameters, 20000, 2000, 1000);

        List<DynamicTest> tests = new ArrayList<>();
        int nrTests = 10;
        for (int i = 0; i < nrTests; i++) {
            Triple params = parameters.get(i);
            tests.add(DynamicTest.dynamicTest(
                    "Test " + i + " (nrMsgs="+ params.nrMsgs + ", P=" + params.P + ", N=" + params.N + ")"
                    ,() -> oneToOneSendReceiveTest(params.nrMsgs, params.P, params.N)));
        }
        return tests;
    }
}
