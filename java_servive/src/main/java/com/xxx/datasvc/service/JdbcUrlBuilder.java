package com.xxx.datasvc.service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Driver;
import java.util.Properties;

import com.xxx.datasvc.model.DatasourceConfig;

public class JdbcUrlBuilder {

    public static String build(DatasourceConfig ds) {
        String type = ds.getType();
        if (type == null || type.trim().isEmpty()) {
            throw new IllegalArgumentException("type 不能为空");
        }

        String host = ds.getHost();
        String port = ds.getPort();
        String database = ds.getDatabase();

        if (host == null || port == null || database == null) {
            throw new IllegalArgumentException("host、port、database 不能为空");
        }

        switch (type.toLowerCase()) {
            case "mysql":
                return String.format("jdbc:mysql://%s:%s/%s?connectTimeout=5000&socketTimeout=30000",
                    host, port, database);
            case "postgresql":
                return String.format("jdbc:postgresql://%s:%s/%s?connectTimeout=5&socketTimeout=30",
                    host, port, database);
            case "oracle":
                return String.format("jdbc:oracle:thin:@%s:%s:%s",
                    host, port, database);
            case "sqlserver":
                return String.format("jdbc:sqlserver://%s:%s;databaseName=%s;loginTimeout=5",
                    host, port, database);
            case "clickhouse":
                return String.format("jdbc:clickhouse://%s:%s/%s?connection_timeout=5000",
                    host, port, database);
            case "hetu":
                return String.format("jdbc:lk://%s:%s/%s",
                    host, port, database);
            default:
                throw new IllegalArgumentException("不支持的数据源类型: " + type);
        }
    }

    public static Connection getConnection(DatasourceConfig ds) throws SQLException {
        registerDriver(ds.getType());
        String url = build(ds);
        String type = ds.getType().toLowerCase();

        if ("hetu".equals(type)) {
            Properties properties = new Properties();
            properties.setProperty("user", ds.getUsername());
            properties.setProperty("password", ds.getPassword());
            properties.setProperty("SSL", "false");
            return DriverManager.getConnection(url, properties);
        } else if ("oracle".equals(type)) {
            Properties properties = new Properties();
            properties.setProperty("user", ds.getUsername());
            properties.setProperty("password", ds.getPassword());
            return DriverManager.getConnection(url, properties);
        } else {
            return DriverManager.getConnection(url, ds.getUsername(), ds.getPassword());
        }
    }


    private static void registerDriver(String type) throws SQLException {
        if (type == null) {
            return;
        }

        String driverClassName;
        switch (type.toLowerCase()) {
            case "hetu":
                driverClassName = "io.hetu.core.jdbc.OpenLooKengDriver";
                break;
            case "mysql":
                driverClassName = "com.mysql.cj.jdbc.Driver";
                break;
            case "postgresql":
                driverClassName = "org.postgresql.Driver";
                break;
            default:
                return;
        }

        try {
            Class<?> driverClass = Class.forName(driverClassName);
            Driver driver = (Driver) driverClass.getDeclaredConstructor().newInstance();
            DriverManager.registerDriver(driver);
        } catch (Exception e) {
            throw new SQLException("加载 JDBC 驱动失败: " + driverClassName, e);
        }
    }

}
