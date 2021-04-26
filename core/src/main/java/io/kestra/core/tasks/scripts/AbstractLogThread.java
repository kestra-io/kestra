package io.kestra.core.tasks.scripts;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class AbstractLogThread extends Thread {
    private final InputStream inputStream;
    private int logsCount = 0;
    protected final Map<String, Object> outputs = new ConcurrentHashMap<>();

    protected AbstractLogThread(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    @Override
    public void run() {
        try {
            InputStreamReader inputStreamReader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
            try (BufferedReader bufferedReader = new BufferedReader(inputStreamReader)) {
                String line;
                while ((line = bufferedReader.readLine()) != null) {
                    this.logsCount++;
                    this.call(line);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    abstract protected void call(String line);

    public int getLogsCount() {
        return logsCount;
    }

    public Map<String, Object> getOutputs() {
        return outputs;
    }
}
