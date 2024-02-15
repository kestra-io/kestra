package io.kestra.repository.memory;

import io.kestra.core.repositories.ServiceInstanceRepositoryInterface;
import io.kestra.core.server.Service;
import io.kestra.core.server.ServiceInstance;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Singleton
@MemoryRepositoryEnabled
public class MemoryServiceInstanceRepository implements ServiceInstanceRepositoryInterface {

    private final Map<String, ServiceInstance> data = new HashMap<>();

    /** {@inheritDoc} **/
    @Override
    public Optional<ServiceInstance> findById(String id) {
        return Optional.ofNullable(data.get(id));
    }

    /** {@inheritDoc} **/
    @Override
    public List<ServiceInstance> findAll() {
        return new ArrayList<>(data.values());
    }

    /** {@inheritDoc} **/
    @Override
    public void delete(ServiceInstance service) {
        data.remove(service.id());
    }

    /** {@inheritDoc} **/
    @Override
    public ServiceInstance save(ServiceInstance service) {
        data.put(service.id(), service);
        return service;
    }

    /** {@inheritDoc} **/
    @Override
    public List<ServiceInstance> findAllInstancesInState(Service.ServiceState state) {
        List<ServiceInstance> instancesInState = new ArrayList<>();
        for (ServiceInstance instance : data.values()) {
            if (instance.state() == state) {
                instancesInState.add(instance);
            }
        }
        return instancesInState;
    }

    /** {@inheritDoc} **/
    @Override
    public List<ServiceInstance> findAllInstancesInStates(List<Service.ServiceState> states) {
        List<ServiceInstance> instancesInStates = new ArrayList<>();
        for (ServiceInstance instance : data.values()) {
            if (states.contains(instance.state())) {
                instancesInStates.add(instance);
            }
        }
        return instancesInStates;
    }
}