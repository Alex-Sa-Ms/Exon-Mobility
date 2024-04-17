package haslab.eo.msgs;

import java.util.Objects;

// Used to identify outgoing messages.
// Incoming messages do not need an identifier.
public class MsgId {
	public final String nodeId; // identifier of the destination node
	public final long clock; // timestamp of queueing time
	public final int hash; // hash of message's byte array

	public MsgId(String nodeId, long clock, int hash) {
		this.nodeId = nodeId;
		this.clock = clock;
        this.hash = hash;
    }

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		MsgId msgId = (MsgId) o;

		if (clock != msgId.clock) return false;
		if (hash != msgId.hash) return false;
        return Objects.equals(nodeId, msgId.nodeId);
    }
}