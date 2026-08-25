package cn.arkmillion.mongodb;

import cn.arkmillion.core.condition.Order;
import cn.arkmillion.core.condition.PageParam;
import cn.arkmillion.core.condition.PageResult;
import cn.arkmillion.core.config.DataManagerConfig;
import cn.arkmillion.core.config.MongoConfig;
import cn.arkmillion.core.config.PlaceholderResolver;
import cn.arkmillion.core.db.DocumentDB;
import cn.arkmillion.core.enums.IndexDirection;
import cn.arkmillion.core.enums.Operator;
import cn.arkmillion.core.enums.SyncMode;
import cn.arkmillion.core.exception.DataManagerException;
import cn.arkmillion.core.filter.AggregationStage;
import cn.arkmillion.core.filter.Filter;
import cn.arkmillion.core.filter.FilterCriterion;
import cn.arkmillion.core.filter.Update;
import cn.arkmillion.core.metadata.ColumnMetadata;
import cn.arkmillion.core.metadata.EntityParser;
import cn.arkmillion.core.metadata.SchemaDefinition;
import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Aggregates;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.IndexOptions;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Updates;
import com.mongodb.client.result.DeleteResult;
import com.mongodb.client.result.UpdateResult;
import org.bson.BsonDocument;
import org.bson.Document;
import org.bson.conversions.Bson;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

public class MongoAdapter implements DocumentDB {

    private final MongoClient client;
    private final MongoConfig config;
    private final MongoMapper mapper = new MongoMapper();
    private final Map<Class<?>, SchemaDefinition> definitions = new ConcurrentHashMap<>();

    public MongoAdapter(MongoConfig config) {
        this.config = config;
        String uri = PlaceholderResolver.resolve(config.getUri());
        try {
            MongoClientSettings.Builder builder = MongoClientSettings.builder()
                    .applyConnectionString(new ConnectionString(uri));
            if (config.getApplicationName() != null && !config.getApplicationName().isEmpty()) {
                builder.applicationName(config.getApplicationName());
            }
            this.client = MongoClients.create(builder.build());
        } catch (RuntimeException e) {
            throw new DataManagerException("Failed to create MongoDB client for URI: " + sanitize(uri), e);
        }
    }

    private static String sanitize(String uri) {
        int at = uri.lastIndexOf('@');
        return at > 0 ? "mongodb://<credentials-hidden>@" + uri.substring(at + 1) : uri;
    }

    private SchemaDefinition definition(Class<?> clazz) {
        return definitions.computeIfAbsent(clazz, mapper::parse);
    }

    private <T> MongoCollection<Document> collection(Class<T> clazz, SchemaDefinition def) {
        String databaseName = firstNonEmpty(config.getDatabase(), def.getDatabaseName());
        if (databaseName == null || databaseName.isEmpty()) {
            throw new DataManagerException("No database resolved for entity " + clazz.getName()
                    + ". Set database in @Document or in MongoConfig.");
        }
        MongoDatabase db = client.getDatabase(databaseName);
        return db.getCollection(def.getCollectionName());
    }

    private static String firstNonEmpty(String a, String b) {
        return a != null && !a.isEmpty() ? a : b;
    }

    @Override
    public <T> void insert(T document) {
        Class<?> clazz = document.getClass();
        SchemaDefinition def = definition(clazz);
        Document bson = mapper.toBson(document, def);
        collection(clazz, def).insertOne(bson);
        mapper.writeBackId(document, def, bson);
    }

    @Override
    public <T> void insertMany(List<T> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        SchemaDefinition def = definition(documents.get(0).getClass());
        MongoCollection<Document> col = collection(documents.get(0).getClass(), def);
        List<Document> batch = new ArrayList<>(documents.size());
        for (T doc : documents) {
            batch.add(mapper.toBson(doc, def));
        }
        col.insertMany(batch);
        for (int i = 0; i < documents.size(); i++) {
            mapper.writeBackId(documents.get(i), def, batch.get(i));
        }
    }

