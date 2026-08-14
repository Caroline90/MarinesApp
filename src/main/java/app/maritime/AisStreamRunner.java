package app.maritime;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
final class AisStreamRunner implements ApplicationRunner {
    private final AisStreamClient client;

    AisStreamRunner(AisStreamClient client) {
        this.client = client;
    }

    @Override
    public void run(ApplicationArguments args) {
        client.start();
    }
}
