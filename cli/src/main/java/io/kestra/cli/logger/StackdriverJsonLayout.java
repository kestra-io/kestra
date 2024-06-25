package io.kestra.cli.logger;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.contrib.json.classic.JsonLayout;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@SuppressWarnings("this-escape")
@Data
public class StackdriverJsonLayout extends JsonLayout {
    private static final String SEVERITY_ATTRIBUTE = "severity";
    private static final String TIMESTAMP_SECONDS_ATTRIBUTE = "timestampSeconds";
    private static final String TIMESTAMP_NANOS_ATTRIBUTE = "timestampNanos";

    private boolean includeExceptionInMessage;

    private Map<String, Object> customJson;

    public StackdriverJsonLayout() {
        this.appendLineSeparator = true;
        this.includeExceptionInMessage = true;
        this.includeException = false;
        ObjectMapper mapper = new ObjectMapper();
        setJsonFormatter(mapper::writeValueAsString);
    }

    @Override
    protected Map<String, Object> toJsonMap(ILoggingEvent event) {
        Map<String, Object> map = new LinkedHashMap<>();

        if (this.includeMDC) {
            map.putAll(event.getMDCPropertyMap());
        }

        if (this.includeTimestamp) {
            map.put(TIMESTAMP_SECONDS_ATTRIBUTE, TimeUnit.MILLISECONDS.toSeconds(event.getTimeStamp()));
            map.put(TIMESTAMP_NANOS_ATTRIBUTE, TimeUnit.MILLISECONDS.toNanos(event.getTimeStamp() % 1_000));
        }

        add(SEVERITY_ATTRIBUTE, this.includeLevel, String.valueOf(event.getLevel()), map);
        add(JsonLayout.THREAD_ATTR_NAME, this.includeThreadName, event.getThreadName(), map);
        add(JsonLayout.LOGGER_ATTR_NAME, this.includeLoggerName, event.getLoggerName(), map);

        if (this.includeFormattedMessage) {
            String message = event.getFormattedMessage();
            if (this.includeExceptionInMessage) {
                IThrowableProxy throwableProxy = event.getThrowableProxy();
                if (throwableProxy != null) {
                    String stackTrace = getThrowableProxyConverter().convert(event);
                    if (stackTrace != null && !stackTrace.equals("")) {
                        message += "\n" + stackTrace;
                    }
                }
            }
            map.put(JsonLayout.FORMATTED_MESSAGE_ATTR_NAME, message);
        }
        add(JsonLayout.MESSAGE_ATTR_NAME, this.includeMessage, event.getMessage(), map);
        add(JsonLayout.CONTEXT_ATTR_NAME, this.includeContextName, event.getLoggerContextVO().getName(), map);
        addThrowableInfo(JsonLayout.EXCEPTION_ATTR_NAME, this.includeException, event, map);

        if (this.customJson != null && !this.customJson.isEmpty()) {
            for (Map.Entry<String, Object> entry : this.customJson.entrySet()) {
                map.putIfAbsent(entry.getKey(), entry.getValue());
            }
        }

        addCustomDataToJsonMap(map, event);

        return map;
    }
}
