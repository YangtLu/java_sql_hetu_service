# Java 数据查询服务 开发手册

> 
> **目标**：写一个 **Javalin** 服务，提供两个 HTTP 接口，内部用 JDBC 查数据库
> **日期**：2026-04-21

---

## 1. 背景

Python 端（bank-report-agent）现在直连数据库做测试和查询。行内要求**不允许 Python 直连数据库**，因此需要一个 Java 服务来代劳。

**调用关系：**

```
浏览器(Vue) → Python(8001) → Java(8082) → 数据库
```

Java 服务**就做一件事**：接收 Python 传来的 SQL 和数据源参数，用 JDBC 跑一下，把结果返回。

---

## 2. 技术选型

| 项 | 选型 |
|----|------|
| HTTP 框架 | **Javalin 6.x**（极简，API 跟 Python Flask 很像） |
| JDBC | 原生 JDBC，不用连接池（每次新建连接，用完就关） |
| JSON | Jackson（Javalin 自带集成） |
| 日志 | SLF4J + Logback（Javalin 默认） |
| 构建 | Maven |
| JDK | 17+ |
| 端口 | `8082` |
| 部署 | 与 Python 服务同机房，内网；`java -jar xxx.jar` 启动 |

### pom.xml 核心依赖

```xml
<dependencies>
    <!-- HTTP 框架 -->
    <dependency>
        <groupId>io.javalin</groupId>
        <artifactId>javalin</artifactId>
        <version>6.3.0</version>
    </dependency>
    <!-- Javalin 依赖这个来序列化 JSON -->
    <dependency>
        <groupId>com.fasterxml.jackson.core</groupId>
        <artifactId>jackson-databind</artifactId>
        <version>2.17.0</version>
    </dependency>
    <!-- 日志 -->
    <dependency>
        <groupId>org.slf4j</groupId>
        <artifactId>slf4j-simple</artifactId>
        <version>2.0.13</version>
    </dependency>

    <!-- JDBC 驱动，按需加 -->
    <dependency>
        <groupId>com.mysql</groupId>
        <artifactId>mysql-connector-j</artifactId>
        <version>8.3.0</version>
    </dependency>
    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>postgresql</artifactId>
        <version>42.7.3</version>
    </dependency>
    <!-- Oracle / SQLServer / ClickHouse 按需加 -->
</dependencies>
```

### 打成可运行 jar（maven-shade-plugin）

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.apache.maven.plugins</groupId>
            <artifactId>maven-shade-plugin</artifactId>
            <version>3.5.0</version>
            <executions>
                <execution>
                    <phase>package</phase>
                    <goals><goal>shade</goal></goals>
                    <configuration>
                        <transformers>
                            <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                                <mainClass>com.xxx.datasvc.Application</mainClass>
                            </transformer>
                        </transformers>
                    </configuration>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

---

## 3. 接口清单

| # | 接口 | 方法 | 路径 |
|---|------|------|------|
| 1 | 测试数据库连接 | POST | `/api/datasource/test` |
| 2 | 执行只读查询 | POST | `/api/datasource/query` |

**字段命名一律 snake_case**（和 Python 侧契约保持一致）。

---

## 4. 启动类（整个 HTTP 服务就这么几行）

```java
package com.xxx.datasvc;

import io.javalin.Javalin;
import com.xxx.datasvc.controller.DatasourceController;
import com.xxx.datasvc.exception.SqlForbiddenException;
import java.util.Map;

public class Application {
    public static void main(String[] args) {
        DatasourceController controller = new DatasourceController();

        Javalin app = Javalin.create(config -> {
            // 可按需开启请求日志
            // config.requestLogger.http((ctx, ms) -> log.info(...));
        })
        .post("/api/datasource/test",  controller::testConnection)
        .post("/api/datasource/query", controller::query)
        // SQL 安全校验不通过 → 统一 403
        .exception(SqlForbiddenException.class, (e, ctx) -> {
            ctx.status(403).json(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        })
        // 参数非法（如 type 不支持）→ 400
        .exception(IllegalArgumentException.class, (e, ctx) -> {
            ctx.status(400).json(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        })
        .start(8082);

        System.out.println("Data query service listening on :8082");
    }
}
```

> **对比 Spring Boot**：没有 `@SpringBootApplication`、没有 `@RestController`、没有 `application.yml`，一个 `main` 方法全搞定。

---

## 5. POST /api/datasource/test

测试能不能连上数据库。

### 请求体

