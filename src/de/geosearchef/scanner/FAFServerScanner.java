package de.geosearchef.scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.time.Instant;

public class FAFServerScanner extends ServiceScanner {

    private static Logger logger = LoggerFactory.getLogger(FAFServerScanner.class);

    private final String host;
    private final int port;

    public FAFServerScanner(String representation, String host, int port) {
        super(representation);
        this.host = host;
        this.port = port;
    }

    @Override
    protected void runScanAsync() {
        try {
            Socket socket = new Socket();
            socket.setSoTimeout(1000);
            socket.connect(new InetSocketAddress(InetAddress.getByName(host), port), 1000);

            DataInputStream in = new DataInputStream(socket.getInputStream());
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());

            // TODO: sent init message and wait for wrong version response

//            byte[] data = new byte[4096];
//            int bytesRead = in.read(data, 0, data.length);
//
//            System.out.println(new String(data, 0, bytesRead));

            if(socket.isConnected()) {
                this.lastResponse = Instant.now();
                logger.trace("Service is up " + host + ":" + port);
            }

            socket.close();
        } catch (IOException e) {
            logger.error("Couldn't reach " + host + ":" + port + " FAF server");
        }
    }
}
