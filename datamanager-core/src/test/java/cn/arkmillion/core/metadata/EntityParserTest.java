package cn.arkmillion.core.metadata;

import cn.arkmillion.core.annotation.AutoIncrement;
import cn.arkmillion.core.annotation.Column;
import cn.arkmillion.core.annotation.Id;
import cn.arkmillion.core.annotation.Index;
import cn.arkmillion.core.annotation.Table;
import cn.arkmillion.core.enums.DataType;
import cn.arkmillion.core.enums.GenerationType;
import cn.arkmillion.core.enums.IndexType;
import cn.arkmillion.core.exception.DataManagerException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityParserTest {

    @Table(name = "t_user", comment = "test user")
    static class TestUser {

        @Id(strategy = GenerationType.AUTO)
        @AutoIncrement
        private Long id;

        @Column(name = "user_name", length = 64, nullable = false, comment = "name")
        private String username;

        @Column(name = "email", length = 128, unique = true)
        @Index(type = IndexType.UNIQUE, name = "uk_email")
        private String email;

        @Column(type = DataType.DECIMAL, precision = 18, scale = 2)
        private BigDecimal balance;

        @Column(name = "created_at")
        private LocalDateTime createdAt;

        @Column(name = "birthday")
        private LocalDate birthday;

        private transient String ignoredTransient;

        public Long getId() {
            return id;
        }
    }

    static class NotAnnotated {
        private String field;
    }

    @Test
    void parsesTableNameAndComment() {
        SchemaDefinition def = EntityParser.parse(TestUser.class);
        assertEquals("t_user", def.getTableName());
        assertEquals("test user", def.getComment());
        assertFalse(def.isDocument());
    }

    @Test
    void resolvesColumnsAndSnakeCase() {
        SchemaDefinition def = EntityParser.parse(TestUser.class);
        assertEquals(6, def.getColumns().size());
        ColumnMetadata username = def.findColumnByProperty("username");
        assertNotNull(username);
        assertEquals("user_name", username.getColumnName());
        assertFalse(username.isNullable());
        assertEquals(64, username.getLength());
        assertEquals(DataType.VARCHAR, username.getDataType());
    }

    @Test
    void detectsIdAndAutoIncrement() {
        SchemaDefinition def = EntityParser.parse(TestUser.class);
        ColumnMetadata id = def.getIdColumn();
        assertNotNull(id);
        assertTrue(id.isAutoIncrement());
        assertEquals(GenerationType.IDENTITY, id.getGenerationType());
        assertNull(def.findColumnByProperty("ignoredTransient"));
    }

    @Test
    void collectsUniqueIndex() {
        SchemaDefinition def = EntityParser.parse(TestUser.class);
        boolean hasUkEmail = def.getIndexes().stream()
                .anyMatch(i -> "uk_email".equals(i.getName()) && i.isUnique());
        assertTrue(hasUkEmail);
    }

    @Test
    void infersDataTypes() {
        SchemaDefinition def = EntityParser.parse(TestUser.class);
        assertEquals(DataType.DECIMAL, def.findColumnByProperty("balance").getDataType());
        assertEquals(DataType.DATETIME, def.findColumnByProperty("createdAt").getDataType());
        assertEquals(DataType.DATE, def.findColumnByProperty("birthday").getDataType());
        assertEquals(DataType.BIGINT, def.findColumnByProperty("id").getDataType());
    }

    @Test
    void rejectsUnannotatedEntity() {
        assertThrows(DataManagerException.class, () -> EntityParser.parse(NotAnnotated.class));
    }

    @Test
    void camelToSnake() {
        assertEquals("user_name", EntityParser.camelToSnake("userName"));
        assertEquals("created_at", EntityParser.camelToSnake("createdAt"));
        assertEquals("id", EntityParser.camelToSnake("id"));
        assertEquals("sys_user", EntityParser.camelToSnake("SysUser"));
        assertEquals("already_snake", EntityParser.camelToSnake("already_snake"));
    }

    @Test
    void cachesDefinitions() {
        SchemaDefinition a = EntityParser.parse(TestUser.class);
        SchemaDefinition b = EntityParser.parse(TestUser.class);
        assertTrue(a == b);
    }

    @Test
    void insertableColumnsExcludeIdentityId() {
        SchemaDefinition def = EntityParser.parse(TestUser.class);
        assertTrue(def.getInsertableColumns().stream().noneMatch(ColumnMetadata::isPrimaryId));
    }
}
