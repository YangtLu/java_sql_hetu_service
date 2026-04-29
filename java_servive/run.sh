#!/bin/bash

set -e

PROJECT_DIR="$(cd "$(dirname "$0")" && pwd)"
LIB_DIR="$PROJECT_DIR/src/main/lib"
BUILD_DIR="$PROJECT_DIR/build/classes"

echo "=== 下载依赖 ==="

if [ ! -f "$LIB_DIR/javalin-6.3.0.jar" ]; then
    echo "下载 javalin-6.3.0.jar..."
    curl -sL -o "$LIB_DIR/javalin-6.3.0.jar" "https://repo1.maven.org/maven2/io/javalin/javalin/6.3.0/javalin-6.3.0.jar"
fi

if [ ! -f "$LIB_DIR/jackson-databind-2.17.0.jar" ]; then
    echo "下载 jackson-databind-2.17.0.jar..."
    curl -sL -o "$LIB_DIR/jackson-databind-2.17.0.jar" "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-databind/2.17.0/jackson-databind-2.17.0.jar"
fi

if [ ! -f "$LIB_DIR/jackson-core-2.17.0.jar" ]; then
    echo "下载 jackson-core-2.17.0.jar..."
    curl -sL -o "$LIB_DIR/jackson-core-2.17.0.jar" "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-core/2.17.0/jackson-core-2.17.0.jar"
fi

if [ ! -f "$LIB_DIR/jackson-annotations-2.17.0.jar" ]; then
    echo "下载 jackson-annotations-2.17.0.jar..."
    curl -sL -o "$LIB_DIR/jackson-annotations-2.17.0.jar" "https://repo1.maven.org/maven2/com/fasterxml/jackson/core/jackson-annotations/2.17.0/jackson-annotations-2.17.0.jar"
fi

if [ ! -f "$LIB_DIR/slf4j-api-2.0.13.jar" ]; then
    echo "下载 slf4j-api-2.0.13.jar..."
    curl -sL -o "$LIB_DIR/slf4j-api-2.0.13.jar" "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/2.0.13/slf4j-api-2.0.13.jar"
fi

if [ ! -f "$LIB_DIR/slf4j-simple-2.0.13.jar" ]; then
    echo "下载 slf4j-simple-2.0.13.jar..."
    curl -sL -o "$LIB_DIR/slf4j-simple-2.0.13.jar" "https://repo1.maven.org/maven2/org/slf4j/slf4j-simple/2.0.13/slf4j-simple-2.0.13.jar"
fi

if [ ! -f "$LIB_DIR/mysql-connector-j-8.3.0.jar" ]; then
    echo "下载 mysql-connector-j-8.3.0.jar..."
    curl -sL -o "$LIB_DIR/mysql-connector-j-8.3.0.jar" "https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.3.0/mysql-connector-j-8.3.0.jar"
fi

if [ ! -f "$LIB_DIR/postgresql-42.7.3.jar" ]; then
    echo "下载 postgresql-42.7.3.jar..."
    curl -sL -o "$LIB_DIR/postgresql-42.7.3.jar" "https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.3/postgresql-42.7.3.jar"
fi

echo "=== 编译 ==="
mkdir -p "$BUILD_DIR"

CLASSPATH=$(find "$LIB_DIR" -name "*.jar" | tr '\n' ':')

javac -d "$BUILD_DIR" -cp "$CLASSPATH" \
    src/main/java/com/xxx/datasvc/Application.java \
    src/main/java/com/xxx/datasvc/controller/DatasourceController.java \
    src/main/java/com/xxx/datasvc/model/DatasourceConfig.java \
    src/main/java/com/xxx/datasvc/model/QueryRequest.java \
    src/main/java/com/xxx/datasvc/service/JdbcUrlBuilder.java \
    src/main/java/com/xxx/datasvc/service/SqlValidator.java \
    src/main/java/com/xxx/datasvc/exception/SqlForbiddenException.java

if [ $? -eq 0 ]; then
    echo "编译成功！"
    echo ""
    echo "=== 运行服务 ==="
    echo "java -cp \"$BUILD_DIR:$CLASSPATH\" com.xxx.datasvc.Application"
    echo "CLASSPATH=$CLASSPATH"
    echo ""
    java -cp "$BUILD_DIR:$CLASSPATH" com.xxx.datasvc.Application
else
    echo "编译失败！"
    exit 1
fi
