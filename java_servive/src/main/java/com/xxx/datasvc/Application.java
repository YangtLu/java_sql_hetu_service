package com.xxx.datasvc;

import java.util.Map;

import com.xxx.datasvc.controller.DatasourceController;
import com.xxx.datasvc.exception.SqlForbiddenException;

import io.javalin.Javalin;

public class Application {
    public static void main(String[] args) {
        int port = parsePort(args);
        DatasourceController controller = new DatasourceController();

        Javalin app = Javalin.create(config -> {
            config.http.defaultContentType = "application/json";
        })
        .post("/api/datasource/test", controller::testConnection)
        .post("/api/datasource/query", controller::query)
        .exception(SqlForbiddenException.class, (e, ctx) -> {
            ctx.status(403).json(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        })
        .exception(IllegalArgumentException.class, (e, ctx) -> {
            ctx.status(400).json(Map.of(
                "success", false,
                "message", e.getMessage()
            ));
        })
        .start(port);

        System.out.println("Data query service listening on :" + port);
    }

    private static int parsePort(String[] args) {
        for (String arg : args) {
            if (arg.startsWith("--server.port=")) {
                try {
                    return Integer.parseInt(arg.substring("--server.port=".length()));
                } catch (NumberFormatException e) {
                    System.err.println("无效的端口号: " + arg);
                }
            }
        }
        return 8095;
    }
}
