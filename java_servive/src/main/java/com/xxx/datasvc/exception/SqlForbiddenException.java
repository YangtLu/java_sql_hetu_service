package com.xxx.datasvc.exception;

public class SqlForbiddenException extends RuntimeException {
    public SqlForbiddenException(String msg) { super(msg); }
}
