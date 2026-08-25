package cn.arkmillion.bytebuddy;

import cn.arkmillion.core.accessor.PropertyAccessors;
import cn.arkmillion.core.accessor.ReflectivePropertyAccessor;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ByteBuddyAccessorProviderTest {

    public static class WithAccessors {

        private Long id;
        private String username;
        private boolean active;
        private int retries;
        private double balance;
        private BigDecimal amount;
        private LocalDateTime createdAt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }

        public int getRetries() {
            return retries;
        }

        public void setRetries(int retries) {
            this.retries = retries;
        }

        public double getBalance() {
            return balance;
        }

        public void setBalance(double balance) {
            this.balance = balance;
        }

        public BigDecimal getAmount() {
            return amount;
        }

        public void setAmount(BigDecimal amount) {
            this.amount = amount;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }
    }

    public static class WithoutAccessors {

        private String value;
    }

    private static Object read(Object bean, String property) throws Exception {
        Field f = bean.getClass().getDeclaredField(property);
        return PropertyAccessors.forField(f).get(bean);
    }

    private static void write(Object bean, String property, Object value) throws Exception {
        Field f = bean.getClass().getDeclaredField(property);
        PropertyAccessors.forField(f).set(bean, value);
    }

    @Test
    void generatedAccessorRoundTripsAllTypes() throws Exception {
        WithAccessors bean = new WithAccessors();

        write(bean, "id", 42L);
        write(bean, "username", "alice");
        write(bean, "active", Boolean.TRUE);
        write(bean, "retries", 7);
        write(bean, "balance", 99.5d);
        write(bean, "amount", new BigDecimal("123.45"));

        assertEquals(42L, read(bean, "id"));
        assertEquals("alice", read(bean, "username"));
        assertEquals(Boolean.TRUE, read(bean, "active"));
        assertEquals(7, read(bean, "retries"));
        assertEquals(99.5d, read(bean, "balance"));
        assertEquals(new BigDecimal("123.45"), read(bean, "amount"));

        LocalDateTime now = LocalDateTime.of(2026, 8, 25, 12, 0);
        write(bean, "createdAt", now);
        assertEquals(now, read(bean, "createdAt"));

        Field f = WithAccessors.class.getDeclaredField("username");
        assertFalse(PropertyAccessors.forField(f) instanceof ReflectivePropertyAccessor);
    }

    @Test
    void missingAccessorsFallBackToReflection() throws Exception {
        Field f = WithoutAccessors.class.getDeclaredField("value");
        assertTrue(PropertyAccessors.forField(f) instanceof ReflectivePropertyAccessor);

        WithoutAccessors bean = new WithoutAccessors();
        PropertyAccessors.forField(f).set(bean, "fallback");
        assertEquals("fallback", PropertyAccessors.forField(f).get(bean));
    }

    @Test
    void accessorsAreCachedPerField() throws Exception {
        Field f1 = WithAccessors.class.getDeclaredField("username");
        Field f2 = WithAccessors.class.getDeclaredField("username");
        assertTrue(PropertyAccessors.forField(f1) == PropertyAccessors.forField(f2));
    }
}
