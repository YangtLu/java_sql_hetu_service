# Java 数据查询服务

一个基于 Javalin 的轻量级数据查询服务，提供 HTTP 接口代理执行只读 SQL 查询。

## 架构

```
浏览器(Vue) → Python(8001) → Java(8095) → 数据库
```

## 技术栈

| 项 | 选型 |
|----|------|
| HTTP 框架 | Javalin 6.x |
| JSON | Jackson |
| 日志 | SLF4J + Logback |
| 构建 | Maven |
| JDK | 17+ |
| 端口 | 8095 |

## 支持的数据源

- MySQL
- PostgreSQL
- Oracle
- SQL Server
- ClickHouse
- Hetu

## 快速开始

### 方式一：使用 run.sh（推荐）

```bash
cd /home/dev/java_servive
./run.sh
```

脚本会自动下载所需依赖并启动服务。

### 方式二：使用 Maven

```bash
cd /home/dev/java_servive
mvn clean package
java -jar target/java-datasource-svc-1.0.0.jar
```

## 日常使用

### 服务启动后的状态

服务启动成功后，终端会显示：

```
Data query service listening on :8095
```

此时服务处于**前台运行**状态，占用当前终端窗口。日志会实时打印到终端，格式如下：

```
2026-04-28 10:30:15 [main] INFO  test connection | type=mysql host=192.168.1.100 db=loan_db user=readonly_user success=true version=8.0.32
2026-04-28 10:30:16 [main] INFO  query | type=mysql host=192.168.1.100 db=loan_db user=readonly_user sql=SELECT ROUND(SUM(balance_amt)/1e8, 2)... elapsed=128ms rows=1 success=true
```

### 后台运行（生产环境推荐）

```bash
# 后台启动，日志输出到 nohup.out
nohup java -jar target/java-datasource-svc-1.0.0.jar > /var/log/datasvc.log 2>&1 &

# 查看进程
ps aux | grep java-datasource-svc

# 查看日志
tail -f /var/log/datasvc.log

# 停止服务
pkill -f java-datasource-svc
```

### 验证服务是否正常

```bash
# 检查端口是否监听
curl http://localhost:8095/api/datasource/test \
  -X POST \
  -H "Content-Type: application/json" \
  -d '{"type":"mysql","host":"127.0.0.1","port":"3306","database":"test","username":"root","password":"root"}'
```

返回 `{"success":true,...}` 或 `{"success":false,...}` 都说明服务正常运行。

## 如何连接

### 从 Python 侧调用

```python
import requests

JAVA_SVC_URL = "http://192.168.1.50:8095"  # Java 服务的 IP 和端口

# 1. 测试数据库连接
def test_connection(datasource_config):
    resp = requests.post(
        f"{JAVA_SVC_URL}/api/datasource/test",
        json=datasource_config
    )
    return resp.json()

# 2. 执行查询
def execute_query(sql, datasource_config):
    resp = requests.post(
        f"{JAVA_SVC_URL}/api/datasource/query",
        json={
            "sql": sql,
            "datasource": datasource_config
        }
    )
    result = resp.json()
    if result.get("success"):
        return result["columns"], result["rows"]
    else:
        raise Exception(result.get("message"))

# 使用示例
datasource = {
    "type": "mysql",
    "host": "10.200.63.104",
    "port": "3306",
    "database": "loan_db",
    "username": "readonly_user",
    "password": "your_password"
}

# 测试连接
print(test_connection(datasource))

# 执行查询
columns, rows = execute_query("SELECT COUNT(*) FROM loan_detail", datasource)
print(f"共 {rows[0][0]} 条记录")
```

### 从 curl 命令行调用

```bash
# 测试 MySQL 连接
curl -X POST http://localhost:8095/api/datasource/test \
  -H "Content-Type: application/json" \
  -d '{
    "type": "mysql",
    "host": "10.200.63.104",
    "port": "3306",
    "database": "loan_db",
    "username": "readonly_user",
    "password": "your_password"
  }'

# 测试 Hetu 连接
curl -X POST http://localhost:8095/api/datasource/test \
  -H "Content-Type: application/json" \
  -d '{
    "type": "hetu",
    "host": "10.200.63.104",
    "port": "22443",
    "database": "bhive",
    "username": "test",
    "password": "secret"
  }'

# 测试 多源 连接
curl -X POST http://localhost:8095/api/datasource/test \
  -H "Content-Type: application/json" \
  -d '{
    "type": "hetu",
    "host": "10.200.63.104",
    "port": "22443",
    "database": "bhive",
    "username": "aide_user",
    "password": "M8,J>,Q3aO"
  }'

# 执行查询
curl -X POST http://localhost:8095/api/datasource/query \
  -H "Content-Type: application/json" \
  -d '{
    "sql": "SELECT COUNT(*) FROM loan_detail",
    "datasource": {
      "type": "mysql",
      "host": "10.200.63.104",
      "port": "3306",
      "database": "loan_db",
      "username": "readonly_user",
      "password": "your_password"
    }
  }'
```

