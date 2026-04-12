# Prototype 模式 — 真实应用

核心：**通过复制已有对象来创建新对象，而不是从零 new 一个。**

---

## 1. JavaScript — 原型链（语言级别内置）

JavaScript 的对象系统本身就是 Prototype 模式。
每个对象有一个 `[[Prototype]]`，属性查找沿链向上。

```javascript
const animal = {
    speak() { return `${this.name} makes a sound`; }
};

// Object.create：以 animal 为原型创建新对象（Prototype 模式的直接体现）
const dog = Object.create(animal);
dog.name = 'Rex';
console.log(dog.speak()); // "Rex makes a sound"
// dog 自身没有 speak()，沿原型链找到 animal.speak()

// class 语法糖背后也是原型链
class Dog extends Animal { ... }
// Dog.prototype.__proto__ === Animal.prototype
```

---

## 2. React — `React.cloneElement`

复制一个已有的 React 元素并修改部分 props，
常见于组件库内部（如给子组件注入额外 props）。

```typescript
// 组件库内部：给所有子元素注入 theme prop，不需要每个子组件单独处理
function ThemeProvider({ theme, children }) {
    return React.Children.map(children, child =>
        React.cloneElement(child, { theme })  // 克隆 + 注入新 prop
        //     ↑ Prototype 模式：复制已有元素，修改部分属性
    );
}

// 使用
<ThemeProvider theme="dark">
    <Button>OK</Button>      // 自动获得 theme="dark"
    <Input placeholder="..." /> // 自动获得 theme="dark"
</ThemeProvider>
```

---

## 3. Java — `Object.clone()` / 拷贝构造器

Java 标准库里 `clone()` 是 Prototype 的直接实现。
更现代的做法是拷贝构造器或 record 的 `with` 语法。

```java
// Java 14+ record 的不可变克隆（推荐）
record Config(String host, int port, boolean ssl) {}

Config base = new Config("localhost", 5432, false);
// "克隆并修改"：用 wither 模式
Config prod = new Config(base.host(), base.port(), true);  // 只改 ssl

// Java 16+ record 可以用 compact constructor 简化
// Immutable + Prototype = 安全的对象复制
```

---

## 4. Redux / Immer — 不可变状态更新

Redux 的 state 更新本质是 Prototype：复制旧 state，修改需要变化的部分，返回新对象。
Immer 让这个过程更自然。

```typescript
import produce from 'immer';  // Immer 是最流行的不可变更新库

const nextState = produce(currentState, draft => {
    // draft 是 currentState 的"草稿副本"（结构共享，不是完整复制）
    draft.user.name = 'Alice';
    draft.cart.items.push({ id: 1, qty: 2 });
    // Immer 内部：只复制被修改的路径，其余节点共享引用（structural sharing）
});

// currentState 不可变，nextState 是新对象
// React/Redux 通过引用比较检测变化：nextState !== currentState → 触发重渲染
```

---

## 5. Python — `copy.deepcopy` / dataclass `replace`

配置对象、测试 fixture 的复制场景。

```python
from dataclasses import dataclass, replace
from copy import deepcopy

@dataclass(frozen=True)  # frozen=不可变
class ServerConfig:
    host: str
    port: int
    timeout: int = 30

base_config = ServerConfig(host="db.prod.com", port=5432)

# dataclasses.replace：克隆并覆盖指定字段（Prototype 模式）
test_config = replace(base_config, host="localhost", port=5433)
# base_config 不变，test_config 是新对象

# 测试里常见：从生产配置克隆出测试配置
```

---

## Python 生态

Python 内置 `copy` 模块，提供浅拷贝和深拷贝，是 Prototype 模式的直接支持。

```python
import copy
from dataclasses import dataclass, field

# 1. copy.copy（浅拷贝）vs copy.deepcopy（深拷贝）
@dataclass
class Config:
    host: str
    port: int
    tags: list[str] = field(default_factory=list)

base = Config(host="localhost", port=5432, tags=["web", "api"])

# 浅拷贝：顶层对象新建，内部引用共享
shallow = copy.copy(base)
shallow.host = "prod.db.com"    # 不影响原对象
shallow.tags.append("db")       # ⚠️ 影响原对象！tags 是共享引用

print(base.tags)    # ['web', 'api', 'db']  ← 被修改了

# 深拷贝：递归复制所有嵌套对象
deep = copy.deepcopy(base)
deep.tags.append("cache")       # 安全，不影响原对象
print(base.tags)    # ['web', 'api', 'db']  ← 未变化

# 2. __copy__ / __deepcopy__ — 自定义拷贝行为
class ExpensiveModel:
    def __init__(self, weights: list[float]):
        self.weights = weights
        self._cache = {}         # 缓存不需要复制

    def __copy__(self):
        new = ExpensiveModel.__new__(ExpensiveModel)
        new.weights = self.weights   # 共享 weights（节省内存）
        new._cache = {}              # 新缓存，不共享
        return new

    def __deepcopy__(self, memo):
        new = ExpensiveModel.__new__(ExpensiveModel)
        memo[id(self)] = new
        new.weights = copy.deepcopy(self.weights, memo)  # 深拷贝 weights
        new._cache = {}
        return new

# 3. dataclasses.replace — 不可变对象的"修改"（最 Pythonic 的 Prototype）
from dataclasses import replace

@dataclass(frozen=True)                # frozen=True：不可变，类似 record
class Request:
    method: str
    url: str
    headers: tuple[tuple[str, str], ...] = ()
    timeout: int = 30

base_request = Request(method="GET", url="https://api.example.com/users")

# replace 创建副本，只修改指定字段（原对象不变）
auth_request = replace(base_request, headers=(("Authorization", "Bearer token"),))
timeout_request = replace(base_request, timeout=5)

print(base_request.headers)    # ()  ← 原对象未变
print(auth_request.headers)    # (('Authorization', 'Bearer token'),)

# 4. Pydantic v2 — model_copy（生产级 Prototype）
# from pydantic import BaseModel
#
# class UserConfig(BaseModel):
#     name: str
#     role: str = "user"
#     permissions: list[str] = []
#
# admin_template = UserConfig(name="template", role="admin", permissions=["read", "write"])
# new_admin = admin_template.model_copy(update={"name": "Alice"})  # Prototype 克隆
```

> **Python 洞察**：`dataclasses.replace()` 是不可变数据结构的 Prototype 实现——
> 它不修改原对象，而是创建一个"大部分相同但某些字段不同"的新对象。
> 这在函数式编程风格（避免副作用）中非常常见，也是 Redux Reducer 的 Python 等价物。

---

## 关键洞察

> Prototype 模式在现代开发里最重要的应用是**不可变数据**：
> 不要修改原对象，而是克隆一份再改。
> React、Redux、Rust、函数式编程都围绕这个思想构建。
> "克隆比 new 更快"的性能优化反而是次要的。
