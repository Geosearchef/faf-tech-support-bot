package de.geosearchef.scanner;

import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;

public class SystemsScanner {

    private static final Duration SCAN_INTERVAL = Duration.ofSeconds(30);
    public static Instant lastScan = Instant.now().minus(SCAN_INTERVAL.plus(Duration.ofSeconds(2)));
    @Getter private static volatile boolean periodicScannerRunning = false;

    private static Logger logger = LoggerFactory.getLogger(SystemsScanner.class);

    public static ArrayList<ServiceScanner> services = new ArrayList<>();

    public static void init() {
        services = new ArrayList<ServiceScanner>() {{
            add(new FAFServerScanner("Lobby-Server", "lobbys.faforever.com", 8002));
            add(new HttpScanner("API", "https://api.faforever.com/"));
            add(new HttpScanner("Webserver", "https://faforever.com"));
            add(new HttpScanner("Forums", "https://forum.faforever.com/"));
        }};
    }

    public static void scanAll() {
        logger.info("Scanning " + services.size() + " services");
        services.forEach(ServiceScanner::scan);
    }

    public static void startPeriodicScanner() {
        if(periodicScannerRunning) {
            logger.error("Couldn't start periodic scanner, already running");
            return;
        }

        periodicScannerRunning = true;
        new Thread(() -> {
            while(true) {
                if(Instant.now().isAfter(lastScan.plus(SCAN_INTERVAL))) {
                    scanAll();
                    lastScan = Instant.now();
                }

                try {
                    Thread.sleep(SCAN_INTERVAL.toMillis() + 500);
                } catch (InterruptedException e) {
                    logger.error("Interrupted while sleeping", e);
                    break;
                }
            }
            periodicScannerRunning = false;
        }).start();
    }


    // Test
    public static void main(String args[]) throws InterruptedException {
        init();

        scanAll();
        Thread.sleep(2000);

        services.forEach(it -> System.out.println(it.isUp()));
    }
}
