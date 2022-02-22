package io.kestra.core.models.collectors;

import io.micronaut.core.annotation.Introspected;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.jackson.Jacksonized;
import oshi.SystemInfo;
import oshi.hardware.CentralProcessor;
import oshi.hardware.ComputerSystem;
import oshi.hardware.HardwareAbstractionLayer;
import oshi.hardware.NetworkIF;
import oshi.software.os.OperatingSystem;

import java.lang.management.ManagementFactory;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@SuperBuilder
@Getter
@Jacksonized
@Introspected
public class HostUsage {
    private final String uuid;
    private final Hardware hardware;
    private final Os os;
    private final Jvm jvm;

    @SuperBuilder
    @Getter
    @Jacksonized
    @Introspected
    public static class Hardware {
        private final int logicalProcessorCount;
        private final long physicalProcessorCount;
        private final long maxFreq;
        private final long memory;
        private final boolean knownVmMacAddr;
        private final boolean knownDockerMacAddr;
    }

    @SuperBuilder
    @Getter
    @Jacksonized
    @Introspected
    public static class Os {
        private final String family;
        private final String version;
        private final String codeName;
        private final String buildNumber;
    }

    @SuperBuilder
    @Getter
    @Jacksonized
    @Introspected
    public static class Jvm {
        private final String name;
        private final String vendor;
        private final String version;
    }

    public static HostUsage of() {
        SystemInfo systemInfo = new SystemInfo();
        OperatingSystem operatingSystem = systemInfo.getOperatingSystem();
        HardwareAbstractionLayer hardware = systemInfo.getHardware();
        CentralProcessor processor = hardware.getProcessor();
        ComputerSystem computerSystem = hardware.getComputerSystem();

        String vendor = operatingSystem.getManufacturer();
        String processorSerialNumber = computerSystem.getSerialNumber();
        if (processorSerialNumber.equals("unknown")) {
            processorSerialNumber = String.join("-", computerSystem.getManufacturer(), computerSystem.getModel(), computerSystem.getBaseboard().getModel());
        }

        String hardwareUUID = computerSystem.getHardwareUUID();
        String processorIdentifier = processor.getProcessorIdentifier().getIdentifier();
        String processorsCount = String.valueOf(processor.getLogicalProcessorCount());

        String hostUuid = Stream.of(
            vendor,
            processorSerialNumber,
            hardwareUUID,
            processorIdentifier,
            processorsCount
        )
            .filter(Objects::nonNull)
            .filter(s -> !s.equals("unknown"))
            .map(s -> String.format("%08x", s.hashCode()))
            .collect(Collectors.joining("-"));

        return HostUsage.builder()
            .uuid(hostUuid)
            .hardware(HostUsage.Hardware.builder()
                .logicalProcessorCount(processor.getLogicalProcessorCount())
                .physicalProcessorCount(processor.getPhysicalProcessorCount())
                .maxFreq(processor.getMaxFreq())
                .memory(hardware.getMemory().getTotal())
                .knownVmMacAddr(hardware.getNetworkIFs().stream().anyMatch(NetworkIF::isKnownVmMacAddr))
                .knownDockerMacAddr(hardware.getNetworkIFs().stream().anyMatch(networkIF -> networkIF.getMacaddr().startsWith("02:42:ac")))
                .build()
            )
            .os(HostUsage.Os.builder()
                .family(operatingSystem.getFamily())
                .version(operatingSystem.getVersionInfo().getVersion())
                .codeName(operatingSystem.getVersionInfo().getCodeName())
                .buildNumber(operatingSystem.getVersionInfo().getBuildNumber())
                .build()
            )
            .jvm(HostUsage.Jvm.builder()
                .name(ManagementFactory.getRuntimeMXBean().getVmName())
                .vendor(ManagementFactory.getRuntimeMXBean().getVmVendor())
                .version(ManagementFactory.getRuntimeMXBean().getVmVersion())
                .build()
            )
            .build();
    }

}



