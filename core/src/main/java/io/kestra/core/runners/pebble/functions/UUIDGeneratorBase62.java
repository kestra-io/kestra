package io.kestra.core.runners.pebble.functions;

import io.pebbletemplates.pebble.extension.Function;
import io.pebbletemplates.pebble.template.EvaluationContext;
import io.pebbletemplates.pebble.template.PebbleTemplate;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class UUIDGeneratorBase62 implements Function {

  private static final String BASE62 =
      "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";
  private static final BigInteger BASE = BigInteger.valueOf(62);
  private static final BigInteger LONG_MAX = BigInteger.ONE.shiftLeft(64); // 2^64
  private static final java.util.function.Function<BigInteger, BigInteger> toUnsigned =
      value -> value.signum() < 0 ? value.add(LONG_MAX) : value;

  @Override
  public Object execute(
      Map<String, Object> args, PebbleTemplate self, EvaluationContext context, int lineNumber) {
    UUID uuid = UUID.randomUUID();
    BigInteger unsignedLow = toUnsigned.apply(BigInteger.valueOf(uuid.getLeastSignificantBits()));
    BigInteger unsignedHigh = toUnsigned.apply(BigInteger.valueOf(uuid.getMostSignificantBits()));
    BigInteger uuidBigInteger = unsignedLow.add(unsignedHigh.multiply(LONG_MAX));
    return encodeBase62(uuidBigInteger);
  }

  private String encodeBase62(BigInteger number) {
    StringBuilder result = new StringBuilder();
    while (number.compareTo(BigInteger.ZERO) > 0) {
      BigInteger[] divMod = number.divideAndRemainder(BASE);
      number = divMod[0];
      int digit = divMod[1].intValue();
      result.insert(0, BASE62.charAt(digit));
    }
    return (result.isEmpty()) ? BASE62.substring(0, 1) : result.toString();
  }

  @Override
  public List<String> getArgumentNames() {
    return List.of();
  }
}
