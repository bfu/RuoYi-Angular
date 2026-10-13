package com.ruoyi.dsp.generator;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.dsp.metadata.DspColumnInfo;
import com.ruoyi.dsp.metadata.DspNaming;

/**
 * CRUD 脚本片段生成器
 * <p>
 * 依据表结构生成 6 条相互独立的脚本：查询所有、翻页查询、批量添加、单条添加、修改、删除
 * （查询拆「查询所有 / 翻页查询」，新增拆「批量添加 / 单条添加」）。
 * 生成的内容为<b>单条</b> MyBatis 语句片段（不写 id、不写 namespace，由平台按脚本 ID 与 slug 补全），
 * 与 {@code dsp_script.content} 的约定一致，可直接在脚本管理页继续编辑与发布。
 * <ul>
 * <li>查询统一返回 {@code java.util.Map}，字段以下划线转驼峰作为别名（如 {@code user_name as userName}），
 * 无需为每张表创建 Java 类型</li>
 * <li>增删改的参数名为驼峰属性名（如 {@code userName}），批量添加的参数为 {@code list}</li>
 * <li>翻页语句按目标库方言生成（MySQL/SQL Server/PostgreSQL/Oracle），参数为 {@code offset} 与 {@code size}</li>
 * <li>自增主键不参与新增与修改的 SET，作为定位条件使用；表无主键时以全部字段作为条件</li>
 * </ul>
 *
 * @author ruoyi
 */
@Component
public class DspScriptGenerator
{
    /** 操作标识：查询所有 */
    public static final String ACTION_SELECT_ALL = "selectAll";

    /** 操作标识：翻页查询 */
    public static final String ACTION_SELECT_PAGE = "selectPage";

    /** 操作标识：批量添加 */
    public static final String ACTION_INSERT_BATCH = "insertBatch";

    /** 操作标识：单条添加 */
    public static final String ACTION_INSERT_ONE = "insertOne";

    /** 操作标识：修改 */
    public static final String ACTION_UPDATE = "update";

    /** 操作标识：删除 */
    public static final String ACTION_DELETE = "delete";

    /** 查询结果统一映射为 Map */
    private static final String RESULT_TYPE = "java.util.Map";

    /** 脚本标识最大长度（与 dsp_script.slug 列长度一致） */
    private static final int MAX_SLUG_LENGTH = 128;

    /** 脚本名称最大长度（与 dsp_script.name 列长度一致） */
    private static final int MAX_NAME_LENGTH = 128;

    /** 由表名推导的标识基名最大长度，为操作后缀预留空间 */
    private static final int MAX_BASE_LENGTH = 100;

    /**
     * 依据表结构生成 6 条 CRUD 脚本
     *
     * @param tableName      表名
     * @param tableComment   表注释（用于生成中文脚本名称，可为空）
     * @param columns        表结构（按字段顺序）
     * @param databaseProduct 数据库产品名（决定翻页方言，可为空）
     */
    public List<DspGeneratedScript> generate(String tableName, String tableComment, List<DspColumnInfo> columns,
            String databaseProduct)
    {
        List<DspGeneratedScript> scripts = new ArrayList<>();
        if (StringUtils.isBlank(tableName) || columns == null || columns.isEmpty())
        {
            return scripts;
        }
        String baseSlug = baseSlug(tableName);
        String baseName = StringUtils.isBlank(tableComment) ? tableName : tableComment.trim();

        List<DspColumnInfo> primaryKeys = new ArrayList<>();
        List<DspColumnInfo> insertColumns = new ArrayList<>();
        List<DspColumnInfo> updateColumns = new ArrayList<>();
        for (DspColumnInfo column : columns)
        {
            if (column.isPrimaryKey())
            {
                primaryKeys.add(column);
            }
            if (!column.isAutoIncrement())
            {
                insertColumns.add(column);
            }
            if (!column.isPrimaryKey() && !column.isAutoIncrement())
            {
                updateColumns.add(column);
            }
        }
        if (insertColumns.isEmpty())
        {
            // 全为自增列时退化为全字段插入，保证 SQL 合法
            insertColumns.addAll(columns);
        }
        if (updateColumns.isEmpty())
        {
            for (DspColumnInfo column : columns)
            {
                if (!column.isPrimaryKey())
                {
                    updateColumns.add(column);
                }
            }
        }
        if (updateColumns.isEmpty())
        {
            updateColumns.addAll(columns);
        }
        // 无主键时以全部字段作为定位条件
        List<DspColumnInfo> conditionColumns = primaryKeys.isEmpty() ? columns : primaryKeys;

        scripts.add(script(ACTION_SELECT_ALL, "查询所有", "SELECT", baseSlug, "SelectAll", baseName + "查询所有",
                "由脚本生成器根据表 " + tableName + " 生成：查询全部数据，无需参数。",
                selectAll(tableName, columns, conditionColumns)));
        scripts.add(script(ACTION_SELECT_PAGE, "翻页查询", "SELECT", baseSlug, "SelectPage", baseName + "翻页查询",
                "由脚本生成器根据表 " + tableName + " 生成：分页查询，参数 offset（起始行，从 0 开始）与 size（每页条数）。",
                selectPage(tableName, columns, conditionColumns, databaseProduct)));
        scripts.add(script(ACTION_INSERT_BATCH, "批量添加", "INSERT", baseSlug, "InsertBatch", baseName + "批量添加",
                "由脚本生成器根据表 " + tableName + " 生成：批量新增，参数 list 为对象数组，元素键为驼峰字段名。",
                insertBatch(tableName, insertColumns)));
        scripts.add(script(ACTION_INSERT_ONE, "单条添加", "INSERT", baseSlug, "InsertOne", baseName + "单条添加",
                "由脚本生成器根据表 " + tableName + " 生成：单条新增，参数为各驼峰字段名。",
                insertOne(tableName, insertColumns)));
        scripts.add(script(ACTION_UPDATE, "修改", "UPDATE", baseSlug, "Update", baseName + "修改",
                "由脚本生成器根据表 " + tableName + " 生成：按主键修改，参数为各驼峰字段名（含主键）。",
                update(tableName, updateColumns, conditionColumns)));
        scripts.add(script(ACTION_DELETE, "删除", "DELETE", baseSlug, "Delete", baseName + "删除",
                "由脚本生成器根据表 " + tableName + " 生成：按主键删除，参数为主键驼峰字段名。",
                delete(tableName, conditionColumns)));
        return scripts;
    }

