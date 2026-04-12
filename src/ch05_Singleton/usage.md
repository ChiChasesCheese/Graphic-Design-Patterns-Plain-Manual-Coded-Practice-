# Singleton 模式 — 真实应用

核心：**全局只有一个实例，所有人共享同一个对象。**

---

## 1. Spring — Bean 默认作用域

Spring 容器里每个 Bean 默认是 Singleton（单例）。
数据库连接池、配置对象、Service 层对象都是单例。

```java
@Service  // 默认 scope = singleton
public class UserService {
    // Spring 容器里只有一个 UserService 实例
    // 所有注入它的地方拿到的是同一个对象
}

// 需要每次请求新实例时，显式声明 prototype
@Bean
@Scope("prototype")
public ReportGenerator reportGenerator() {
    return new ReportGenerator();
}
```

---

## 2. Node.js — 模块缓存

Node.js 的 `require()` / `import` 天然是 Singleton：
第一次加载执行模块代码，之后所有 `require` 返回同一个缓存对象。

```javascript
// db.js
const { Pool } = require('pg');
const pool = new Pool({ connectionString: process.env.DATABASE_URL });
// 这个 pool 只创建一次，所有 require('db') 的地方共享同一个连接池
module.exports = pool;

// userService.js
const pool = require('./db');  // 拿到同一个 pool 实例

// orderService.js
const pool = require('./db');  // 还是同一个 pool 实例
```

---

## 3. Python — 模块级对象 / `functools.lru_cache`

Python 模块本身就是 Singleton。模块级变量只初始化一次。
`lru_cache` 用 Singleton 缓存计算结果。

```python
# config.py（模块即单例）
import os
DATABASE_URL = os.getenv("DATABASE_URL")
DEBUG = os.getenv("DEBUG", "false").lower() == "true"

# 任何地方 import config 都拿到同一个模块对象
from config import DATABASE_URL

# Redis 客户端单例
import redis
_client = None

def get_redis():
    global _client
    if _client is None:
        _client = redis.Redis.from_url(os.getenv("REDIS_URL"))
    return _client
```

---

## 4. Java — `Runtime` / `System`

JVM 层面的单例，代表进程本身，只能有一个。

```java
Runtime runtime = Runtime.getRuntime();  // 唯一实例
System.out.println(runtime.availableProcessors()); // CPU 核心数
System.out.println(runtime.maxMemory());           // 最大堆内存

// 注册 JVM 关闭钩子（所有线程共享同一个 Runtime）
runtime.addShutdownHook(new Thread(() -> {
    System.out.println("JVM shutting down, cleanup...");
}));
```

---

## 5. JavaScript — Redux Store

Redux 应用只有一个 Store，全局状态集中在一处。
这是前端架构对 Singleton 最典型的应用。

```typescript
// store.ts — 整个应用只创建一次
import { configureStore } from '@reduxjs/toolkit';

export const store = configureStore({
    reducer: {
        user:  userReducer,
        cart:  cartReducer,
        order: orderReducer,
    },
});

// main.tsx — 把 store 注入整个应用树
ReactDOM.createRoot(document.getElementById('root')!).render(
    <Provider store={store}>   {/* 所有组件共享同一个 store */}
        <App />
    </Provider>
);

// 任何组件里访问的都是同一个 store 实例
const user = useSelector(state => state.user);
```

---

## 反模式警告

Singleton 是设计模式里**最容易被滥用**的一个：

```
❌ 把 Singleton 当全局变量用：隐式依赖，难以测试
❌ 多线程环境下的懒加载 Singleton：需要仔细处理线程安全
❌ 用 Singleton 传递状态：应该用依赖注入
```

**现代实践**：不要自己手写 Singleton，交给容器管理（Spring IoC、模块系统）。
容器控制生命周期，测试时可以替换为 Mock。

---

## Python 生态

Python 有多种 Singleton 实现方式，从模块级变量到 `__new__` 再到线程安全版本。

```python
import threading

# 1. 模块级变量 — 最 Pythonic 的 Singleton
# Python 模块本身就是单例：第一次 import 时执行，之后从缓存返回
# config.py
_instance = None

def get_config():
    global _instance
    if _instance is None:
        _instance = {"debug": False, "db_url": "sqlite:///app.db"}
    return _instance

# 2. __new__ 实现 Singleton
class AppConfig:
    _instance = None

    def __new__(cls):
        if cls._instance is None:
            cls._instance = super().__new__(cls)
            cls._instance._initialized = False
        return cls._instance

    def __init__(self):
        if self._initialized:
            return
        self.debug = False
        self.db_url = "sqlite:///app.db"
        self._initialized = True

a = AppConfig()
b = AppConfig()
assert a is b   # True

# 3. 线程安全 Singleton（双重检查锁定）
class ThreadSafeRegistry:
    _instance = None
    _lock = threading.Lock()

    def __new__(cls):
        if cls._instance is None:
            with cls._lock:              # 只在第一次创建时加锁，之后无锁访问
                if cls._instance is None:
                    cls._instance = super().__new__(cls)
        return cls._instance

# 4. Metaclass Singleton — 最通用，可复用
class SingletonMeta(type):
    _instances: dict = {}
    _lock = threading.Lock()

    def __call__(cls, *args, **kwargs):
        if cls not in cls._instances:
            with cls._lock:
                if cls not in cls._instances:
                    cls._instances[cls] = super().__call__(*args, **kwargs)
        return cls._instances[cls]

class DatabasePool(metaclass=SingletonMeta):
    def __init__(self, size: int = 5):
        self.pool = [f"conn_{i}" for i in range(size)]

    def acquire(self) -> str:
        return self.pool.pop() if self.pool else None

p1 = DatabasePool(size=10)
p2 = DatabasePool(size=20)   # size=20 被忽略，返回第一次创建的实例
assert p1 is p2

# 5. functools.cache / lru_cache — 函数级"单例"
from functools import cache

@cache
def get_heavy_resource(name: str):
    """相同参数只执行一次，结果永久缓存（Singleton per key）"""
    print(f"Loading {name}...")
    return {"name": name, "data": "..."}

r1 = get_heavy_resource("model")   # 打印 "Loading model..."
r2 = get_heavy_resource("model")   # 直接返回缓存，不打印
assert r1 is r2
```

> **Python 洞察**：Python 中最常见的 Singleton 不是类，而是**模块**。
> 把共享状态放在模块级变量中，天然线程安全（GIL 保证模块导入的原子性）。
> `functools.cache` 是按参数的 Singleton，适合缓存纯函数的昂贵计算结果。

---

## 关键洞察

> Singleton 解决的是"资源只应存在一份"的问题——连接池、配置、日志器。
> 现代框架（Spring、NestJS、Angular DI）都内置了 Singleton 管理，
> **你几乎不需要自己实现它，只需要理解框架为什么这么设计。**