    @Override
    public <T> List<T> find(Class<T> clazz, Filter filter) {
        SchemaDefinition def = definition(clazz);
        Iterable<Document> docs = collection(clazz, def).find(toBsonFilter(filter));
        List<T> result = new ArrayList<>();
        for (Document doc : docs) {
            result.add(mapper.fromBson(clazz, def, doc));
        }
        return result;
    }

    @Override
    public <T> T findOne(Class<T> clazz, Filter filter) {
        SchemaDefinition def = definition(clazz);
        Document doc = collection(clazz, def).find(toBsonFilter(filter)).first();
        return doc == null ? null : mapper.fromBson(clazz, def, doc);
    }

    @Override
    public <T> long update(Class<T> clazz, Filter filter, Update update) {
        SchemaDefinition def = definition(clazz);
        UpdateResult result = collection(clazz, def).updateMany(toBsonFilter(filter), toBsonUpdate(update));
        return result.getModifiedCount();
    }

    @Override
    public <T> long updateOne(Class<T> clazz, Filter filter, Update update) {
        SchemaDefinition def = definition(clazz);
        UpdateResult result = collection(clazz, def).updateOne(toBsonFilter(filter), toBsonUpdate(update));
        return result.getModifiedCount();
    }

    @Override
    public <T> long delete(Class<T> clazz, Filter filter) {
        SchemaDefinition def = definition(clazz);
        DeleteResult result = collection(clazz, def).deleteMany(toBsonFilter(filter));
        return result.getDeletedCount();
    }

    @Override
    public <T> long deleteOne(Class<T> clazz, Filter filter) {
        SchemaDefinition def = definition(clazz);
        DeleteResult result = collection(clazz, def).deleteOne(toBsonFilter(filter));
        return result.getDeletedCount();
    }

    @Override
    public <T> long count(Class<T> clazz, Filter filter) {
        SchemaDefinition def = definition(clazz);
        return collection(clazz, def).countDocuments(toBsonFilter(filter));
    }

    @Override
    public <T> PageResult<T> findPage(Class<T> clazz, Filter filter, PageParam page) {
        SchemaDefinition def = definition(clazz);
        Bson bsonFilter = toBsonFilter(filter);
        MongoCollection<Document> col = collection(clazz, def);
        long total = col.countDocuments(bsonFilter);
        if (total == 0) {
            return PageResult.empty(page);
        }
        List<T> records = new ArrayList<>();
        for (Document doc : col.find(bsonFilter).skip(page.getOffset()).limit(page.getPageSize())) {
            records.add(mapper.fromBson(clazz, def, doc));
        }
        return PageResult.of(records, total, page);
    }

    @Override
    public <T> List<T> aggregate(Class<T> clazz, List<AggregationStage> stages) {
        SchemaDefinition def = definition(clazz);
        List<Bson> pipeline = new ArrayList<>();
        for (AggregationStage stage : stages) {
            pipeline.add(toBsonStage(stage));
        }
        List<T> result = new ArrayList<>();
        for (Document doc : collection(clazz, def).aggregate(pipeline)) {
            result.add(mapper.fromBson(clazz, def, doc));
        }
        return result;
    }

    @Override
    public <T> void syncSchema(Class<T> clazz, SyncMode mode) {
        SchemaDefinition def = definition(clazz);
        SyncMode effectiveMode = mode == SyncMode.UPDATE ? SyncMode.CREATE : mode;
        switch (effectiveMode) {
            case CREATE:
                ensureIndexes(clazz, def);
                break;
            case VALIDATE:
                String db = firstNonEmpty(config.getDatabase(), def.getDatabaseName());
                boolean exists = db != null
                        && client.getDatabase(db).listCollectionNames()
                        .into(new ArrayList<>()).contains(def.getCollectionName());
                if (!exists) {
                    throw new DataManagerException("Schema validation failed: collection '"
                            + def.getCollectionName() + "' does not exist");
                }
                break;
            case DROP_CREATE:
                String dbName = firstNonEmpty(config.getDatabase(), def.getDatabaseName());
                if (dbName != null) {
                    client.getDatabase(dbName).getCollection(def.getCollectionName()).drop();
                }
                break;
            default:
                throw new DataManagerException("Unsupported sync mode: " + mode);
        }
    }

