package io.kestra.controller.messages;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.google.protobuf.UnsafeByteOperations;

import io.kestra.core.serializers.JacksonMapper;

/**
 * Supported formats for serialized messages in Protocol Buffer message.
 */
public enum MessageFormats implements MessageFormat {

    JSON() {
        private static final ObjectMapper OBJECT_MAPPER = JacksonMapper.ofJson(false);

        /** {@inheritDoc} **/
        @Override
        public <T> T fromByteString(ByteString data, Class<T> type) {
            if (data == null || data.isEmpty()) {
                return null;
            }
            try {
                return OBJECT_MAPPER.readValue(data.newInput(), type);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public <T> T fromByteString(ByteString data, TypeReference<T> type) {
            if (data == null || data.isEmpty()) {
                return null;
            }
            try {
                return OBJECT_MAPPER.readValue(data.newInput(), type);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        /** {@inheritDoc} **/
        @Override
        public ByteString toByteString(Object value) {
            if (value == null) {
                return ByteString.EMPTY;
            }
            try {
                // the array is never touched again, so wrapping it saves a copy of every message
                return UnsafeByteOperations.unsafeWrap(OBJECT_MAPPER.writeValueAsBytes(value));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    };
}
