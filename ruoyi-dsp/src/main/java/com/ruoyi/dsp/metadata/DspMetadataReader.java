package com.ruoyi.dsp.metadata;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.stereotype.Component;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.dsp.engine.DspDataSourceManager;

/**
 * 数据源元数据读取器
 * <p>
 * 基于 JDBC {@link DatabaseMetaData} 探测任意数据源下的实例（catalog/schema）、数据表与字段结构，
 * 不依赖 {@code information_schema} 等方言专有视图，因此 MySQL / PostgreSQL / Oracle 等可共用同一实现。
 * 连接由 {@link DspDataSourceManager} 按 {@code dataSourceId} 提供（{@code 0} 表示默认主库）。
 *
 * @author ruoyi
 */
@Component
public class DspMetadataReader
{
    /** 纳入探测的表类型（视图一并纳入，便于生成只读脚本） */
    private static final String[] TABLE_TYPES = { "TABLE", "VIEW" };

    /** 单次最多返回的表数量，避免超大库把下拉列表撑爆 */
    private static final int MAX_TABLES = 500;

    /** 以 catalog 表示实例的数据库产品关键字（其余产品优先使用 schema） */
    private static final String[] CATALOG_PRODUCTS = { "mysql", "mariadb", "sql server", "sqlserver", "h2",
            "sqlite", "clickhouse", "db2", "derby" };

    /** 系统模式，探测实例时过滤，避免下拉被系统对象淹没 */
    private static final Set<String> SYSTEM_SCHEMAS = Set.of("information_schema", "pg_catalog", "pg_toast",
            "mysql", "performance_schema", "sys", "system");

    private final DspDataSourceManager dataSourceManager;

    public DspMetadataReader(DspDataSourceManager dataSourceManager)
    {
        this.dataSourceManager = dataSourceManager;
    }

    /**
     * 数据源对应的数据库产品名（小写），用于决定分页方言与实例探测策略
     */
    public String databaseProduct(Long dataSourceId)
    {
        try (Connection connection = dataSourceManager.openConnection(dataSourceId))
        {
            return lower(connection.getMetaData().getDatabaseProductName());
        }
        catch (SQLException e)
        {
            throw new ServiceException("读取数据库信息失败：" + e.getMessage());
        }
    }

    /**
     * 探测数据源下的实例（MySQL 库名 / PostgreSQL、Oracle 模式）
     */
    public List<DspInstanceInfo> listInstances(Long dataSourceId)
    {
        try (Connection connection = dataSourceManager.openConnection(dataSourceId))
        {
            DatabaseMetaData metaData = connection.getMetaData();
            String product = lower(metaData.getDatabaseProductName());
            String currentCatalog = connection.getCatalog();
            String currentSchema = safeSchema(connection);
            List<DspInstanceInfo> instances = new ArrayList<>();
            if (preferCatalog(product))
            {
                appendCatalogInstances(instances, readCatalogs(metaData), currentCatalog);
                return instances;
            }
            for (String schema : readSchemas(metaData))
            {
                if (isSystemSchema(schema))
                {
                    continue;
                }
                instances.add(new DspInstanceInfo(schema, currentCatalog, schema, "schema",
                        schema.equals(currentSchema)));
            }
            if (!instances.isEmpty())
            {
                return instances;
            }
            // 未探测到业务模式（如 SQLite、H2 内存库）时回退到 catalog
            appendCatalogInstances(instances, readCatalogs(metaData), currentCatalog);
            return instances;
        }
        catch (SQLException e)
        {
            throw new ServiceException("读取数据库实例失败：" + e.getMessage());
        }
    }

    /**
     * 查询指定实例下的数据表（tableName 为空时返回全部，最多 {@link #MAX_TABLES} 张）
     */
    public List<DspTableInfo> listTables(Long dataSourceId, String catalog, String schema, String tableName)
    {
        try (Connection connection = dataSourceManager.openConnection(dataSourceId))
        {
            DatabaseMetaData metaData = connection.getMetaData();
            String pattern = StringUtils.isBlank(tableName) ? "%" : "%" + escapePattern(tableName.trim()) + "%";
            List<DspTableInfo> tables = new ArrayList<>();
            try (ResultSet rs = metaData.getTables(blankToNull(catalog), blankToNull(schema), pattern, TABLE_TYPES))
            {
                while (rs.next() && tables.size() < MAX_TABLES)
                {
                    tables.add(new DspTableInfo(rs.getString("TABLE_NAME"), rs.getString("REMARKS"),
                            rs.getString("TABLE_TYPE")));
                }
            }
            tables.sort(Comparator.comparing(DspTableInfo::getTableName,
                    Comparator.nullsLast(String::compareToIgnoreCase)));
            return tables;
        }
        catch (SQLException e)
        {
            throw new ServiceException("读取数据表失败：" + e.getMessage());
        }
    }

