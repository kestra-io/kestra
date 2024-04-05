package io.kestra.core.serializers.ion;

import com.amazon.ion.IonReader;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonWriter;
import com.amazon.ion.system.IonSystemBuilder;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.io.IOContext;

import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;

public class IonFactory extends com.fasterxml.jackson.dataformat.ion.IonFactory {
    private static final long serialVersionUID = 1L;

    public IonFactory(IonSystem system) {
        super(null, system);
    }

    @Override
    protected JsonParser _createParser(Reader r, IOContext ctxt) throws IOException {
        IonReader ionReader = IonSystemBuilder.standard().build().newReader(r);
        return new IonParser(ionReader, ctxt);
    }

    protected com.fasterxml.jackson.dataformat.ion.IonGenerator _createGenerator(IonWriter ion, boolean ionWriterIsManaged, IOContext ctxt, Closeable dst) {
        return new IonGenerator(
            _generatorFeatures,
            _ionGeneratorFeatures,
            _objectCodec,
            ion,
            ionWriterIsManaged,
            ctxt,
            dst
        );
    }
}
