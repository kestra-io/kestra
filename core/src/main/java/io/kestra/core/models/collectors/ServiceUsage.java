package io.kestra.core.models.collectors;

import com.google.common.annotations.VisibleForTesting;
import io.kestra.core.repositories.ServiceInstanceRepositoryInterface;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceInstance;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.LongSummaryStatistics;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Statistics about the number of running services over a given period.
 */
public record ServiceUsage(
    List<DailyServiceStatistics> dailyStatistics
) {

    /**
     * Daily statistics for a specific service type.
     *
     * @param type   The service type.
     * @param values The statistic values.
     */
    public record DailyServiceStatistics(
        String type,
        List<DailyStatistics> values
    ) {
    }

    /**
     * Statistics about the number of services running at any given time interval (e.g., 15 minutes) over a day.
     *
     * @param date The {@link LocalDate}.
     * @param min  The minimum number of services.
     * @param max  The maximum number of services.
     * @param avg  The average number of services.
     */
    public record DailyStatistics(
        LocalDate date,
        long min,
        long max,
        long avg
    ) {
    }
    public static ServiceUsage of(final Instant from,
                                  final Instant to,
                                  final ServiceInstanceRepositoryInterface repository,
                                  final Duration interval) {

        List<DailyServiceStatistics> statistics = Arrays
            .stream(Service.ServiceType.values())
            .map(type -> of(from, to, repository, type, interval))
            .toList();
        return new ServiceUsage(statistics);
    }

    private static DailyServiceStatistics of(final Instant from,
                                             final Instant to,
                                             final ServiceInstanceRepositoryInterface repository,
                                             final Service.ServiceType serviceType,
                                             final Duration interval) {
        return of(serviceType, interval, repository.findAllInstancesBetween(serviceType, from, to));
    }

    @VisibleForTesting
    static DailyServiceStatistics of(final Service.ServiceType serviceType,
                                     final Duration interval,
                                     final List<ServiceInstance> instances) {
        // Compute the number of running service per time-interval.
        final long timeIntervalInMillis = interval.toMillis();

        final Map<Long, Long> aggregatePerTimeIntervals = instances
            .stream()
            .flatMap(instance -> {
                List<ServiceInstance.TimestampedEvent> events = instance.events();
                long start = 0;
                long end = 0;
                for (ServiceInstance.TimestampedEvent event : events) {
                    long epochMilli = event.ts().toEpochMilli();
                    if (event.state().equals(Service.ServiceState.RUNNING)) {
                        start = epochMilli;
                    }
                    else if (event.state().equals(Service.ServiceState.NOT_RUNNING) && end == 0) {
                        end = epochMilli;
                    }
                    else if (event.state().equals(Service.ServiceState.TERMINATED_GRACEFULLY)) {
                        end = epochMilli; // more precise than NOT_RUNNING
                    }
                    else if (event.state().equals(Service.ServiceState.TERMINATED_FORCED)) {
                        end = epochMilli; // more precise than NOT_RUNNING
                    }
                }

                if (instance.state().equals(Service.ServiceState.RUNNING)) {
                    end = Instant.now().toEpochMilli();
                }

                if (start != 0 && end != 0) {
                    // align to epoch-time by removing precision.
                    start = (start / timeIntervalInMillis) * timeIntervalInMillis;

                    // approximate the number of time interval for the current service
                    int intervals = (int) ((end - start) / timeIntervalInMillis);

                    // compute all time intervals
                    List<Long> keys = new ArrayList<>(intervals);
                    while (start < end) {
                        keys.add(start);
                        start = start + timeIntervalInMillis; // Next window
                    }
                    return keys.stream();
                }
                return Stream.empty(); // invalid service
            })
            .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));

        // Aggregate per day
        List<DailyStatistics> dailyStatistics = aggregatePerTimeIntervals.entrySet()
            .stream()
            .collect(Collectors.groupingBy(entry -> {
                Long epochTimeMilli = entry.getKey();
                return Instant.ofEpochMilli(epochTimeMilli).atZone(ZoneId.systemDefault()).toLocalDate();
            }, Collectors.toList()))
            .entrySet()
            .stream()
            .map(entry -> {
                LongSummaryStatistics statistics = entry.getValue().stream().collect(Collectors.summarizingLong(Map.Entry::getValue));
                return new DailyStatistics(
                    entry.getKey(),
                    statistics.getMin(),
                    statistics.getMax(),
                    BigDecimal.valueOf(statistics.getAverage()).setScale(2, RoundingMode.HALF_EVEN).longValue()
                );
            })
            .toList();
        return new DailyServiceStatistics(serviceType.name(), dailyStatistics);
    }
}
