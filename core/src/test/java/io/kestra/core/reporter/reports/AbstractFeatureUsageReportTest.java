package io.kestra.core.reporter.reports;

import io.kestra.core.junit.annotations.KestraTest;
import io.kestra.core.reporter.Reportable;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

@KestraTest
public abstract class AbstractFeatureUsageReportTest {
    
    @Inject
    FeatureUsageReport featureUsageReport;
    
    @Test
    public void shouldGetReport() {
        // When
        Instant now = Instant.now();
        FeatureUsageReport.UsageEvent event = featureUsageReport.report(
            now, 
            Reportable.TimeInterval.of(now.minus(Duration.ofDays(1)).atZone(ZoneId.systemDefault()), now.atZone(ZoneId.systemDefault()))
        );
        
        // Then
        assertThat(event.getExecutions().getDailyExecutionsCount().size()).isGreaterThan(0);
        assertThat(event.getExecutions().getDailyTaskRunsCount()).isNull();
    }
}