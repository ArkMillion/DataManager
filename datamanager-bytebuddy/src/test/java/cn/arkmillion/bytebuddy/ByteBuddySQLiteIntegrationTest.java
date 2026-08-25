package cn.arkmillion.bytebuddy;

import cn.arkmillion.core.annotation.Column;
import cn.arkmillion.core.annotation.Id;
import cn.arkmillion.core.config.SQLiteConfig;
import cn.arkmillion.core.enums.GenerationType;
import cn.arkmillion.core.enums.SyncMode;
import cn.arkmillion.core.jdbc.ConnectionPool;
import cn.arkmillion.sqlite.SQLiteAdapter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static cn.arkmillion.core.condition.Condition.where;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ByteBuddySQLiteIntegrationTest {

    @cn.arkmillion.core.annotation.Table(name = "bb_order")
    public static class Order {

        @Id(strategy = GenerationType.AUTO)
        private Long id;

        @Column(name = "user_name", nullable = false)
        private String userName;

        @Column(name = "total")
        private Double total;

        @Column(name = "paid")
        private Boolean paid;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public Double getTotal() {
            return total;
        }

        public void setTotal(Double total) {
            this.total = total;
        }

        public Boolean getPaid() {
            return paid;
        }

        public void setPaid(Boolean paid) {
            this.paid = paid;
        }
    }

    private Path dbFile;
    private SQLiteAdapter db;

    @BeforeEach
    void setUp() throws Exception {
        dbFile = Files.createTempFile("bytebuddy-sqlite", ".db");
        Files.deleteIfExists(dbFile);
        db = new SQLiteAdapter(new SQLiteConfig(dbFile.toString()));
    }

    @AfterEach
    void tearDown() {
        if (db != null) {
            db.close();
        }
        ConnectionPool.shutdownAll();
    }

    @Test
    void fullCrudStackRunsThroughGeneratedAccessors() {
        db.syncSchema(Order.class, SyncMode.CREATE);

        List<Order> orders = new ArrayList<>();
        for (int i = 1; i <= 5; i++) {
            Order o = new Order();
            o.setUserName("user" + i);
            o.setTotal(10.0 * i);
            o.setPaid(i % 2 == 0);
            orders.add(o);
        }
        db.batchInsert(orders);

        Order first = db.selectOne(Order.class, where("userName").eq("user1").build());
        assertEquals(10.0, first.getTotal());
        assertEquals(Boolean.FALSE, first.getPaid());
        assertTrue(first.getId() != null && first.getId() > 0);

        first.setTotal(99.9);
        first.setPaid(true);
        assertEquals(1, db.update(first));

        Order reloaded = db.selectById(Order.class, first.getId());
        assertEquals(99.9, reloaded.getTotal());
        assertEquals(Boolean.TRUE, reloaded.getPaid());

        assertEquals(2L, db.count(Order.class, where("paid").eq(false).build()));
        assertEquals(3L, db.count(Order.class, where("paid").eq(true).build()));
    }
}
