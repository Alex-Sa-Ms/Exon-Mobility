package haslab.eo.msgs;


import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class TokenMsg extends NetMsg {
	public final long s, r;
	public byte[] payload;

	public TokenMsg(String srcId, String destId, long s, long r, byte[] payload) {
		super(srcId, destId);
		this.s = s;
		this.r = r;
		this.payload = payload;
	}

	@Override
	public String toString() {
		return "TokenMsg{" +
				"s=" + s +
				", r=" + r +
				", payload=" + StandardCharsets.UTF_8.decode(ByteBuffer.wrap(payload)) +
				", srcId='" + srcId + '\'' +
				", destId='" + destId + '\'' +
				'}';
	}
}