    private void ensureIndexes(Class<?> clazz, SchemaDefinition def) {
        MongoCollection<Document> col = collection(clazz, def);
        for (ColumnMetadata c : def.getColumns()) {
            ColumnMetadata.MongoIndexSpec spec = c.getMongoIndexSpec();
            if (spec == null) {
                continue;
            }
            Bson keys = spec.getDirection() == IndexDirection.DESC
                    ? Indexes.descending(c.getPropertyName())
                    : Indexes.ascending(c.getPropertyName());
            IndexOptions options = new IndexOptions()
                    .unique(spec.isUnique())
                    .sparse(spec.isSparse())
                    .name(c.getPropertyName() + "_" + (spec.getDirection() == IndexDirection.DESC ? -1 : 1));
            col.createIndex(keys, options);
        }
    }

    private Bson toBsonStage(AggregationStage stage) {
        switch (stage.getKind()) {
            case MATCH:
                return Aggregates.match(toBsonFilter(stage.getFilter()));
            case SORT:
                Document sortDoc = new Document();
                for (Order o : stage.getOrders()) {
                    sortDoc.append(o.getField(), o.isAscending() ? 1 : -1);
                }
                return Aggregates.sort(sortDoc);
            case SKIP:
                return Aggregates.skip((int) stage.getNumber());
            case LIMIT:
                return Aggregates.limit((int) stage.getNumber());
            case COUNT:
                return Aggregates.count(stage.getFieldName());
            case GROUP: {
                Document groupDoc = new Document("_id", parseValue(stage.getGroupExpression()));
                for (Map.Entry<String, String> e : stage.getAccumulators().entrySet()) {
                    groupDoc.append(e.getKey(), parseValue(e.getValue()));
                }
                return new Document("$group", groupDoc);
            }
            case RAW:
                return BsonDocument.parse(stage.getRawJson());
            default:
                throw new DataManagerException("Unsupported aggregation stage: " + stage.getKind());
        }
    }

    private Object parseValue(String json) {
        if (json == null) {
            return null;
        }
        String trimmed = json.trim();
        try {
            return Document.parse("{v:" + trimmed + "}").get("v");
        } catch (RuntimeException e) {
            if (trimmed.startsWith("$") || trimmed.startsWith("'") || trimmed.startsWith("\"")) {
                trimmed = trimmed.replace("'", "\"");
                if (trimmed.startsWith("$")) {
                    trimmed = "\"" + trimmed + "\"";
                }
                return Document.parse("{v:" + trimmed + "}").get("v");
            }
            return trimmed;
        }
    }

    Bson toBsonFilter(Filter filter) {
        if (filter == null || filter.isEmpty()) {
            return Filters.empty();
        }
        List<Bson> groups = new ArrayList<>();
        List<Bson> currentGroup = new ArrayList<>();
        boolean groupIsOr = false;
        for (FilterCriterion criterion : filter.getCriteria()) {
            boolean or = criterion.getConnector() == FilterCriterion.Connector.OR;
            if (or && !currentGroup.isEmpty() && !groupIsOr) {
                groups.add(Filters.and(currentGroup.toArray(new Bson[0])));
                currentGroup = new ArrayList<>();
            }
            groupIsOr = or;
            currentGroup.add(criterionToBson(criterion));
        }
        if (!currentGroup.isEmpty()) {
            groups.add(groupIsOr
                    ? Filters.or(currentGroup.toArray(new Bson[0]))
                    : Filters.and(currentGroup.toArray(new Bson[0])));
        }
        if (groups.size() == 1) {
            return groups.get(0);
        }
        return Filters.and(groups.toArray(new Bson[0]));
    }

