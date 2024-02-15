package io.kestra.core.utils;

import java.net.InetAddress;
import java.net.UnknownHostException;

public final class Network {

    private static final String HOSTNAME;

    static {
        try {
            HOSTNAME = InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);
        }
    }

    public static String localHostname() {
        return HOSTNAME;
    }
    private Network() {}
}
