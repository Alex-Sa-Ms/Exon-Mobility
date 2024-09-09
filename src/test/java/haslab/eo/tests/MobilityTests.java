package haslab.eo.tests;

import haslab.eo.EOMiddleware;
import haslab.eo.msgs.ClientMsg;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

public class MobilityTests {
    @Test
    public void sendToItself() throws IOException, InterruptedException {
        EOMiddleware eom = EOMiddleware.start("node", 11111);
        String msgContent = "Hello!";
        eom.send("node", msgContent.getBytes());
        ClientMsg msg = eom.receive();
        assert msg != null;
        String msgReceived = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(msg.msg)).toString();
        assert msgContent.equals(msgReceived);
    }
}
