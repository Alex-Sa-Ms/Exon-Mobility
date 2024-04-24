package haslab.eo.msgs;

import java.util.Objects;

// Used to identify outgoing messages.
// Incoming messages do not need an identifier.
public class MsgId {
	public final long id;

	public MsgId(long id) {
        this.id = id;
    }

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;

		MsgId msgId = (MsgId) o;
        return this.id == msgId.id;
    }
}