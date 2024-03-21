package haslab.eo;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Objects;

public class TransportAddress implements Serializable {
    public final InetAddress addr;
    public final int port;

    public TransportAddress(String addr, int port) throws UnknownHostException {
        this.addr = InetAddress.getByName(addr);
        this.port = port;
    }

    public TransportAddress(InetAddress addr, int port) {
        this.addr = addr;
        this.port = port;
    }

    @Override
    public int hashCode() {
        return Objects.hash(addr, port);
    }

    @Override
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof TransportAddress))
            return false;
        TransportAddress other = (TransportAddress) o;
        return this.port == other.port && this.addr.equals(other.addr);
    }
}