### 从 Postman 调用

1. 新建 POST 请求，URL 填写 `http://localhost:8095/api/datasource/query`
2. Headers 添加 `Content-Type: application/json`
3. Body 选择 raw + JSON，填入请求体
4. 点击 Send

## 接口文档

### 1. POST /api/datasource/test

测试数据库连接。

**请求体：**

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
| type | string | 数据源类型：`mysql` / `postgresql` / `oracle` / `sqlserver` / `clickhouse` / `hetu` |
| host | string | 数据库主机 IP 或域名 |
| port | string | 端口号（字符串类型） |
| database | string | 库名 / catalog 名 |
| username | string | 账号 |
| password | string | 密码 |

**响应（成功）：**

```json
{
  "success": true,
  "message": "连接成功",
  "server_version": "8.0.32"
}
```

**响应（失败）：**

```json
{
  "success": false,
  "message": "连接失败：Access denied for user 'readonly_user'@'%'"
}
```

### 2. POST /api/datasource/query

执行只读查询。

**请求体：**

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

**响应（成功）：**

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
| columns | string[] | 列名列表 |
| rows | string[][] | 数据行，所有单元格统一为字符串，NULL 转为空字符串 `""` |
| execute_time_ms | int | SQL 执行耗时（毫秒） |

**响应（SQL 被拦截）—— HTTP 403：**

```json
{
  "success": false,
  "message": "SQL 包含禁用关键字 DROP，仅支持只读查询"
}
```

**响应（执行失败）—— HTTP 200：**

```json
{
  "success": false,
  "message": "Table 'loan_db.loan_detail' doesn't exist"
}
```

## 联调用例速查表

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

## 项目结构

```
java_servive/
├── pom.xml
├── run.sh
├── README.md
├── src/main/lib/
│   └── hetu_jdbc-1.3.0.jar
├── src/main/java/com/xxx/datasvc/
│   ├── Application.java
│   ├── controller/
│   │   └── DatasourceController.java
│   ├── model/
│   │   ├── DatasourceConfig.java
│   │   └── QueryRequest.java
│   ├── service/
│   │   ├── JdbcUrlBuilder.java
│   │   └── SqlValidator.java
│   └── exception/
│       └── SqlForbiddenException.java
└── src/main/resources/
    └── simplelogger.properties
```

## 安全说明

- 仅允许 SELECT / WITH 查询
- 拦截多语句执行
- 拦截危险关键字：INSERT、UPDATE、DELETE、DROP、TRUNCATE 等
- password 字段不会出现在日志中
- 查询超时 10 秒，连接超时 5 秒
- 结果集最多返回 1000 行

## 常见问题

### Q: 服务启动后无法访问？

检查防火墙是否放行 8095 端口：

```bash
# 检查端口是否监听
netstat -tlnp | grep 8095

# 临时关闭防火墙测试（仅测试环境）
systemctl stop firewalld
```

### Q: 连接数据库超时？

1. 确认 Java 服务与数据库之间的网络连通性：`telnet <db_host> <db_port>`
2. 确认数据库账号密码正确
3. 确认数据库允许该 IP 访问（检查白名单）

### Q: 如何查看完整日志？

日志默认输出到标准输出。如需持久化：

```bash
java -jar target/java-datasource-svc-1.0.0.jar 2>&1 | tee /var/log/datasvc.log
```

### Q: 如何调整日志级别？

编辑 `src/main/resources/simplelogger.properties`：

```properties
org.slf4j.simpleLogger.defaultLogLevel=debug  # 改为 debug 查看更详细日志
```

### Q: 如何执行编译？
```bash
cd /home/dev/java_servive
lsof -ti:8095 | xargs kill -9 2>/dev/null
mvn clean package -DskipTests
java -jar target/java-datasource-svc-1.0.0.jar --server.port=8095
```
