package io.kestra.core.repositories;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kestra.core.models.Setting;
import io.kestra.jdbc.JdbcMapper;
import jakarta.validation.ConstraintViolationException;

import java.util.*;

public class InMemorySettingRepository implements SettingRepositoryInterface {
    private final Map<String, String> settings = new HashMap<>();
    private static final ObjectMapper MAPPER = JdbcMapper.of();

    @Override
    public Optional<Setting> findByKey(String key) {
        return deserialize(settings.get(key));
    }

    @Override
    public List<Setting> findAll() {
        return new ArrayList<>(settings.values().stream().map(this::deserialize)
            .filter(Optional::isPresent).map(Optional::get).toList());
    }

    @Override
    public Setting save(Setting setting) throws ConstraintViolationException {
        return internalSave(setting);
    }

    @Override
    public Setting internalSave(Setting setting) throws ConstraintViolationException {
        settings.put(setting.getKey(), serialize(setting));
        return setting;
    }

    @Override
    public Setting delete(Setting setting) {
        if (!settings.containsKey(setting.getKey())) {
            throw new IllegalStateException("Setting " + setting.getKey() + " doesn't exists");
        }

        settings.remove(setting.getKey());
        return setting;
    }

    public void clear() {
        settings.clear();
    }

    private String serialize(Setting setting) {
        try {
            return MAPPER.writeValueAsString(setting);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
    private Optional<Setting> deserialize(String jsonString) {
        if (jsonString == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(MAPPER.readValue(jsonString, Setting.class));
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
