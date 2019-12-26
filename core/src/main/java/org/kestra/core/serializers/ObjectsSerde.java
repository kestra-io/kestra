
package org.kestra.core.serializers;

import io.reactivex.FlowableOnSubscribe;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

abstract public class ObjectsSerde {
    public static void write(ObjectOutputStream output, Object row) throws IOException {
        output.writeObject(row);
        output.reset();
    }

    public static FlowableOnSubscribe<Object> reader(ObjectInputStream input) {
        return s -> {
            try {
                Object row;
                while ((row = input.readObject()) != null) {
                    s.onNext(row);
                }
            } catch (EOFException e) {
                s.onComplete();
            }
        };
    }
}