```json
{
  "type": "mysql",
  "host": "192.168.1.100",
  "port": "3306",
  "database": "loan_db",
  "username": "readonly_user",
  "password": "xxxxxx"
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| type | string | `mysql` / `postgresql` / `oracle` / `sqlserver` / `clickhouse` |
| host | string | 主机 IP 或域名 |
| port | string | 端口（注意是字符串不是数字） |
| database | string | 库名 |
| username | string | 账号 |
| password | string | 密码 |

### 响应（成功）

```json
{
  "success": true,
  "message": "连接成功",
  "server_version": "8.0.32"
}
```

### 响应（失败）—— 注意 HTTP 仍是 200

```json
{
  "success": false,
  "message": "连接失败：Access denied for user 'readonly_user'@'%'"
}
```

### 实现要点

1. 用 `DriverManager.getConnection(url, user, pwd)` 拿连接
2. **设置连接超时 5 秒**（JDBC URL 加参数，例如 MySQL 加 `?connectTimeout=5000`）
3. 连上以后跑一句 `SELECT VERSION()`（或 Oracle 的 `v$version`）拿版本号
4. **拿到后立即 `connection.close()`**，不留连接
5. 任何异常（连不上、认证失败、超时）都返回 HTTP 200 + `success: false` + 异常 message
6. **只有"参数非法"才返回 HTTP 400**（比如 `type` 不在支持列表，抛 `IllegalArgumentException`）
7. **password 不要写到日志里**

### JDBC URL 拼接参考

```java
switch (type) {
    case "mysql"      → "jdbc:mysql://host:port/db?connectTimeout=5000&socketTimeout=30000"
    case "postgresql" → "jdbc:postgresql://host:port/db?connectTimeout=5&socketTimeout=30"
    case "oracle"     → "jdbc:oracle:thin:@host:port:db"  // 超时用 Properties 传
    case "sqlserver"  → "jdbc:sqlserver://host:port;databaseName=db;loginTimeout=5"
    case "clickhouse" → "jdbc:clickhouse://host:port/db?connection_timeout=5000"
}
```

### Controller 示例

```java
public void testConnection(Context ctx) {
    DatasourceConfig ds = ctx.bodyAsClass(DatasourceConfig.class);
    String url = JdbcUrlBuilder.build(ds);   // 不支持的 type → throw IllegalArgumentException

    try (Connection conn = DriverManager.getConnection(url, ds.getUsername(), ds.getPassword());
         Statement st = conn.createStatement();
         ResultSet rs = st.executeQuery(versionSql(ds.getType()))) {

        String version = rs.next() ? rs.getString(1) : "unknown";
        ctx.json(Map.of(
            "success", true,
            "message", "连接成功",
            "server_version", version
        ));
    } catch (SQLException e) {
        log.warn("test connection failed: type={} host={} db={} user={} err={}",
            ds.getType(), ds.getHost(), ds.getDatabase(), ds.getUsername(), e.getMessage());
        ctx.json(Map.of(
            "success", false,
            "message", "连接失败：" + e.getMessage()
        ));
    }
}
```

---

## 6. POST /api/datasource/query

执行一条 SELECT 查询。

### 请求体

```json
{
  "sql": "SELECT ROUND(SUM(balance_amt)/1e8, 2) FROM loan_detail WHERE report_date='2024-06-30'",
  "datasource": {
    "type": "mysql",
    "host": "192.168.1.100",
    "port": "3306",
    "database": "loan_db",
    "username": "readonly_user",
    "password": "xxxxxx"
  }
}
```

| 字段 | 说明 |
|------|------|
| sql | 要执行的 SQL（**必须是只读查询**） |
| datasource | 和 `/datasource/test` 请求体格式相同 |

### 响应（成功）

```json
{
  "success": true,
  "columns": ["ROUND(SUM(balance_amt)/1e8, 2)"],
  "rows": [["42.56"]],
  "execute_time_ms": 128
}
```

| 字段 | 类型 | 说明 |
|------|------|------|
| columns | string[] | 列名 |
| rows | string[][] | 所有单元格**统一转 String**，NULL 转 `""` |
| execute_time_ms | int | SQL 执行耗时（毫秒） |

### 响应（SQL 被拦截）—— HTTP 403

```json
{
  "success": false,
  "message": "SQL 包含禁用关键字 DROP，仅支持只读查询"
}
```

### 响应（执行失败）—— HTTP 200

```json
{
  "success": false,
  "message": "Table 'loan_db.loan_detail' doesn't exist"
}
```

### Controller 完整示例

```java
public void query(Context ctx) {
    QueryRequest req = ctx.bodyAsClass(QueryRequest.class);

    // 1. SQL 只读校验（见 §7），不通过抛 SqlForbiddenException → 全局 handler 转 403
    SqlValidator.validateReadonly(req.getSql());

    DatasourceConfig ds = req.getDatasource();
    String url = JdbcUrlBuilder.build(ds);

    try (Connection conn = DriverManager.getConnection(url, ds.getUsername(), ds.getPassword());
         PreparedStatement ps = conn.prepareStatement(req.getSql())) {

        // 2. 查询超时 10 秒，防止慢 SQL 挂死
        ps.setQueryTimeout(10);

        long start = System.currentTimeMillis();
        try (ResultSet rs = ps.executeQuery()) {
            // 3. 读列名
            ResultSetMetaData md = rs.getMetaData();
            int colCount = md.getColumnCount();
            List<String> columns = new ArrayList<>();
            for (int i = 1; i <= colCount; i++) columns.add(md.getColumnLabel(i));

            // 4. 读数据，最多 1000 行
            final int MAX_ROWS = 1000;
            List<List<String>> rows = new ArrayList<>();
            while (rs.next() && rows.size() < MAX_ROWS) {
                List<String> row = new ArrayList<>(colCount);
                for (int i = 1; i <= colCount; i++) {
                    Object v = rs.getObject(i);
                    row.add(v == null ? "" : String.valueOf(v));
                }
                rows.add(row);
            }
            long elapsed = System.currentTimeMillis() - start;

            log.info("query | type={} host={} db={} user={} elapsed={}ms rows={} success=true",
                ds.getType(), ds.getHost(), ds.getDatabase(), ds.getUsername(), elapsed, rows.size());

            ctx.json(Map.of(
                "success", true,
                "columns", columns,
                "rows", rows,
                "execute_time_ms", elapsed
            ));
        }
    } catch (SQLException e) {
        log.warn("query failed: type={} host={} db={} user={} err={}",
            ds.getType(), ds.getHost(), ds.getDatabase(), ds.getUsername(), e.getMessage());
        ctx.json(Map.of(
            "success", false,
            "message", e.getMessage()
        ));
    }
}
```

### 容易踩的坑

- `ps.setQueryTimeout(10)` 一定要加，否则慢 SQL 会挂死线程
- `rs.getObject(i)` 拿到的可能是 `BigDecimal` / `Timestamp` / `byte[]`，统一 `String.valueOf`
- 结果集超过 1000 行直接 break，不要全读（会 OOM）
- 连接/语句/结果集一律用 try-with-resources，别手动 close

---

## 7. SQL 安全校验（必须做）

**别跳过这一步**。如果有人直接调你的 Java 服务（绕过 Python），SQL 里写 DELETE 就把表删了。

```java
package com.xxx.datasvc.service;

