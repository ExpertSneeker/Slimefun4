package city.norain.slimefun4.utils;

import java.time.Duration;
import java.time.LocalDateTime;

public class SimpleTimer {
    private final LocalDateTime startTime;
    private Duration snapshot = null;

    public SimpleTimer() {
        startTime = LocalDateTime.now();
    }

    public Duration duration() {
        var duration = Duration.between(startTime, LocalDateTime.now());
        snapshot = duration;
        return duration;
    }

    public String durationStr() {
        if (snapshot == null) {
            snapshot = duration();
        }

        return String.format("%d:%02d:%02d", snapshot.toHours(), snapshot.toMinutesPart(), snapshot.toSecondsPart());
    }

    public boolean isTimeout(Duration duration) {
        if (snapshot == null) {
            return false;
        }

        return snapshot.compareTo(duration) > 0;
    }
}
