package cn.arkmillion.core.condition;

import cn.arkmillion.core.condition.Criterion.Connector;
import cn.arkmillion.core.condition.Criterion.Kind;
import cn.arkmillion.core.enums.Operator;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionTest {

    @Test
    void simpleWhereChain() {
        Condition c = Condition.where("name").eq("alice").build();
        assertEquals(1, c.getCriteria().size());
        Criterion leaf = c.getCriteria().get(0);
        assertEquals(Kind.LEAF, leaf.getKind());
        assertEquals("name", leaf.getField());
        assertEquals(Operator.EQ, leaf.getOperator());
        assertEquals("alice", leaf.getValue());
        assertFalse(c.isEmpty());
    }

    @Test
    void multipleFieldsDefaultAnd() {
        Condition c = Condition.where("a").eq(1).and("b").gt(2).or("c").lt(3).build();
        List<Criterion> criteria = c.getCriteria();
        assertEquals(3, criteria.size());
        assertEquals(Connector.AND, criteria.get(1).getConnector());
        assertEquals(Connector.OR, criteria.get(2).getConnector());
    }

    @Test
    void betweenAddsTwoValues() {
        Condition c = Condition.where("age").between(18, 65).build();
        Criterion leaf = c.getCriteria().get(0);
        Object[] range = (Object[]) leaf.getValue();
        assertEquals(18, range[0]);
        assertEquals(65, range[1]);
    }

    @Test
    void inOperatorHoldsCollection() {
        List<Integer> ids = Arrays.asList(1, 2, 3);
        Condition c = Condition.where("id").in(ids).build();
        assertEquals(ids, c.getCriteria().get(0).getValue());
    }

    @Test
    void mergedConditionBecomesGroup() {
        Condition inner = Condition.where("x").eq(9).build();
        Condition outer = Condition.where("y").eq(1).or(inner).build();
        assertEquals(2, outer.getCriteria().size());
        Criterion group = outer.getCriteria().get(1);
        assertEquals(Kind.GROUP, group.getKind());
        assertEquals(Connector.OR, group.getConnector());
        assertEquals(1, group.getChildren().size());
    }

    @Test
    void orderByAppendsOrders() {
        Condition c = Condition.where("a").eq(1).orderBy("created_at", false);
        assertEquals(1, c.getOrders().size());
        assertFalse(c.getOrders().get(0).isAscending());
    }

    @Test
    void builderIsACondition() {
        Condition.ConditionBuilder builder = Condition.where("z").isNull();
        Condition asCondition = builder;
        assertEquals(1, asCondition.getCriteria().size());
        assertTrue(builder.build() == builder);
    }

    @Test
    void emptyCondition() {
        assertTrue(Condition.empty().isEmpty());
    }
}