import com.xxx.datasvc.exception.SqlForbiddenException;

public class SqlValidator {

    private static final String[] FORBIDDEN = {
        "INSERT", "UPDATE", "DELETE", "DROP", "TRUNCATE", "ALTER",
        "CREATE", "GRANT", "REVOKE", "EXEC", "EXECUTE", "CALL",
        "RENAME", "REPLACE", "MERGE", "LOAD", "LOCK", "UNLOCK",
        "SHUTDOWN", "SET"
    };

    public static void validateReadonly(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new SqlForbiddenException("SQL 不能为空");
        }

        // 1. 去掉注释
        String cleaned = sql
            .replaceAll("/\\*[\\s\\S]*?\\*/", " ")   // /* ... */
            .replaceAll("--[^\\n]*", " ")             // --
            .replaceAll("#[^\\n]*", " ")              // #
            .trim();

        // 2. 去掉末尾可选分号
        if (cleaned.endsWith(";")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }

        // 3. 多语句拦截
        if (cleaned.contains(";")) {
            throw new SqlForbiddenException("不允许多条 SQL");
        }

        // 4. 白名单：必须以 SELECT / WITH 开头
        String upper = cleaned.toUpperCase();
        if (!upper.startsWith("SELECT") && !upper.startsWith("WITH")) {
            throw new SqlForbiddenException("只允许 SELECT / WITH 查询");
        }

        // 5. 黑名单关键字（按单词边界匹配）
        for (String kw : FORBIDDEN) {
            if (upper.matches("(?s).*\\b" + kw + "\\b.*")) {
                throw new SqlForbiddenException("SQL 包含禁用关键字 " + kw);
            }
        }
    }
}
```

```java
// exception/SqlForbiddenException.java
public class SqlForbiddenException extends RuntimeException {
    public SqlForbiddenException(String msg) { super(msg); }
}
```

> 403 的响应由 `Application.java` 里的 `.exception(SqlForbiddenException.class, ...)` 统一返回，不用在 controller 里处理。

---

## 8. 日志要求

**就三条，别记复杂的：**

1. 每次请求打一行 INFO，包含：接口名 / 数据源 type/host/port/database/username / SQL 前 200 字 / 耗时 / success
2. **password 字段绝对不能出现在日志里**（包括异常堆栈）
3. 异常打 WARN 或 ERROR，带堆栈

```java
// 好的示例
log.info("query | type={} host={} db={} user={} sql={} elapsed={}ms success={}",
    ds.getType(), ds.getHost(), ds.getDatabase(), ds.getUsername(),
    sql.length() > 200 ? sql.substring(0, 200) : sql,
    elapsed, success);

