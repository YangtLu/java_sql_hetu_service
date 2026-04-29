package com.xxx.datasvc.model;

public class QueryRequest {
    private String sql;
    private DatasourceConfig datasource;

    public String getSql() { return sql; }
    public void setSql(String sql) { this.sql = sql; }

    public DatasourceConfig getDatasource() { return datasource; }
    public void setDatasource(DatasourceConfig datasource) { this.datasource = datasource; }
}
