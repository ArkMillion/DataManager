package cn.arkmillion.core.jdbc;

import cn.arkmillion.core.exception.DataManagerException;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.sql.Blob;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

public final class TypeBinders {

    private static final DateTimeFormatter SQLITE_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter SQLITE_DATETIME_MS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private TypeBinders() {
    }

    public static void bind(PreparedStatement ps, int index, Object value) {
        try {
            if (value == null) {
                ps.setObject(index, null);
                return;
            }
            if (value instanceof LocalDateTime) {
                ps.setTimestamp(index, Timestamp.valueOf((LocalDateTime) value));
            } else if (value instanceof LocalDate) {
                ps.setDate(index, Date.valueOf((LocalDate) value));
            } else if (value instanceof LocalTime) {
                ps.setTime(index, Time.valueOf((LocalTime) value));
            } else if (value instanceof Instant) {
                ps.setTimestamp(index, Timestamp.from((Instant) value));
            } else if (value instanceof java.util.Date && !(value instanceof java.sql.Date) && !(value instanceof Timestamp)) {
                ps.setTimestamp(index, new Timestamp(((java.util.Date) value).getTime()));
            } else if (value instanceof Enum<?>) {
                ps.setString(index, ((Enum<?>) value).name());
            } else if (value instanceof byte[]) {
                ps.setBytes(index, (byte[]) value);
            } else {
                ps.setObject(index, value);
            }
        } catch (SQLException e) {
            throw new DataManagerException("Failed to bind parameter at index " + index, e);
        }
    }