// 千万别这么写 ❌
log.info("received request: {}", objectMapper.writeValueAsString(req));  // 会把密码打出来
```

---

## 9. 联调用例

自测和联调时按这张表走：

| # | 用例 | 期望结果 |
|---|------|---------|
| 1 | 正常 MySQL 连接 | HTTP 200，`success:true`，有 `server_version` |
| 2 | 错误密码 | HTTP 200，`success:false`，message 含 `Access denied` |
| 3 | 不可达 IP | HTTP 200，`success:false`，5 秒内返回 |
| 4 | type=`mongodb` | HTTP 400 |
| 5 | `SELECT 1` | HTTP 200，`rows=[["1"]]` |
| 6 | `DELETE FROM t` | HTTP 403 |
| 7 | `SELECT 1; DROP TABLE t` | HTTP 403 |
| 8 | SQL 语法错 | HTTP 200，`success:false`，message 是数据库报错 |
| 9 | SQL 超过 10 秒 | HTTP 200，`success:false`，message 含超时字样 |
| 10 | 日志里搜 `password` | 没有任何命中 |

### 快速自测命令

```bash
# 启动
java -jar target/java-datasource-svc-1.0.jar

# 测连接
curl -X POST http://localhost:8082/api/datasource/test \
  -H "Content-Type: application/json" \
  -d '{"type":"mysql","host":"127.0.0.1","port":"3306","database":"test","username":"root","password":"root"}'

# 测查询
curl -X POST http://localhost:8082/api/datasource/query \
  -H "Content-Type: application/json" \
  -d '{"sql":"SELECT 1","datasource":{"type":"mysql","host":"127.0.0.1","port":"3306","database":"test","username":"root","password":"root"}}'

# 测 SQL 拦截（应返回 403）
curl -i -X POST http://localhost:8082/api/datasource/query \
  -H "Content-Type: application/json" \
  -d '{"sql":"DELETE FROM t","datasource":{"type":"mysql","host":"127.0.0.1","port":"3306","database":"test","username":"root","password":"root"}}'
```

---

## 10. 接口字段对齐速查表

**Python 侧发什么就接什么，发回什么 Python 侧就按什么格式解析**。字段名严格一致，别自作主张改。

### /api/datasource/test

| 方向 | 字段 |
|------|------|
| 请求 | `type, host, port, database, username, password` |
| 响应成功 | `success=true, message, server_version` |
| 响应失败 | `success=false, message` |

### /api/datasource/query

| 方向 | 字段 |
|------|------|
| 请求 | `sql, datasource{type,host,port,database,username,password}` |
| 响应成功 | `success=true, columns, rows, execute_time_ms` |
| 响应失败 | `success=false, message` |

---

## 11. 最小项目结构建议

```
java-datasource-svc/
├── src/main/java/com/xxx/datasvc/
│   ├── Application.java                 # main 方法 + Javalin 路由注册
│   ├── controller/
│   │   └── DatasourceController.java    # 两个接口都在这（testConnection / query）
│   ├── model/
│   │   ├── DatasourceConfig.java        # type/host/port/database/username/password
│   │   └── QueryRequest.java            # sql + datasource
│   ├── service/
│   │   ├── JdbcUrlBuilder.java          # 按 type 拼 JDBC URL
│   │   └── SqlValidator.java            # §7 的校验逻辑
│   └── exception/
│       └── SqlForbiddenException.java
├── src/main/resources/
│   └── simplelogger.properties          # slf4j-simple 日志级别配置（可选）
└── pom.xml
```

总代码量大概 **300~400 行**就能搞定，别想复杂了。

### Model 类示范（用 Jackson 自动反序列化，字段必须 snake_case 对应）

```java
// DatasourceConfig.java —— Jackson 会把 JSON 的 type/host/port/... 自动灌进来
public class DatasourceConfig {
    private String type;
    private String host;
    private String port;
    private String database;
    private String username;
    private String password;
    // getters / setters ...
}

// QueryRequest.java
public class QueryRequest {
    private String sql;
    private DatasourceConfig datasource;
    // getters / setters ...
}
```

---

## 12. 有疑问找谁

- 字段对齐 / 接口契约：Python 侧开发
- 行内数据源连接信息：DBA
- Javalin 用法：官网 https://javalin.io/documentation 
