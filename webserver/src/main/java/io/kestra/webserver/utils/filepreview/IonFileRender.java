package io.kestra.webserver.utils.filepreview;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.serializers.FileSerde;
import io.kestra.core.serializers.JacksonMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static io.kestra.core.utils.Rethrow.throwConsumer;

public class IonFileRender extends FileRender {
    public List<Object> content;

    private ObjectMapper MAPPER = JacksonMapper.ofIon()
        .setSerializationInclusion(JsonInclude.Include.ALWAYS);

    IonFileRender(String extension, InputStream filestream) throws IOException {
        super(extension);
        renderContent(filestream);

        this.type = TYPE.list;
    }

    public void renderContent(InputStream filestream) throws IOException {
        try (BufferedReader inputStream = new BufferedReader(new InputStreamReader(filestream))) {
            AtomicLong lineCount = new AtomicLong();

            List<Object> list = new ArrayList<>();
            FileSerde.reader(inputStream, throwConsumer(e -> {
                if (lineCount.get() > maxLines) {
                    return;
                }
                list.add(e);
                lineCount.incrementAndGet();
            }));

            this.content = list;
        }
    }
}
