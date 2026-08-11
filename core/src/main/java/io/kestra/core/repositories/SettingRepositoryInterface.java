package io.kestra.core.repositories;

import java.util.List;
import java.util.Optional;

import io.kestra.core.models.Setting;

import jakarta.validation.ConstraintViolationException;

public interface SettingRepositoryInterface {
    Optional<Setting> findByKey(String key);

    List<Setting> findAll();

    Setting save(Setting setting) throws ConstraintViolationException;

    Setting internalSave(Setting setting) throws ConstraintViolationException;

    Setting delete(Setting setting);
}
