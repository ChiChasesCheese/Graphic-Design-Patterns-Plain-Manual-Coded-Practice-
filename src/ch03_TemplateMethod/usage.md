# Template Method 模式 — 真实应用

核心：**父类锁定算法骨架，子类填充变化步骤。框架控制流程，用户填空。**

---

## 1. Spring — `JdbcTemplate`

名字直接带 Template。连接、异常处理、资源释放是骨架，你只填"怎么处理一行数据"。

```java
// 你写的部分（变化点）
List<User> users = jdbcTemplate.query(
    "SELECT id, name FROM users WHERE active = ?",
    (rs, rowNum) -> new User(rs.getLong("id"), rs.getString("name")), // ← 钩子
    true
);

// Spring 内部骨架（你不需要写）：
// 1. 获取连接
// 2. 创建 PreparedStatement
// 3. 执行查询
// 4. 遍历 ResultSet，每行调用你的 RowMapper（钩子）
// 5. 关闭资源（即使抛异常也会关闭）
```

---

## 2. Android — Activity / Fragment 生命周期

Android 框架是教科书级别的 Template Method 应用。
框架控制 `onCreate → onStart → onResume → onPause → onStop → onDestroy` 的顺序，
你只 override 关心的钩子。

```kotlin
class MainActivity : AppCompatActivity() {

    // 你只填这几个钩子，其余生命周期框架处理
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        // 初始化 UI
    }

    override fun onResume() {
        super.onResume()
        // 恢复相机、传感器等资源
    }

    override fun onPause() {
        super.onPause()
        // 释放资源，保存草稿
    }
}
```

---

## 3. React — `useEffect` 清理函数

React 的 `useEffect` 是函数式的 Template Method：
框架控制"挂载执行 → 依赖变化重新执行 → 卸载清理"的骨架，
你填两个钩子：effect 本身 和 cleanup。

```typescript
useEffect(() => {
    // 钩子1：setup（挂载或依赖变化时执行）
    const subscription = websocket.subscribe(userId, onMessage);

    // 钩子2：cleanup（卸载或下次 effect 前执行）
    return () => {
        subscription.unsubscribe();  // React 框架保证这个一定会被调用
    };
}, [userId]);  // 依赖数组

// React 内部骨架：
// mount   → 执行 effect
// update  → 执行旧 cleanup → 执行新 effect
// unmount → 执行 cleanup
```

---

## 4. Python — `unittest.TestCase`

JUnit 的 Python 版，setUp/tearDown 是钩子，测试框架保证执行顺序。

```python
class UserServiceTest(unittest.TestCase):

    def setUp(self):           # 钩子：每个测试前执行
        self.db = create_test_db()
        self.service = UserService(self.db)

    def tearDown(self):        # 钩子：每个测试后执行（即使测试失败）
        self.db.rollback()
        self.db.close()

    def test_create_user(self):
        user = self.service.create("Alice")
        self.assertEqual(user.name, "Alice")

# unittest 框架骨架：
# for each test method:
#     setUp()  ← 钩子
#     test_*() ← 钩子
#     tearDown() ← 钩子（try/finally 保证执行）
```

---

## 5. Webpack — Loader 链

Webpack 的 loader 执行流程是 Template Method：
框架控制"读文件 → 依次经过 loader → 输出 JS"的骨架，
每个 loader 是一个钩子，只关心自己的转换逻辑。

```javascript
// babel-loader（简化）
module.exports = function babelLoader(source) {
    // 这个函数就是"钩子"：接收上一步的代码，返回转换后的代码
    const result = babel.transformSync(source, this.getOptions());
    return result.code;
};

// webpack.config.js
module: {
    rules: [{
        test: /\.tsx?$/,
        use: ['babel-loader', 'ts-loader']  // 骨架：从右到左依次调用钩子
    }]
}
// 执行顺序：ts-loader(source) → babel-loader(tsResult) → 最终 JS
```

---

## Python 生态

Python 用 `abc` 模块实现模板方法，并提供了 `contextlib.contextmanager` 这种函数式的"模板"替代方案。

```python
from abc import ABC, abstractmethod

# 1. abc.ABC + @abstractmethod：标准模板方法实现
class DataProcessor(ABC):
    """模板类：定义处理骨架"""

    def process(self, source: str) -> list:   # 模板方法（骨架）
        raw = self.read(source)
        cleaned = self.clean(raw)
        return self.transform(cleaned)

    @abstractmethod
    def read(self, source: str) -> str: ...   # 钩子：子类必须实现

    def clean(self, data: str) -> str:        # 钩子：有默认实现，子类可选覆盖
        return data.strip()

    @abstractmethod
    def transform(self, data: str) -> list: ...

class CSVProcessor(DataProcessor):
    def read(self, source: str) -> str:
        with open(source) as f:
            return f.read()

    def transform(self, data: str) -> list:
        rows = data.splitlines()
        return [row.split(",") for row in rows]

# DataProcessor()  # 直接实例化会抛 TypeError：Can't instantiate abstract class

# 2. contextlib.contextmanager — 函数式模板方法
# 模板：setup → yield（用户代码） → teardown
from contextlib import contextmanager

@contextmanager
def db_transaction(conn):
    """模板骨架：事务管理"""
    cursor = conn.cursor()
    try:
        yield cursor           # ← 用户在这里填写业务逻辑
        conn.commit()          # 成功后提交
    except Exception:
        conn.rollback()        # 失败后回滚
        raise
    finally:
        cursor.close()

# with 语句 = 模板方法的调用端
with db_transaction(conn) as cur:
    cur.execute("INSERT INTO orders VALUES (?)", (order_id,))

# 3. __init_subclass__：注册子类时自动验证钩子实现
class Plugin(ABC):
    _plugins: dict[str, type] = {}

    def __init_subclass__(cls, name: str, **kwargs):
        super().__init_subclass__(**kwargs)
        Plugin._plugins[name] = cls

    @abstractmethod
    def run(self) -> None: ...

class MyPlugin(Plugin, name="my_plugin"):
    def run(self):
        print("MyPlugin running")

# 注册表自动填充
print(Plugin._plugins)   # {'my_plugin': <class 'MyPlugin'>}

# 4. dataclasses + __post_init__ — 数据类的模板钩子
from dataclasses import dataclass, field

@dataclass
class BaseConfig:
    host: str
    port: int

    def __post_init__(self):          # 模板钩子：__init__ 之后自动调用
        self.validate()               # 子类可以覆盖 validate()

    def validate(self):
        if not (1 <= self.port <= 65535):
            raise ValueError(f"Invalid port: {self.port}")

@dataclass
class SSLConfig(BaseConfig):
    cert_path: str = ""

    def validate(self):               # 覆盖模板钩子，增加额外验证
        super().validate()
        if not self.cert_path:
            raise ValueError("SSL config requires cert_path")
```

> **Python 洞察**：`contextmanager` 是 Python 最优雅的模板方法实现——
> `yield` 之前是 setup，`yield` 之后是 teardown，中间的 `with` 块是"用户填空"。
> 比继承更轻量，适合一次性的资源管理模板。

---

## 关键洞察

> 你每次写 `extends SomeAbstractClass` 并 override 几个方法，
> 或者给框架传一个回调/lambda，**本质上都是在使用 Template Method**。
> 这是框架设计最普遍的手法：框架写骨架，用户填空。
