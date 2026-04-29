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

        String cleaned = sql
            .replaceAll("/\\*[\\s\\S]*?\\*/", " ")
            .replaceAll("--[^\\n]*", " ")
            .replaceAll("#[^\\n]*", " ")
            .trim();

        if (cleaned.endsWith(";")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1).trim();
        }

        if (cleaned.contains(";")) {
            throw new SqlForbiddenException("不允许多条 SQL");
        }

        String upper = cleaned.toUpperCase();
        if (!upper.startsWith("SELECT") && !upper.startsWith("WITH")) {
            throw new SqlForbiddenException("只允许 SELECT / WITH 查询");
        }

        for (String kw : FORBIDDEN) {
            if (upper.matches("(?s).*\\b" + kw + "\\b.*")) {
                throw new SqlForbiddenException("SQL 包含禁用关键字 " + kw);
            }
        }
    }
}
