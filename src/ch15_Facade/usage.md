# Facade 模式 — 真实应用

核心：**为复杂子系统提供简单统一的入口，隐藏内部复杂性。**

---

## 1. Axios — HTTP 请求门面

Axios 是对浏览器 `XMLHttpRequest` 和 Node.js `http` 模块的 Facade，
把复杂的底层 API 包装成简洁的接口。

```typescript
// 没有 Facade 时（原生 XMLHttpRequest）
const xhr = new XMLHttpRequest();
xhr.open('POST', '/api/users');
xhr.setRequestHeader('Content-Type', 'application/json');
xhr.setRequestHeader('Authorization', `Bearer ${token}`);
xhr.onload = () => {
    if (xhr.status >= 200 && xhr.status < 300) {
        const data = JSON.parse(xhr.responseText);
        // 处理数据...
    } else {
        // 处理错误...
    }
};
xhr.onerror = () => { /* 网络错误 */ };
xhr.send(JSON.stringify({ name: 'Alice' }));

// 有 Facade 之后（Axios）
const { data } = await axios.post('/api/users', { name: 'Alice' });
// 自动处理：序列化、Content-Type、状态码检查、错误处理、Promise 化
```

---

## 2. Spring — `JdbcTemplate` / `RedisTemplate`

Spring 的各种 Template 类都是 Facade，把复杂的资源管理包装成简单操作。

```java
// 没有 Facade 时（原生 JDBC）
Connection conn = null;
PreparedStatement ps = null;
ResultSet rs = null;
try {
    conn = dataSource.getConnection();
    ps = conn.prepareStatement("SELECT * FROM users WHERE id = ?");
    ps.setLong(1, userId);
    rs = ps.executeQuery();
    if (rs.next()) return mapRow(rs);
} catch (SQLException e) {
    throw new RuntimeException(e);
} finally {
    if (rs != null) try { rs.close(); } catch (SQLException ignored) {}
    if (ps != null) try { ps.close(); } catch (SQLException ignored) {}
    if (conn != null) try { conn.close(); } catch (SQLException ignored) {}
}

// 有 Facade 之后（JdbcTemplate）
return jdbcTemplate.queryForObject(
    "SELECT * FROM users WHERE id = ?",
    this::mapRow,
    userId
);
```

---

## 3. AWS SDK — 高层 API

AWS SDK 提供两层 API：低层（Protocol/Serialization）和高层 Facade。
大多数开发者只需要 Facade 层。

```typescript
// 低层 API（你不想写的）：手动处理 S3 协议细节
const command = new PutObjectCommand({
    Bucket: bucket, Key: key, Body: buffer,
    ContentType: 'application/json',
    ServerSideEncryption: 'AES256',
});
await s3Client.send(command);

// AWS SDK v3 高层 Facade（S3 Transfer Manager）
import { Upload } from '@aws-sdk/lib-storage';

const upload = new Upload({
    client: s3Client,
    params: { Bucket: bucket, Key: key, Body: stream },
});
// Facade 自动处理：分片上传、重试、进度追踪
upload.on('httpUploadProgress', (progress) => console.log(progress));
await upload.done();
```

---

## 4. Python — `requests` 库

`requests` 是对 Python 标准库 `urllib` 的 Facade，
让 HTTP 请求从繁琐变成直观。

```python
# 没有 Facade 时（原生 urllib）
import urllib.request, urllib.parse, json

data = json.dumps({'name': 'Alice'}).encode()
req = urllib.request.Request(
    'https://api.example.com/users',
    data=data,
    headers={'Content-Type': 'application/json',
             'Authorization': f'Bearer {token}'},
    method='POST'
)
with urllib.request.urlopen(req) as response:
    result = json.loads(response.read().decode())

# 有 Facade 之后（requests）
result = requests.post(
    'https://api.example.com/users',
    json={'name': 'Alice'},
    headers={'Authorization': f'Bearer {token}'}
).json()
```

---

## 5. React Query / TanStack Query — 数据获取门面

TanStack Query 是对"数据获取 + 缓存 + 同步 + 错误处理"复杂状态机的 Facade。

