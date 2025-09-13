package io.kestra.core.plugins;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Enumeration;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.zip.CRC32;

@AllArgsConstructor
@Getter
@EqualsAndHashCode
@ToString
public class ExternalPlugin {
    private final URL location;
    private final URL[] resources;
    private volatile Long crc32; // lazy-val
    
    public ExternalPlugin(URL location, URL[] resources) {
        this.location = location;
        this.resources = resources;
    }
    
    public Long getCrc32() {
        if (this.crc32 == null) {
            synchronized (this) {
                if (this.crc32 == null) {
                    this.crc32 = computeJarCrc32(location);
                }
            }
        }
        return crc32;
    }
    
    /**
     * Compute a CRC32 of the JAR File without reading the whole file
     *
     * @param location of the JAR File.
     * @return the CRC32 of {@code -1} if the checksum can't be computed.
     */
    private static long computeJarCrc32(final URL location) {
        CRC32 crc = new CRC32();
        try (JarFile jar = new JarFile(location.toURI().getPath(), false)) {
            Enumeration<JarEntry> entries = jar.entries();
            byte[] buffer = new byte[Long.BYTES]; // reusable buffer to avoid re-allocation
            
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                crc.update(entry.getName().getBytes(StandardCharsets.UTF_8));
                updateCrc32WithLong(crc, buffer, entry.getSize());
                updateCrc32WithLong(crc, buffer, entry.getCrc());
            }
            
            return crc.getValue();
        } catch (Exception e) {
            return -1;
        }
    }
    
    private static void updateCrc32WithLong(CRC32 crc32, byte[] reusable, long val) {
        // fast long -> byte conversion
        reusable[0] = (byte) (val >>> 56);
        reusable[1] = (byte) (val >>> 48);
        reusable[2] = (byte) (val >>> 40);
        reusable[3] = (byte) (val >>> 32);
        reusable[4] = (byte) (val >>> 24);
        reusable[5] = (byte) (val >>> 16);
        reusable[6] = (byte) (val >>> 8);
        reusable[7] = (byte) val;
        crc32.update(reusable);;
    }
}