    public static Object convert(Object raw, Class<?> target) {
        if (raw == null) {
            return null;
        }
        if (target == null || target.isInstance(raw)) {
            return raw;
        }
        if (target == String.class) {
            return raw.toString();
        }
        if (target == Long.class || target == long.class) {
            return toLong(raw);
        }
        if (target == Integer.class || target == int.class) {
            Number n = toNumber(raw);
            return n == null ? null : n.intValue();
        }
        if (target == Short.class || target == short.class) {
            Number n = toNumber(raw);
            return n == null ? null : n.shortValue();
        }
        if (target == Byte.class || target == byte.class) {
            Number n = toNumber(raw);
            return n == null ? null : n.byteValue();
        }
        if (target == Double.class || target == double.class) {
            Number n = toNumber(raw);
            return n == null ? null : n.doubleValue();
        }
        if (target == Float.class || target == float.class) {
            Number n = toNumber(raw);
            return n == null ? null : n.floatValue();
        }
        if (target == BigDecimal.class) {
            return toBigDecimal(raw);
        }
        if (target == Boolean.class || target == boolean.class) {
            return toBoolean(raw);
        }
        if (target == BigInteger.class) {
            BigDecimal bd = toBigDecimal(raw);
            return bd == null ? null : bd.toBigInteger();
        }
        if (target == LocalDateTime.class) {
            return toLocalDateTime(raw);
        }
        if (target == LocalDate.class) {
            return toLocalDate(raw);
        }
        if (target == LocalTime.class) {
            return toLocalTime(raw);
        }
        if (target == Instant.class) {
            LocalDateTime ldt = toLocalDateTime(raw);
            return ldt != null ? ldt.toInstant(java.time.ZoneOffset.UTC) : null;
        }
        if (target == java.util.Date.class) {
            Timestamp ts = asTimestamp(raw);
            return ts != null ? new java.util.Date(ts.getTime()) : null;
        }
        if (target == java.sql.Date.class) {
            LocalDate ld = toLocalDate(raw);
            return ld != null ? Date.valueOf(ld) : null;
        }
        if (target.isEnum()) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object e = Enum.valueOf((Class<? extends Enum>) target, raw.toString());
            return e;
        }
        if (target == byte[].class) {
            return toBytes(raw);
        }
        throw new DataManagerException("Unsupported mapping from " + raw.getClass().getName() + " to " + target.getName());
    }

    private static Number toNumber(Object raw) {
        if (raw instanceof Number) {
            return (Number) raw;
        }
        if (raw instanceof String) {
            String s = ((String) raw).trim();
            if (s.isEmpty()) {
                return null;
            }
            try {
                return Long.valueOf(s);
            } catch (NumberFormatException e) {
                return Double.valueOf(s);
            }
        }
        if (raw instanceof Boolean) {
            return ((Boolean) raw) ? 1L : 0L;
        }
        throw new DataManagerException("Cannot convert " + raw.getClass().getName() + " to number");
    }

    private static Long toLong(Object raw) {
        Number n = toNumber(raw);
        return n == null ? null : n.longValue();
    }

    private static BigDecimal toBigDecimal(Object raw) {
        if (raw instanceof BigDecimal) {
            return (BigDecimal) raw;
        }
        if (raw instanceof BigInteger) {
            return new BigDecimal((BigInteger) raw);
        }
        if (raw instanceof Number) {
            return new BigDecimal(raw.toString());
        }
        if (raw instanceof String) {
            String s = ((String) raw).trim();
            return s.isEmpty() ? null : new BigDecimal(s);
        }
        throw new DataManagerException("Cannot convert " + raw.getClass().getName() + " to BigDecimal");
    }

    private static Boolean toBoolean(Object raw) {
        if (raw instanceof Boolean) {
            return (Boolean) raw;
        }
        if (raw instanceof Number) {
            return ((Number) raw).intValue() != 0;
        }
        if (raw instanceof String) {
            String s = ((String) raw).trim();
            if ("1".equals(s) || "true".equalsIgnoreCase(s)) {
                return Boolean.TRUE;
            }
            if ("0".equals(s) || "false".equalsIgnoreCase(s) || s.isEmpty()) {
                return Boolean.FALSE;
            }
        }
        throw new DataManagerException("Cannot convert " + raw.getClass().getName() + " to Boolean");
    }

    private static LocalDateTime toLocalDateTime(Object raw) {
        if (raw instanceof LocalDateTime) {
            return (LocalDateTime) raw;
        }
        Timestamp ts = asTimestamp(raw);
        if (ts != null) {
            return ts.toLocalDateTime();
        }
        if (raw instanceof java.sql.Date) {
            return ((java.sql.Date) raw).toLocalDate().atStartOfDay();
        }
        if (raw instanceof String) {
            String s = ((String) raw).trim();
            if (s.isEmpty()) {
                return null;
            }
            if (isAllDigits(s)) {
                return new Timestamp(Long.parseLong(s)).toLocalDateTime();
            }
            if (s.contains("T")) {
                return LocalDateTime.parse(s);
            }
            if (s.length() > 19 && s.charAt(10) == ' ') {
                return LocalDateTime.parse(s, SQLITE_DATETIME_MS);
            }
            if (s.length() == 19) {
                return LocalDateTime.parse(s, SQLITE_DATETIME);
            }
            if (s.length() == 10) {
                return LocalDate.parse(s).atStartOfDay();
            }
            return LocalDateTime.parse(s);
        }
        if (raw instanceof Number) {
            return new Timestamp(((Number) raw).longValue()).toLocalDateTime();
        }
        throw new DataManagerException("Cannot convert " + raw.getClass().getName() + " to LocalDateTime");
    }

    private static LocalDate toLocalDate(Object raw) {
        if (raw instanceof LocalDate) {
            return (LocalDate) raw;
        }
        if (raw instanceof java.sql.Date) {
            return ((java.sql.Date) raw).toLocalDate();
        }
        Timestamp ts = asTimestamp(raw);
        if (ts != null) {
            return ts.toLocalDateTime().toLocalDate();
        }
        if (raw instanceof String) {
            String s = ((String) raw).trim();
            if (s.isEmpty()) {
                return null;
            }
            if (isAllDigits(s)) {
                return new Timestamp(Long.parseLong(s)).toLocalDateTime().toLocalDate();
            }
            if (s.length() >= 10) {
                return LocalDate.parse(s.substring(0, 10));
            }
            return LocalDate.parse(s);
        }
        throw new DataManagerException("Cannot convert " + raw.getClass().getName() + " to LocalDate");
    }

    private static LocalTime toLocalTime(Object raw) {
        if (raw instanceof LocalTime) {
            return (LocalTime) raw;
        }
        if (raw instanceof Time) {
            return ((Time) raw).toLocalTime();
        }
        if (raw instanceof String) {
            String s = ((String) raw).trim();
            if (s.isEmpty()) {
                return null;
            }
            return s.length() == 8 ? LocalTime.parse(s) : LocalTime.parse(s, SQLITE_DATETIME);
        }
        throw new DataManagerException("Cannot convert " + raw.getClass().getName() + " to LocalTime");
    }

    private static boolean isAllDigits(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (!Character.isDigit(s.charAt(i))) {
                return false;
            }
        }
        return !s.isEmpty();
    }

    private static Timestamp asTimestamp(Object raw) {
        if (raw instanceof Timestamp) {
            return (Timestamp) raw;
        }
        return null;
    }

    private static byte[] toBytes(Object raw) {
        if (raw instanceof byte[]) {
            return (byte[]) raw;
        }
        if (raw instanceof Blob) {
            try {
                Blob blob = (Blob) raw;
                return blob.getBytes(1, (int) blob.length());
            } catch (SQLException e) {
                throw new DataManagerException("Failed to read BLOB", e);
            }
        }
        if (raw instanceof String) {
            return ((String) raw).getBytes(StandardCharsets.UTF_8);
        }
        throw new DataManagerException("Cannot convert " + raw.getClass().getName() + " to byte[]");
    }

    public static <T> T mapScalar(ResultSet rs, Class<T> clazz) throws SQLException {
        Object raw = rs.getObject(1);
        Object converted = convert(raw, clazz);
        return clazz.cast(converted);
    }
}