    private DspGeneratedScript script(String action, String actionName, String sqlCommandType, String baseSlug,
            String slugSuffix, String name, String description, String content)
    {
        DspGeneratedScript script = new DspGeneratedScript();
        script.setAction(action);
        script.setActionName(actionName);
        script.setSqlCommandType(sqlCommandType);
        script.setSlug(truncate(baseSlug + slugSuffix, MAX_SLUG_LENGTH));
        script.setName(truncate(name, MAX_NAME_LENGTH));
        script.setDescription(description);
        script.setContent(content);
        return script;
    }

    private String selectAll(String tableName, List<DspColumnInfo> columns, List<DspColumnInfo> orderColumns)
    {
        StringBuilder builder = new StringBuilder();
        builder.append("<select resultType=\"").append(RESULT_TYPE).append("\">\n");
        builder.append("  select ").append(selectColumns(columns, "  ")).append('\n');
        builder.append("  from ").append(tableName);
        appendOrderBy(builder, orderColumns);
        builder.append("\n</select>");
        return builder.toString();
    }

    private String selectPage(String tableName, List<DspColumnInfo> columns, List<DspColumnInfo> orderColumns,
            String databaseProduct)
    {
        StringBuilder builder = new StringBuilder();
        builder.append("<select resultType=\"").append(RESULT_TYPE).append("\">\n");
        builder.append("  select ").append(selectColumns(columns, "  ")).append('\n');
        builder.append("  from ").append(tableName);
        appendOrderBy(builder, orderColumns);
        builder.append('\n').append("  ").append(pageClause(databaseProduct));
        builder.append("\n</select>");
        return builder.toString();
    }

    private String insertBatch(String tableName, List<DspColumnInfo> insertColumns)
    {
        StringBuilder builder = new StringBuilder();
        builder.append("<insert>\n");
        builder.append("  insert into ").append(tableName).append(" (\n");
        builder.append("    ").append(columnList(insertColumns, "    ")).append('\n');
        builder.append("  ) values\n");
        builder.append("  <foreach collection=\"list\" item=\"item\" separator=\",\">\n");
        builder.append("    (").append(bindings(insertColumns, "item.")).append(")\n");
        builder.append("  </foreach>\n");
        builder.append("</insert>");
        return builder.toString();
    }

    private String insertOne(String tableName, List<DspColumnInfo> insertColumns)
    {
        StringBuilder builder = new StringBuilder();
        builder.append("<insert>\n");
        builder.append("  insert into ").append(tableName).append(" (\n");
        builder.append("    ").append(columnList(insertColumns, "    ")).append('\n');
        builder.append("  ) values (").append(bindings(insertColumns, "")).append(")\n");
        builder.append("</insert>");
        return builder.toString();
    }

