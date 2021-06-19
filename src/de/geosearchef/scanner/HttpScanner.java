package de.geosearchef.scanner;

import de.geosearchef.TechSupportBot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.Instant;

public class HttpScanner extends ServiceScanner {

    private static Logger logger = LoggerFactory.getLogger(HttpScanner.class);

    private URL route;

    public HttpScanner(String representation, String route) {
        super(representation);
        try {
            this.route = new URL(route);
        } catch (MalformedURLException e) {
            logger.error("Couldn't parse given URL " + route, e);
        }
    }

    @Override
    protected void runScanAsync() {
        try {
            HttpURLConnection connection = (HttpURLConnection) route.openConnection();
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            int status = connection.getResponseCode();

            if(status == 200) {
                this.lastResponse = Instant.now();
                logger.trace("Service is up " + route);
            }
        } catch (IOException e) {
            logger.error("Couldn't connect connection to server " + route);
        }
    }
}
