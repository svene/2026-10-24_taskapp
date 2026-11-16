package org.svenehrke.taskapp.server;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;
import java.time.Clock;

/** "Today" for every Handler: the laptop's clock in its default zone. Tests replace it with a mutable Clock. */
@ApplicationScoped
class ClockProducer {

    @Produces
    @Singleton
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}
