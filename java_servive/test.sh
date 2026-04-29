#!/bin/bash

set -e

BASE_URL="http://localhost:8095"
PASS=0
FAIL=0
TOTAL=0

run_test() {
    local name="$1"
    local expected_http="$2"
    local url="$3"
    local data="$4"

    TOTAL=$((TOTAL + 1))

    echo "========================================"
    echo "测试 #$TOTAL: $name"
    echo "========================================"

    response=$(curl -s -w "\n%{http_code}" -X POST "$url" \
        -H "Content-Type: application/json" \
        -d "$data")

    http_code=$(echo "$response" | tail -1)
    body=$(echo "$response" | sed '$d')

    echo "HTTP 状态码: $http_code"
    echo "响应体: $body"

    if [ "$http_code" = "$expected_http" ]; then
        echo "结果: PASS"
        PASS=$((PASS + 1))
    else
        echo "结果: FAIL (期望 HTTP $expected_http)"
        FAIL=$((FAIL + 1))
    fi
    echo ""
}

echo "============================================"
echo "  Java 数据查询服务 - 接口测试"
echo "============================================"
echo ""

# 检查服务是否运行
echo "检查服务是否运行..."
if ! curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/api/datasource/test" | grep -q "405\|400\|404\|200"; then
    echo "错误: 服务未运行，请先启动服务: ./run.sh"
    exit 1
fi
echo "服务正常运行中"
echo ""

# 测试 1: 不支持的数据源类型 → 400
run_test "type=mongodb（不支持的类型）" \
    "400" \
    "$BASE_URL/api/datasource/test" \
    '{"type":"mongodb","host":"127.0.0.1","port":"3306","database":"test","username":"root","password":"root"}'

# 测试 2: 缺少必填字段 → 400
run_test "缺少 host 字段" \
    "400" \
    "$BASE_URL/api/datasource/test" \
    '{"type":"mysql","port":"3306","database":"test","username":"root","password":"root"}'

# 测试 3: 不可达 IP → 200 + success:false
run_test "不可达 IP（连接超时）" \
    "200" \
    "$BASE_URL/api/datasource/test" \
    '{"type":"mysql","host":"192.168.255.255","port":"3306","database":"test","username":"root","password":"root"}'

# 测试 4: DELETE 语句 → 403
run_test "DELETE 语句（应被拦截）" \
    "403" \
    "$BASE_URL/api/datasource/query" \
    '{"sql":"DELETE FROM t","datasource":{"type":"mysql","host":"127.0.0.1","port":"3306","database":"test","username":"root","password":"root"}}'

# 测试 5: DROP 语句 → 403
run_test "DROP 语句（应被拦截）" \
    "403" \
    "$BASE_URL/api/datasource/query" \
    '{"sql":"SELECT 1; DROP TABLE t","datasource":{"type":"mysql","host":"127.0.0.1","port":"3306","database":"test","username":"root","password":"root"}}'

# 测试 6: INSERT 语句 → 403
run_test "INSERT 语句（应被拦截）" \
    "403" \
    "$BASE_URL/api/datasource/query" \
    '{"sql":"INSERT INTO t VALUES(1)","datasource":{"type":"mysql","host":"127.0.0.1","port":"3306","database":"test","username":"root","password":"root"}}'

# 测试 7: 空 SQL → 403
run_test "空 SQL" \
    "403" \
    "$BASE_URL/api/datasource/query" \
    '{"sql":"","datasource":{"type":"mysql","host":"127.0.0.1","port":"3306","database":"test","username":"root","password":"root"}}'

# 测试 8: UPDATE 语句 → 403
run_test "UPDATE 语句（应被拦截）" \
    "403" \
    "$BASE_URL/api/datasource/query" \
    '{"sql":"UPDATE t SET a=1","datasource":{"type":"mysql","host":"127.0.0.1","port":"3306","database":"test","username":"root","password":"root"}}'

# 测试 9: 注释中包含危险语句 → 403
run_test "注释中包含 DROP" \
    "403" \
    "$BASE_URL/api/datasource/query" \
    '{"sql":"SELECT 1 /* DROP TABLE t */","datasource":{"type":"mysql","host":"127.0.0.1","port":"3306","database":"test","username":"root","password":"root"}}'

# 测试 10: 错误密码 → 200 + success:false
run_test "错误密码" \
    "200" \
    "$BASE_URL/api/datasource/test" \
    '{"type":"mysql","host":"127.0.0.1","port":"3306","database":"test","username":"root","password":"wrong_password"}'

# 测试 11: 查询不可达数据库 → 200 + success:false
run_test "查询不可达数据库" \
    "200" \
    "$BASE_URL/api/datasource/query" \
    '{"sql":"SELECT 1","datasource":{"type":"mysql","host":"192.168.255.255","port":"3306","database":"test","username":"root","password":"root"}}'

echo "============================================"
echo "  测试汇总"
echo "============================================"
echo "总计: $TOTAL"
echo "通过: $PASS"
echo "失败: $FAIL"
echo "============================================"

if [ $FAIL -eq 0 ]; then
    echo "全部通过！"
    exit 0
else
    echo "存在失败用例！"
    exit 1
fi
