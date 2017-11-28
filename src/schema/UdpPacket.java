package schema;

import java.io.Serializable;
import java.util.HashMap;

// holds the data representation that can be passed amongst the servers.

public class UdpPacket implements Serializable {
    private static final long serialVersionUID = 1L;
    public int operation, fePort, sequence;
    public HashMap<String, Object> body;

    public UdpPacket(int operation, HashMap<String, Object> body) {
        this.operation = operation;
        this.body = body;
        this.fePort = -1;
    }

    public UdpPacket(int operation, HashMap<String, Object> body, int sequence, int fePort) {
        this.operation = operation;
        this.body = body;
        this.fePort = fePort;
        this.sequence = sequence;
    }
}
