package io.kestra.webserver.utils.filepreview;

import java.io.IOException;
import java.io.InputStream;
import org.apache.parquet.column.page.PageReadStore;
import org.apache.parquet.example.data.Group;
import org.apache.parquet.example.data.simple.convert.GroupRecordConverter;
import org.apache.parquet.hadoop.ParquetFileReader;
import org.apache.parquet.io.ColumnIOFactory;
import org.apache.parquet.io.InputFile;
import org.apache.parquet.io.MessageColumnIO;
import org.apache.parquet.io.RecordReader;
import org.apache.parquet.io.SeekableInputStream;
import org.apache.parquet.io.api.RecordMaterializer;
import org.apache.parquet.schema.MessageType;

import java.io.ByteArrayInputStream;
import java.nio.ByteBuffer;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class ParquetFileRender extends FileRender {

    public ParquetFileRender(String extension, InputStream filestream, Integer maxLine) throws IOException {
        this(extension, filestream, StandardCharsets.UTF_8, maxLine);
    }

    public ParquetFileRender(String extension, InputStream filestream, Charset charset, Integer maxLine) throws IOException {
        super(extension, maxLine);
        renderContent(filestream, charset);

        this.type = Type.LIST;
    }

    public ParquetFileRender(String extension, InputStream filestream, Type type, Integer maxLine) throws IOException {
        this(extension, filestream, StandardCharsets.UTF_8, maxLine);
        this.type = type;
    }

    private void renderContent(InputStream fileStream, Charset charset) throws IOException {
        
        byte[] data = fileStream.readAllBytes();

        SeekableInputStream seekableInputStream = new InMemorySeekableInputStream(data);

        InputFile inputFile = new InputFile() {
            @Override
            public long getLength() {
                return data.length;
            }

            @Override
            public SeekableInputStream newStream() {
                return seekableInputStream;
            }
        };

        try (ParquetFileReader reader = ParquetFileReader.open(inputFile)) {
            MessageType schema = reader.getFooter().getFileMetaData().getSchema();
            PageReadStore pageStore;
            int count = 0;

            List<Object> contentList = new ArrayList<>();

            while ((pageStore = reader.readNextRowGroup()) != null && count < maxLine) {
                long rows = pageStore.getRowCount();

                MessageColumnIO columnIO = new ColumnIOFactory().getColumnIO(schema);
                RecordMaterializer<Group> materializer = new GroupRecordConverter(schema);
                RecordReader<Group> recordReader = columnIO.getRecordReader(pageStore, materializer);

                

                for (int i = 0; i < rows && count < maxLine; i++) {
                    
                    Map<String, Object> row = new HashMap<>();

                    Group group = recordReader.read();
                    for (int fieldIndex = 0; fieldIndex < schema.getFieldCount(); fieldIndex++) {
                        String fieldName = schema.getFieldName(fieldIndex);
                        int valueCount = group.getFieldRepetitionCount(fieldIndex);

                        if (valueCount == 0) {
                            row.put(fieldName, null);
                        } else if (valueCount == 1) {
                            row.put(fieldName, getFieldValue(group, fieldIndex, 0, charset));
                        } else {
                            List<Object> values = new ArrayList<>();
                            for (int valueIndex = 0; valueIndex < valueCount; valueIndex++) {
                                values.add(getFieldValue(group, fieldIndex, valueIndex, charset));
                            }
                            row.put(fieldName, values);
                        }

                    }
                    count++;
                    contentList.add(row);
                }
            }

            this.content = contentList;
            this.truncated = count >= maxLine;



        }
        

    }

    private Object getFieldValue(Group group, int fieldIndex, int valueIndex, Charset charset) {
        try {
            if (group.getType().getType(fieldIndex).isPrimitive() &&
                group.getType().getType(fieldIndex).asPrimitiveType().getPrimitiveTypeName().name().equals("BINARY")) {
                byte[] bytes = group.getBinary(fieldIndex, valueIndex).getBytes();
                String asString = new String(bytes, charset);
                if (asString.chars().allMatch(c -> c >= 32 && c < 127)) {
                    return asString;
                } else {
                    return javax.xml.bind.DatatypeConverter.printHexBinary(bytes);
                }
            } else {
                return group.getValueToString(fieldIndex, valueIndex);
            }
        } catch (Exception e) {
            return "[unreadable]";
        }
    }

    // Custom SeekableInputStream wrapper over in-memory byte array
    static class InMemorySeekableInputStream extends SeekableInputStream {
        private final ByteArrayInputStream bais;
        private final byte[] data;
        private int position = 0;

        public InMemorySeekableInputStream(byte[] data) {
            this.data = data;
            this.bais = new ByteArrayInputStream(data);
        }

        @Override
        public long getPos() {
            return position;
        }

        @Override
        public void seek(long newPos) throws IOException {
            if (newPos < 0 || newPos > data.length)
                throw new IOException("Invalid seek position");
            bais.reset();
            bais.skip(newPos);
            position = (int) newPos;
        }

        @Override
        public int read() {
            position++;
            return bais.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
            int bytesRead = bais.read(b, off, len);
            position += bytesRead;
            return bytesRead;
        }

        @Override
        public void close() throws IOException {
            bais.close();
        }

        @Override
        public void readFully(byte[] bytes) throws IOException {
            int read = bais.read(bytes);
            if (read < bytes.length) {
                throw new IOException("Could not read enough bytes");
            }
            position += read;
        }

        @Override
        public void readFully(byte[] bytes, int start, int len) throws IOException {
            int read = bais.read(bytes, start, len);
            if (read < len) {
                throw new IOException("Could not read enough bytes");
            }
            position += read;
        }

        @Override
        public int read(ByteBuffer buf) throws IOException {
            byte[] tmp = new byte[buf.remaining()];
            int read = bais.read(tmp);
            if (read > 0) {
                buf.put(tmp, 0, read);
                position += read;
            }
            return read;
        }

        @Override
        public void readFully(ByteBuffer buf) throws IOException {
            int total = 0;
            while (buf.hasRemaining()) {
                int read = read(buf);
                if (read == -1) {
                    throw new IOException("End of stream reached before filling buffer");
                }
                total += read;
            }
        }
    }
}
