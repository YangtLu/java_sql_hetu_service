package com.xxx.datasvc.controller;

import com.xxx.datasvc.model.DatasourceConfig;
import com.xxx.datasvc.model.QueryRequest;
import com.xxx.datasvc.service.JdbcUrlBuilder;
import com.xxx.datasvc.service.SqlValidator;
import io.javalin.http.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DatasourceController {

    private static final Logger log = LoggerFactory.getLogger(DatasourceController.class);

    public void testConnection(Context ctx) {
        DatasourceConfig ds = ctx.bodyAsClass(DatasourceConfig.class);
        String url = JdbcUrlBuilder.build(ds);

        try (Connection conn = JdbcUrlBuilder.getConnection(ds)) {
            String versionSql = getVersionSql(ds.getType());
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(versionSql)) {

                String version = rs.next() ? rs.getString(1) : "unknown";
                log.info("test connection | type={} host={} db={} user={} success=true version={}",
                    ds.getType(), ds.getHost(), ds.getDatabase(), ds.getUsername(), version);
                ctx.json(Map.of(
                    "success", true,
                    "message", "连接成功",
                    "server_version", version
                ));
            }
        } catch (SQLException e) {
            log.warn("test connection failed: type={} host={} db={} user={} err={}",
                ds.getType(), ds.getHost(), ds.getDatabase(), ds.getUsername(), e.getMessage());
            ctx.json(Map.of(
                "success", false,
                "message", "连接失败：" + e.getMessage()
            ));
        }
    }

    public void query(Context ctx) {
        QueryRequest req = ctx.bodyAsClass(QueryRequest.class);

        SqlValidator.validateReadonly(req.getSql());

        DatasourceConfig ds = req.getDatasource();

        try (Connection conn = JdbcUrlBuilder.getConnection(ds);
             PreparedStatement ps = conn.prepareStatement(req.getSql())) {

            ps.setQueryTimeout(10);

            long start = System.currentTimeMillis();
            try (ResultSet rs = ps.executeQuery()) {
                ResultSetMetaData md = rs.getMetaData();
                int colCount = md.getColumnCount();
                List<String> columns = new ArrayList<>();
                for (int i = 1; i <= colCount; i++) {
                    columns.add(md.getColumnLabel(i));
                }

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

                log.info("query | type={} host={} db={} user={} sql={} elapsed={}ms rows={} success=true",
                    ds.getType(), ds.getHost(), ds.getDatabase(), ds.getUsername(),
                    req.getSql().length() > 200 ? req.getSql().substring(0, 200) : req.getSql(),
                    elapsed, rows.size());

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

    private String getVersionSql(String type) {
        switch (type.toLowerCase()) {
            case "oracle":
                return "SELECT banner FROM v$version WHERE ROWNUM=1";
            case "sqlserver":
                return "SELECT @@VERSION";
            default:
                return "SELECT VERSION()";
        }
    }
}
