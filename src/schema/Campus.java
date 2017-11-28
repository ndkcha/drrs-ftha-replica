package schema;

import java.io.Serializable;

// holds the data representation that can be passed amongst the servers.

public class Campus implements Serializable {
    private int udpPort;
    private String code;
    public String name;

    public Campus(int udpPort, String code, String name) {
        this.udpPort = udpPort;
        this.code = code;
        this.name = name;
    }

    public int getUdpPort() {
        return udpPort;
    }

    public String getCode() {
        return code;
    }
}
