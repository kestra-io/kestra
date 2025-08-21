package io.kestra.core.reporter;

public abstract class AbstractReportable<T extends Reportable.Event> implements Reportable<T> {
    
    private final Type type;
    private final ReportingSchedule schedule;
    private final boolean isTenantSupported;
    
    public AbstractReportable(Type type, ReportingSchedule schedule, boolean isTenantSupported) {
        this.type = type;
        this.schedule = schedule;
        this.isTenantSupported = isTenantSupported;
    }
    
    @Override
    public boolean isTenantSupported() {
        return isTenantSupported;
    }
    
    @Override
    public Type type() {
        return type;
    }
    
    @Override
    public ReportingSchedule schedule() {
        return schedule;
    }
}
