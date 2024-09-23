package io.kestra.core.utils;

import com.google.common.hash.Hashing;
import org.apache.commons.lang3.RandomStringUtils;

import java.nio.charset.StandardCharsets;

public class AuthUtils {
    public static String encodePassword(String salt, String password)  {
        return Hashing
            .sha512()
            .hashString(salt + "|" + password, StandardCharsets.UTF_8)
            .toString();
    }

    public static String generateSalt() {
        return RandomStringUtils.secure().next(32, true, true);
    }
}