```typescript
// 没有 Facade 时（手动管理所有状态）
const [data, setData] = useState(null);
const [loading, setLoading] = useState(false);
const [error, setError] = useState(null);

useEffect(() => {
    setLoading(true);
    fetch(`/api/users/${id}`)
        .then(r => r.json())
        .then(setData)
        .catch(setError)
        .finally(() => setLoading(false));
    // 还没处理：缓存、重新验证、后台刷新、竞态条件...
}, [id]);

// 有 Facade 之后（TanStack Query）
const { data, isLoading, error } = useQuery({
    queryKey: ['user', id],
    queryFn: () => fetch(`/api/users/${id}`).then(r => r.json()),
    // 自动处理：缓存、后台重新验证、重试、竞态条件、窗口聚焦刷新
});
```

---

## Python 生态

Python 的 Facade 最常见形式是**模块级函数** + `contextlib` 资源管理，把复杂的初始化/清理逻辑隐藏起来。

```python
# 1. 模块级 Facade（最 Pythonic）
# database.py — 把复杂的连接池、ORM、迁移逻辑封装成简单函数

import contextlib
from typing import Generator
import sqlite3

# 子系统（内部细节）
_connection_pool: list[sqlite3.Connection] = []
_pool_size = 5

def _init_pool(db_path: str) -> None:
    for _ in range(_pool_size):
        conn = sqlite3.connect(db_path)
        conn.row_factory = sqlite3.Row
        _connection_pool.append(conn)

def _get_connection() -> sqlite3.Connection:
    if not _connection_pool:
        raise RuntimeError("Connection pool exhausted")
    return _connection_pool.pop()

def _return_connection(conn: sqlite3.Connection) -> None:
    _connection_pool.append(conn)

# Facade：简单的公共接口
@contextlib.contextmanager
def get_db() -> Generator[sqlite3.Cursor, None, None]:
    """Facade：调用方只需要 with get_db() as cursor，不需要了解连接池细节"""
    conn = _get_connection()
    cursor = conn.cursor()
    try:
        yield cursor
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        cursor.close()
        _return_connection(conn)

# 使用：极简接口，所有复杂性隐藏
with get_db() as cursor:
    cursor.execute("SELECT * FROM users WHERE active = 1")
    users = cursor.fetchall()

# 2. httpx / requests — HTTP 客户端 Facade
import httpx

# httpx 是 HTTP 协议复杂性的 Facade
# 内部处理：连接池、TLS 握手、重定向、超时、编解码...
client = httpx.Client(
    base_url="https://api.example.com",
    headers={"Authorization": "Bearer token"},
    timeout=30.0,
)

# 调用方只需要这一行
response = client.get("/users", params={"page": 1})

# 3. pathlib.Path — 文件系统操作 Facade
from pathlib import Path

# Path 封装了 os、os.path、shutil、open 等多个模块的功能
p = Path("data/output")
p.mkdir(parents=True, exist_ok=True)          # 相当于 os.makedirs(exist_ok=True)
p.joinpath("result.json").write_text("{}")    # 相当于 open(..., "w").write(...)
files = list(p.glob("*.json"))                # 相当于 os.listdir + fnmatch

# 4. logging — 日志子系统的 Facade
import logging

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(name)s: %(message)s",
)
# 调用方只需要这一行，隐藏了 Handler、Formatter、Filter 等复杂配置
logging.info("Application started")

# 5. subprocess.run — 进程管理 Facade（Python 3.5+）
import subprocess

result = subprocess.run(
    ["git", "log", "--oneline", "-5"],
    capture_output=True, text=True, check=True
)
# Facade 隐藏了：Popen、管道、编码、信号处理、返回码检查...
print(result.stdout)
```

> **Python 洞察**：Python 标准库本身大量使用 Facade——
> `pathlib` 是文件系统 API 的 Facade，`logging` 是日志子系统的 Facade，
> `subprocess.run` 是进程管理的 Facade。
> `contextlib.contextmanager` 特别适合把"需要 setup/teardown 的资源"封装成简洁的 Facade。

---

## 关键洞察

> Facade 是最"友好"的设计模式，它不改变任何功能，只改变易用性。
> 几乎每个流行库的核心价值都是 Facade：
> "把你不想写的那 50 行代码，变成 1 行调用。"
> 判断标准：**用了这个库之后，代码变简单了吗？如果是，它就是个 Facade。**
