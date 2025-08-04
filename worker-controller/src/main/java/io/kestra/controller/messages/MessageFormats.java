package io.kestra.controller.messages;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import io.kestra.core.serializers.JacksonMapper;

import java.io.IOException;
import java.util.Optional;

/**
 * Supported formats for serialized messages in Protocol Buffer message.
 */
public enum MessageFormats implements MessageFormat {
    
    JSON() {
        private static final ObjectMapper OBJECT_MAPPER = JacksonMapper.ofJson(false);
        
        /** {@inheritDoc} **/
        @Override
        public <T> T fromByteString(ByteString data, Class<T> type) {
            byte[] bytes = toByteArray(data);
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            try {
                return OBJECT_MAPPER.readValue(bytes, type);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        
        @Override
        public <T> T fromByteString(ByteString data, TypeReference<T> type) {
            byte[] bytes = toByteArray(data);
            if (bytes == null || bytes.length == 0) {
                return null;
            }
            try {
                return OBJECT_MAPPER.readValue(bytes, type);
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
                return ByteString.copyFrom(OBJECT_MAPPER.writeValueAsBytes(value));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        }
    };
    
    private static byte[] toByteArray(ByteString data) {
        return Optional.ofNullable(data).map(ByteString::toByteArray).orElse(null);
    }
}