    private Bson criterionToBson(FilterCriterion criterion) {
        if (criterion.getKind() == FilterCriterion.Kind.GROUP) {
            List<Bson> children = new ArrayList<>();
            for (FilterCriterion child : criterion.getChildren()) {
                children.add(leafOrGroup(child));
            }
            if (children.isEmpty()) {
                return Filters.empty();
            }
            return criterion.getConnector() == FilterCriterion.Connector.OR
                    ? Filters.or(children.toArray(new Bson[0]))
                    : Filters.and(children.toArray(new Bson[0]));
        }
        return leafToBson(criterion.getField(), criterion.getOperator(), criterion.getValue());
    }

    private Bson leafOrGroup(FilterCriterion criterion) {
        return criterion.getKind() == FilterCriterion.Kind.GROUP
                ? criterionToBson(criterion)
                : leafToBson(criterion.getField(), criterion.getOperator(), criterion.getValue());
    }

    private Bson leafToBson(String field, Operator op, Object value) {
        switch (op) {
            case EQ:
                return Filters.eq(field, value);
            case NE:
                return Filters.ne(field, value);
            case GT:
                return Filters.gt(field, value);
            case GTE:
                return Filters.gte(field, value);
            case LT:
                return Filters.lt(field, value);
            case LTE:
                return Filters.lte(field, value);
            case LIKE:
                String literal = value == null ? "" : value.toString();
                return Filters.regex(field, ".*" + Pattern.quote(literal) + ".*");
            case IN:
                Collection<?> inValues = (Collection<?>) value;
                return Filters.in(field, inValues == null ? new ArrayList<>() : inValues);
            case NOT_IN:
                Collection<?> ninValues = (Collection<?>) value;
                return Filters.nin(field, ninValues == null ? new ArrayList<>() : ninValues);
            case BETWEEN: {
                Object[] range = (Object[]) value;
                return Filters.and(Filters.gte(field, range[0]), Filters.lte(field, range[1]));
            }
            case IS_NULL:
                return Filters.eq(field, null);
            case IS_NOT_NULL:
                return Filters.ne(field, null);
            default:
                throw new DataManagerException("Unsupported operator for MongoDB: " + op);
        }
    }

    private Bson toBsonUpdate(Update update) {
        if (update == null || update.isEmpty()) {
            throw new DataManagerException("Refusing empty update");
        }
        List<Bson> parts = new ArrayList<>();
        List<Bson> others = new ArrayList<>();
        for (Update.Entry entry : update.getEntries()) {
            switch (entry.getOp()) {
                case SET:
                    parts.add(Updates.set(entry.getKey(), entry.getValue()));
                    break;
                case UNSET:
                    others.add(Updates.unset(entry.getKey()));
                    break;
                case INC:
                    Number n = (Number) entry.getValue();
                    if (n instanceof Integer) {
                        others.add(Updates.inc(entry.getKey(), n.intValue()));
                    } else if (n instanceof Long) {
                        others.add(Updates.inc(entry.getKey(), n.longValue()));
                    } else {
                        others.add(Updates.inc(entry.getKey(), n.doubleValue()));
                    }
                    break;
                case PUSH:
                    others.add(Updates.push(entry.getKey(), entry.getValue()));
                    break;
                case PULL:
                    others.add(Updates.pull(entry.getKey(), entry.getValue()));
                    break;
                case SET_ON_INSERT:
                    others.add(Updates.setOnInsert(entry.getKey(), entry.getValue()));
                    break;
                default:
                    throw new DataManagerException("Unsupported update op: " + entry.getOp());
            }
        }
        parts.addAll(others);
        if (parts.isEmpty()) {
            throw new DataManagerException("Refusing empty update");
        }
        if (parts.size() == 1) {
            return parts.get(0);
        }
        return Updates.combine(parts.toArray(new Bson[0]));
    }

    @Override
    public void close() {
        client.close();
    }

    public MongoConfig getConfig() {
        return config;
    }

    static MongoAdapter createIfConfigured(DataManagerConfig managerConfig) {
        MongoConfig cfg = managerConfig.getMongoConfig();
        if (cfg == null) {
            throw new DataManagerException("MongoDB is not configured in DataManagerConfig");
        }
        return new MongoAdapter(cfg);
    }
}
