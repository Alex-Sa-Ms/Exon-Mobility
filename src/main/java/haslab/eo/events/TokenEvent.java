package haslab.eo.events;

import haslab.eo.TokenRecord;

public final class TokenEvent extends Event {
	public final TokenRecord t;

	public TokenEvent(String nodeId, TokenRecord t, long time) {
		super(nodeId, time);
		this.t = t;
	}

	public TokenRecord getT() {
		return t;
	}
	
}