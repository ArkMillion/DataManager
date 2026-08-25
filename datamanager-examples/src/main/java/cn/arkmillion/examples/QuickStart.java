package cn.arkmillion.examples;

import cn.arkmillion.core.condition.Condition;
import cn.arkmillion.core.config.DataManagerConfig;
import cn.arkmillion.core.db.DataManager;
import cn.arkmillion.core.db.RelationalDB;
import cn.arkmillion.core.enums.SyncMode;
import cn.arkmillion.core.exception.DataManagerException;
import cn.arkmillion.core.factory.DataManagerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static cn.arkmillion.core.condition.Condition.where;

public final class QuickStart {

    private QuickStart() {
    }

    public static Path run(Path sqliteFile) throws Exception {
        DataManagerConfig config = DataManagerConfig.builder()
                .sqlite(sqliteFile.toString())
                .poolSize(1)
                .busyTimeout(5_000)
                .build()
                .build();

        try (DataManager dm = DataManagerFactory.create(config)) {
            RelationalDB db = dm.getRelationalDB("sqlite");

            db.syncSchema(User.class, SyncMode.CREATE);
            db.syncSchema(Order.class, SyncMode.CREATE);

            User user = new User("alice", "alice@corp.com");
            db.insert(user);

            User found = db.selectOne(User.class,
                    Condition.where("email").eq("alice@corp.com").build());

            db.beginTransaction();
            try {
                Order order = new Order(user.getId(), 100.00);
                db.insert(order);
                Map<String, Object> updates = new HashMap<>();
                updates.put("status", 2);
                db.update(User.class, where("id").eq(user.getId()).build(), updates);
                db.commit();
            } catch (Exception e) {
                db.rollback();
                throw e;
            }

            List<Order> orders = db.select(Order.class, where("user_id").eq(user.getId()).build());
            long orderCount = db.count(Order.class, where("user_id").eq(user.getId()).build());

            System.out.println("User: id=" + found.getId() + ", name=" + found.getUsername()
                    + ", status=" + found.getStatus());
            System.out.println("Orders for user: " + orders.size() + " (count=" + orderCount + ")");

            Map<String, String> placeholders = new HashMap<>();
            placeholders.put("EMAIL_SUFFIX", "@corp.com");
            db.executeSqlFile("classpath:db/seed_users.sql", placeholders);

            long corpUsers = db.count(User.class, where("email").like("%@corp.com").build());
            System.out.println("Corp users after seed file: " + corpUsers);
            return sqliteFile;
        } catch (DataManagerException e) {
            System.err.println("DataManager failure: " + e.getMessage());
            throw e;
        }
    }

    public static void main(String[] args) throws Exception {
        Path tempFile = Files.createTempFile("datamanager-quickstart", ".db");
        Files.deleteIfExists(tempFile);
        run(tempFile);
        Files.deleteIfExists(tempFile);
    }
}