    private String update(String tableName, List<DspColumnInfo> updateColumns, List<DspColumnInfo> conditionColumns)
    {
        StringBuilder builder = new StringBuilder();
        builder.append("<update>\n");
        builder.append("  update ").append(tableName).append('\n');
        builder.append("  set ").append(setClause(updateColumns)).append('\n');
        builder.append("  where ").append(conditionClause(conditionColumns)).append('\n');
        builder.append("</update>");
        return builder.toString();
    }

    private String delete(String tableName, List<DspColumnInfo> conditionColumns)
    {
        StringBuilder builder = new StringBuilder();
        builder.append("<delete>\n");
        builder.append("  delete from ").append(tableName).append('\n');
        builder.append("  where ").append(conditionClause(conditionColumns)).append('\n');
        builder.append("</delete>");
        return builder.toString();
    }

    private void appendOrderBy(StringBuilder builder, List<DspColumnInfo> orderColumns)
    {
        if (orderColumns.isEmpty())
        {
            return;
        }
        // 含自增主键时按「最新在前」排序，否则按字段升序
        boolean desc = orderColumns.stream().anyMatch(DspColumnInfo::isAutoIncrement);
        builder.append("\n  order by ");
        for (int i = 0; i < orderColumns.size(); i++)
        {
            if (i > 0)
            {
                builder.append(", ");
            }
            builder.append(orderColumns.get(i).getColumnName()).append(desc ? " desc" : " asc");
        }
    }

    private String pageClause(String databaseProduct)
    {
        String product = databaseProduct == null ? "" : databaseProduct.toLowerCase(Locale.ROOT);
        if (product.contains("oracle") || product.contains("sql server") || product.contains("sqlserver"))
        {
            return "offset #{offset} rows fetch next #{size} rows only";
        }
        if (product.contains("mysql") || product.contains("mariadb"))
        {
            return "limit #{offset}, #{size}";
        }
        return "limit #{size} offset #{offset}";
    }

    private String selectColumns(List<DspColumnInfo> columns, String indent)
    {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < columns.size(); i++)
        {
            if (i > 0)
            {
                builder.append(",\n").append(indent);
            }
            builder.append(alias(columns.get(i)));
        }
        return builder.toString();
    }

    private String alias(DspColumnInfo column)
    {
        String name = column.getColumnName();
        String field = parameterName(column);
        if (field.equalsIgnoreCase(name))
        {
            return name;
        }
        return name + " as " + field;
    }

    private String columnList(List<DspColumnInfo> columns, String indent)
    {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < columns.size(); i++)
        {
            if (i > 0)
            {
                builder.append(",\n").append(indent);
            }
            builder.append(columns.get(i).getColumnName());
        }
        return builder.toString();
    }

    private String bindings(List<DspColumnInfo> columns, String prefix)
    {
        List<String> parts = new ArrayList<>();
        for (DspColumnInfo column : columns)
        {
            parts.add("#{" + prefix + parameterName(column) + "}");
        }
        return String.join(", ", parts);
    }

    private String setClause(List<DspColumnInfo> columns)
    {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < columns.size(); i++)
        {
            if (i > 0)
            {
                builder.append(",\n      ");
            }
            builder.append(columns.get(i).getColumnName()).append(" = #{").append(parameterName(columns.get(i)))
                    .append('}');
        }
        return builder.toString();
    }

    private String conditionClause(List<DspColumnInfo> columns)
    {
        List<String> parts = new ArrayList<>();
        for (DspColumnInfo column : columns)
        {
            parts.add(column.getColumnName() + " = #{" + parameterName(column) + "}");
        }
        return String.join(" and ", parts);
    }

    /**
     * 参数名统一取驼峰属性名，与查询别名保持一致；属性名缺失时回退字段名
     */
    private String parameterName(DspColumnInfo column)
    {
        return StringUtils.isBlank(column.getJavaField()) ? column.getColumnName() : column.getJavaField();
    }

    /**
     * 由表名推导脚本标识基名（小驼峰，仅保留字母数字，且以字母开头）
     */
    private String baseSlug(String tableName)
    {
        String camel = DspNaming.toCamelCase(tableName);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < camel.length(); i++)
        {
            char current = camel.charAt(i);
            if (Character.isLetterOrDigit(current))
            {
                builder.append(current);
            }
        }
        String value = builder.length() == 0 ? "table" : builder.toString();
        if (!Character.isLetter(value.charAt(0)))
        {
            value = "t" + value;
        }
        return truncate(value, MAX_BASE_LENGTH);
    }

    private String truncate(String value, int maxLength)
    {
        if (value == null || value.length() <= maxLength)
        {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
