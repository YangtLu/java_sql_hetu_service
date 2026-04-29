package com.xxx.datasvc.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * 根据数据源类型构造 JDBC 连接，并提供统一的查询输出方式。
 */
public class JdbcUrlBuilder {

    /**
     * 通过数据源响应信息建立连接。
     * 必填字段：type/host/port/database/user/password。
     */
    public Connection getConnection(Map<String, String> datasource) throws Exception {
        String type = required(datasource, "type").toLowerCase(Locale.ROOT);
        String host = required(datasource, "host");
        String port = required(datasource, "port");
        String database = required(datasource, "database");

        String url = buildUrl(type, host, port, database);
        Properties properties = buildProperties(type, datasource);
        return DriverManager.getConnection(url, properties);
    }

    /**
     * 统一执行并打印单列结果（如 count(1)）。
     */
    public void queryAndPrint(Connection conn, String sql) throws Exception {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                System.out.println("Count: " + rs.getInt(1));
            }
        }
    }

    private String buildUrl(String type, String host, String port, String database) {
        switch (type) {
            case "hetu":
                // 按 Olk 示例：jdbc:lk://host:port/database
                return String.format("jdbc:lk://%s:%s/%s", host, port, database);
            case "mysql":
                return String.format("jdbc:mysql://%s:%s/%s", host, port, database);
            case "postgresql":
                return String.format("jdbc:postgresql://%s:%s/%s", host, port, database);
            default:
                throw new IllegalArgumentException("Unsupported datasource type: " + type);
        }
    }

    private Properties buildProperties(String type, Map<String, String> datasource) {
        Properties properties = new Properties();
        properties.setProperty("user", required(datasource, "user"));
        properties.setProperty("password", required(datasource, "password"));

        if ("hetu".equalsIgnoreCase(type)) {
            // 对齐 Olk getConnection：关闭 SSL
            properties.setProperty("SSL", datasource.getOrDefault("SSL", "false"));
        }

        return properties;
    }

    private String required(Map<String, String> datasource, String key) {
        String value = datasource.get(key);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Missing required datasource field: " + key);
        }
        return value;
    }
}