    /**
     * 查询指定表的字段结构（按字段顺序返回）
     */
    public List<DspColumnInfo> listColumns(Long dataSourceId, String catalog, String schema, String tableName)
    {
        if (StringUtils.isBlank(tableName))
        {
            throw new ServiceException("表名不能为空");
        }
        try (Connection connection = dataSourceManager.openConnection(dataSourceId))
        {
            DatabaseMetaData metaData = connection.getMetaData();
            String targetCatalog = blankToNull(catalog);
            String targetSchema = blankToNull(schema);
            Set<String> primaryKeys = readPrimaryKeys(metaData, targetCatalog, targetSchema, tableName);
            List<DspColumnInfo> columns = new ArrayList<>();
            try (ResultSet rs = metaData.getColumns(targetCatalog, targetSchema, null, null))
            {
                while (rs.next())
                {
                    // getColumns 的表名是 LIKE 模式，需按实际表名精确过滤，避免同前缀表串入
                    if (!tableName.equalsIgnoreCase(rs.getString("TABLE_NAME")))
                    {
                        continue;
                    }
                    DspColumnInfo column = new DspColumnInfo();
                    String columnName = rs.getString("COLUMN_NAME");
                    int sqlType = rs.getInt("DATA_TYPE");
                    int size = rs.getInt("COLUMN_SIZE");
                    String typeName = rs.getString("TYPE_NAME");
                    boolean nullable = rs.getInt("NULLABLE") != DatabaseMetaData.columnNoNulls;
                    boolean primaryKey = primaryKeys.contains(columnName);
                    column.setColumnName(columnName);
                    column.setColumnComment(rs.getString("REMARKS"));
                    column.setColumnType(size > 0 ? typeName + "(" + size + ")" : typeName);
                    column.setJavaType(javaTypeOf(sqlType));
                    column.setJavaField(DspNaming.toCamelCase(columnName));
                    column.setPrimaryKey(primaryKey);
                    column.setRequired(!nullable && !primaryKey);
                    column.setAutoIncrement("YES".equalsIgnoreCase(rs.getString("IS_AUTOINCREMENT")));
                    column.setOrdinal(rs.getInt("ORDINAL_POSITION"));
                    columns.add(column);
                }
            }
            columns.sort(Comparator.comparingInt(DspColumnInfo::getOrdinal));
            return columns;
        }
        catch (SQLException e)
        {
            throw new ServiceException("读取表结构失败：" + e.getMessage());
        }
    }

    private Set<String> readPrimaryKeys(DatabaseMetaData metaData, String catalog, String schema, String tableName)
            throws SQLException
    {
        Set<String> primaryKeys = new LinkedHashSet<>();
        try (ResultSet rs = metaData.getPrimaryKeys(catalog, schema, tableName))
        {
            while (rs.next())
            {
                primaryKeys.add(rs.getString("COLUMN_NAME"));
            }
        }
        return primaryKeys;
    }

    private void appendCatalogInstances(List<DspInstanceInfo> instances, List<String> catalogs, String currentCatalog)
    {
        for (String catalog : catalogs)
        {
            instances.add(new DspInstanceInfo(catalog, catalog, null, "catalog", catalog.equals(currentCatalog)));
        }
    }

    private List<String> readCatalogs(DatabaseMetaData metaData) throws SQLException
    {
        List<String> values = new ArrayList<>();
        try (ResultSet rs = metaData.getCatalogs())
        {
            while (rs.next())
            {
                String value = rs.getString("TABLE_CAT");
                if (StringUtils.isNotBlank(value))
                {
                    values.add(value);
                }
            }
        }
        return values;
    }

    private List<String> readSchemas(DatabaseMetaData metaData) throws SQLException
    {
        List<String> values = new ArrayList<>();
        try (ResultSet rs = metaData.getSchemas())
        {
            while (rs.next())
            {
                String value = rs.getString("TABLE_SCHEM");
                if (StringUtils.isNotBlank(value))
                {
                    values.add(value);
                }
            }
        }
        return values;
    }

    private boolean preferCatalog(String product)
    {
        for (String keyword : CATALOG_PRODUCTS)
        {
            if (product.contains(keyword))
            {
                return true;
            }
        }
        return false;
    }

    private boolean isSystemSchema(String schema)
    {
        return schema != null && SYSTEM_SCHEMAS.contains(schema.toLowerCase(Locale.ROOT));
    }

    private String safeSchema(Connection connection)
    {
        try
        {
            return connection.getSchema();
        }
        catch (Exception e)
        {
            return null;
        }
    }

    private String javaTypeOf(int sqlType)
    {
        switch (sqlType)
        {
            case Types.BIT:
            case Types.BOOLEAN:
                return "Boolean";
            case Types.TINYINT:
            case Types.SMALLINT:
            case Types.INTEGER:
                return "Integer";
            case Types.BIGINT:
                return "Long";
            case Types.FLOAT:
            case Types.REAL:
            case Types.DOUBLE:
                return "Double";
            case Types.NUMERIC:
            case Types.DECIMAL:
                return "BigDecimal";
            case Types.DATE:
                return "LocalDate";
            case Types.TIME:
                return "LocalTime";
            case Types.TIMESTAMP:
            case Types.TIMESTAMP_WITH_TIMEZONE:
                return "LocalDateTime";
            case Types.BINARY:
            case Types.VARBINARY:
            case Types.LONGVARBINARY:
            case Types.BLOB:
                return "byte[]";
            default:
                return "String";
        }
    }

    private String escapePattern(String value)
    {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private String blankToNull(String value)
    {
        return StringUtils.isBlank(value) ? null : value.trim();
    }

    private String lower(String value)
    {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
