package io.kestra.core.serializers.ion;

import java.io.Closeable;
import java.io.InputStream;
import java.io.Reader;

import com.amazon.ion.IonReader;
import com.amazon.ion.IonSystem;
import com.amazon.ion.IonWriter;

import tools.jackson.core.ObjectReadContext;
import tools.jackson.core.ObjectWriteContext;
import tools.jackson.core.io.IOContext;

public class IonFactory extends tools.jackson.dataformat.ion.IonFactory {
    public IonFactory(IonSystem system, boolean binaryWriters) {
        super(
            (binaryWriters
                ? tools.jackson.dataformat.ion.IonFactory.builderForBinaryWriters()
                : tools.jackson.dataformat.ion.IonFactory.builderForTextualWriters())
                .ionSystem(system)
        );
    }

    // The base IonFactory's createParser(InputStream)/createParser(Reader)/createParser(byte[])/createParser(String)
    // route through private helpers that hardcode the base tools.jackson.dataformat.ion.IonParser, bypassing any
    // override of createParser(ObjectReadContext, IonReader). Overriding these entry points directly is required
    // so our custom IonParser (with its Instant/LocalDate/etc. embedded-object handling) is actually used.
    @Override
    public IonParser createParser(ObjectReadContext readCtxt, InputStream in) {
        return createParser(readCtxt, _system.newReader(in));
    }

    @Override
    public IonParser createParser(ObjectReadContext readCtxt, Reader r) {
        return createParser(readCtxt, _system.newReader(r));
    }

    @Override
    public IonParser createParser(ObjectReadContext readCtxt, byte[] data) {
        return createParser(readCtxt, _system.newReader(data));
    }

    @Override
    public IonParser createParser(ObjectReadContext readCtxt, byte[] data, int offset, int len) {
        return createParser(readCtxt, _system.newReader(data, offset, len));
    }

    @Override
    public IonParser createParser(ObjectReadContext readCtxt, IonReader ionReader) {
        IOContext ioCtxt = _createContext(_createContentReference(ionReader), false);
        return new IonParser(readCtxt, ioCtxt, _streamReadFeatures, _formatReadFeatures, ionReader, _system);
    }

    @Override
    protected tools.jackson.dataformat.ion.IonGenerator _createGenerator(ObjectWriteContext writeCtxt, IOContext ctxt, IonWriter ion, boolean ionWriterIsManaged, Closeable dst) {
        return new IonGenerator(writeCtxt, ctxt, _streamWriteFeatures, _formatWriteFeatures, ion, ionWriterIsManaged, dst);
    }
}
