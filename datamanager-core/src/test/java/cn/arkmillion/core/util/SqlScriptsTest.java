package cn.arkmillion.core.util;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlScriptsTest {

    @Test
    void splitsOnSemicolons() {
        String script = "CREATE TABLE a(id INT);\nINSERT INTO a VALUES (1);\n";
        List<String> statements = SqlScripts.splitStatements(script);
        assertEquals(2, statements.size());
        assertEquals("CREATE TABLE a(id INT)", statements.get(0));
        assertEquals("INSERT INTO a VALUES (1)", statements.get(1));
    }

    @Test
    void ignoresCommentsAndBlankStatements() {
        String script = "-- line comment\nSELECT 1;\n/* block\ncomment */\n;SELECT 'semi;colon';\n";
        List<String> statements = SqlScripts.splitStatements(script);
        assertEquals(2, statements.size());
        assertEquals("SELECT 1", statements.get(0));
        assertEquals("SELECT 'semi;colon'", statements.get(1));
    }

    @Test
    void semicolonInsideQuotesPreserved() {
        String script = "INSERT INTO t VALUES ('a;b', \"c;d\");SELECT 2;";
        List<String> statements = SqlScripts.splitStatements(script);
        assertEquals(2, statements.size());
        assertEquals("INSERT INTO t VALUES ('a;b', \"c;d\")", statements.get(0));
    }

    @Test
    void escapedSingleQuoteInsideLiteral() {
        String script = "INSERT INTO t VALUES ('it''s ok');";
        List<String> statements = SqlScripts.splitStatements(script);
        assertEquals(1, statements.size());
        assertEquals("INSERT INTO t VALUES ('it''s ok')", statements.get(0));
    }

    @Test
    void appliesPlaceholders() {
        Map<String, String> values = new HashMap<>();
        values.put("SCHEMA", "app");
        values.put("TBL", "users");
        String result = SqlScripts.applyPlaceholders(
                "CREATE TABLE ${SCHEMA}.${TBL}(id INT); SELECT '${SCHEMA}';",
                values);
        assertEquals("CREATE TABLE app.users(id INT); SELECT 'app';", result);
    }

    @Test
    void missingPlaceholderFailsFast() {
        assertThrows(cn.arkmillion.core.exception.DataManagerException.class,
                () -> SqlScripts.applyPlaceholders("SELECT ${MISSING};",
                        java.util.Collections.emptyMap()));
    }
}
