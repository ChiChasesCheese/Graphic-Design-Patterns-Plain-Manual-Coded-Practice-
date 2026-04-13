# Chapter 03: Template Method

> 类型: 行为 | 难度: ★☆☆ | 原书: `src/ch03_TemplateMethod/` | 前置: Ch02

---

## 模式速览

Template Method 在基类中定义算法的骨架，把具体步骤的实现推迟到子类。子类可以重写（override）某些步骤，但不能改变算法的整体结构。

```
AbstractClass
──────────────────────────────────
+ templateMethod()       ← 骨架，final，不可重写
  ├─ step1()             ← abstract / hook
  ├─ step2()             ← abstract / hook
  └─ step3()             ← abstract / hook
        ▲                ▲
        │                │
 ConcreteClassA    ConcreteClassB
 (覆写各 step)     (覆写各 step)
```

**核心思想**：把"不变的部分"固定在父类，把"变化的部分"开放给子类——这是"好莱坞原则"（Hollywood Principle）的经典体现：*Don't call us, we'll call you*。父类掌控流程，子类只提供数据/行为。

---

## 本章新语言特性

### Java

| 特性 | 说明 |
|------|------|
| `abstract class` | 不能实例化，可以有抽象方法和具体方法 |
| `abstract method` | 无方法体，强制子类实现 |
| `@Override` | 注解，编译器验证方法确实覆写了父类方法 |
| `final method` | 禁止子类覆写，保护算法骨架 |
| `abstract class` vs `interface` | 抽象类可携带状态和构造器；接口（Java 8+）只能有 `default`/`static` 方法，无状态 |

```java
// 抽象类定义骨架
abstract class AbstractDisplay {
    // 抽象方法：子类必须实现
    abstract void open();
    abstract void print();
    abstract void close();

    // 模板方法：final 保护骨架，不让子类破坏流程
    public final void display() {
        open();
        for (int i = 0; i < 5; i++) {
            print();        // 调用子类提供的"hook"
        }
        close();
    }
}

class CharDisplay extends AbstractDisplay {
    private final char ch;

    CharDisplay(char ch) { this.ch = ch; }

    @Override void open()  { System.out.print("<<"); }
    @Override void print() { System.out.print(ch); }
    @Override void close() { System.out.println(">>"); }
}
```

`@Override` 不是可选的装饰——它让编译器帮你检查方法签名，避免拼错方法名时静默失败。

### Python

| 特性 | 说明 |
|------|------|
| `abc.ABC` | 抽象基类的基类，继承它即可使用 `@abstractmethod` |
| `@abstractmethod` | 装饰器，声明抽象方法；子类若未实现则实例化时抛 `TypeError` |
| `super()` | 调用父类方法；Python 的 MRO（Method Resolution Order）决定调用顺序 |
| MRO (C3 线性化) | `ClassName.__mro__` 可查看，多继承时确定方法查找路径 |

```python
from abc import ABC, abstractmethod

class AbstractDisplay(ABC):
    @abstractmethod
    def open(self) -> None: ...

    @abstractmethod
    def print(self) -> None: ...

    @abstractmethod
    def close(self) -> None: ...

    # 模板方法：具体方法，子类无需覆写
    def display(self) -> None:
        self.open()
        for _ in range(5):
            self.print()   # 调用子类实现的 hook
        self.close()

class CharDisplay(AbstractDisplay):
    def __init__(self, ch: str) -> None:
        self.ch = ch

    def open(self)  -> None: print("<<", end="")
    def print(self) -> None: print(self.ch, end="")
    def close(self) -> None: print(">>")
```

```python
# 查看 MRO
class A(ABC): pass
class B(A): pass
class C(A): pass
class D(B, C): pass   # 多继承

print(D.__mro__)
# (<class 'D'>, <class 'B'>, <class 'C'>, <class 'A'>, <class 'ABC'>, <class 'object'>)
# super() 在多继承时按此顺序查找，而不是只找"直接父类"
```

---

## Java 实战: `java.util.AbstractList`

### 源码解析

`AbstractList` 是 JDK 里最典型的 Template Method 实现。子类只需实现两个抽象方法：

```java
// java.util.AbstractList 的核心契约
public abstract class AbstractList<E> extends AbstractCollection<E> implements List<E> {

    // hook 1：子类必须实现——按下标取元素
    public abstract E get(int index);

    // hook 2：子类必须实现——返回列表长度
    public abstract int size();

    // ─── 以下全是"免费赠送"的模板方法 ───────────────────────────────────

    // indexOf: 模板方法，骨架固定，通过调用 get() 遍历
    public int indexOf(Object o) {
        // 用 ListIterator 遍历（内部调用 get(i)）
        ListIterator<E> it = listIterator();
        if (o == null) {
            while (it.hasNext())
                if (it.next() == null)      // 委托给 get()
                    return it.previousIndex();
        } else {
            while (it.hasNext())
                if (o.equals(it.next()))    // 委托给 get()
                    return it.previousIndex();
        }
        return -1;
    }

    // contains: 直接复用 indexOf 的结果——算法骨架层层复用
    public boolean contains(Object o) {
        return indexOf(o) >= 0;
    }

    // iterator(): 返回内部类 Itr，Itr.next() 调用 get(cursor++)
    public Iterator<E> iterator() { return new Itr(); }
}
```

**关键洞察**：`indexOf`、`contains`、`iterator`、`subList`、`equals`、`hashCode` 这六个方法——你一行都不用写，只要实现 `get` 和 `size`，全部自动拥有。

同样的模式在 `java.io.InputStream` 中出现：

```java
public abstract class InputStream {

    // hook：读取单个字节，子类必须实现
    public abstract int read() throws IOException;

    // 模板方法：批量读取，骨架固定，内部循环调用 read()
    public int read(byte[] b, int off, int len) throws IOException {
        // ... 参数校验省略 ...
        int c = read();          // 调用 hook（单字节版本）
        if (c == -1) return -1;
        b[off] = (byte) c;
        int i = 1;
        try {
            for (; i < len; i++) {
                c = read();      // 再次调用 hook
                if (c == -1) break;
                b[off + i] = (byte) c;
            }
        } catch (IOException ee) { /* 忽略中途异常，返回已读字节数 */ }
        return i;               // 返回实际读取的字节数
    }
}
```

`FileInputStream.read()` 是一个 native 方法，调用操作系统读文件；而 `read(byte[])` 的循环逻辑一次也不用 `FileInputStream` 操心——Template Method 把这个"体力活"永久留在了父类。

### 现代重写：`interface` + `default` 方法

Java 8 之后，`interface` 的 `default` 方法也能实现 Template Method，适合不想占用继承槽的场景：

```java
// 现代替代方案：interface + default 方法
interface Sortable<T> {
    List<T> getData();           // hook：子类提供数据
    Comparator<T> comparator();  // hook：子类提供排序规则

    default List<T> sorted() {   // 模板：算法骨架固定
        var result = new ArrayList<>(getData());
        result.sort(comparator());
        return result;
    }
}

// 实现类：只需填入"变化的部分"
class EmployeeRoster implements Sortable<Employee> {
    private final List<Employee> employees;

    EmployeeRoster(List<Employee> employees) {
        this.employees = employees;
    }

    @Override
    public List<Employee> getData() { return employees; }

    @Override
    public Comparator<Employee> comparator() {
        return Comparator.comparing(Employee::salary).reversed();
    }
}
```

与抽象类的区别：`interface` 无法有实例字段，也无法写构造器。当模板方法需要维护状态时，仍然要用 `abstract class`。

---

## Python 实战: `collections.abc` + `unittest.TestCase`

### 源码解析

**`collections.abc.Sequence`** 与 `AbstractList` 如出一辙：

```python
from collections.abc import Sequence

class FibSequence(Sequence):
    """只实现两个 hook，其余方法全部免费获得"""

    def __init__(self, n: int) -> None:
        # 预计算前 n 个斐波那契数
        self._data: list[int] = []
        a, b = 0, 1
        for _ in range(n):
            self._data.append(a)
            a, b = b, a + b

    # hook 1：按下标取值
    def __getitem__(self, index: int) -> int:
        return self._data[index]

    # hook 2：返回长度
    def __len__(self) -> int:
        return len(self._data)

    # 以下方法全部来自 Sequence 的模板实现（无需自己写）：
    # __contains__, __iter__, __reversed__, index, count

fib = FibSequence(10)
print(5 in fib)            # True  ← __contains__ 免费
print(list(reversed(fib))) # ← __reversed__ 免费
print(fib.count(1))        # 2     ← count 免费（1 出现两次：fib[1] 和 fib[2]）
```

**`unittest.TestCase`** 是另一个经典案例——测试框架掌控流程，用户只提供"变化的步骤"：

```python
import unittest

class DatabaseTest(unittest.TestCase):

    # hook：setUp 在每个 test_* 方法前被框架调用
    def setUp(self) -> None:
        self.db = connect_to_test_db()
        self.db.begin_transaction()

    # hook：tearDown 在每个 test_* 方法后被框架调用（即使测试失败）
    def tearDown(self) -> None:
        self.db.rollback()   # 回滚，让每个测试互不干扰
        self.db.close()

    # 变化的步骤：用户自定义的测试逻辑
    def test_insert_user(self) -> None:
        self.db.insert("users", {"name": "Alice"})
        self.assertEqual(self.db.count("users"), 1)

# TestCase.run() 是模板方法，大致逻辑：
#   result.startTest(self)
#   self.setUp()          ← hook
#   try:
#       testMethod()      ← 用户写的 test_* ← 变化的步骤
#   except ...:
#       result.addFailure(...)
#   finally:
#       self.tearDown()   ← hook
#   result.stopTest(self)
```

### Pythonic 重写：两种替代方案

Python 有更轻量的手段达到相同目的，不必强制使用类继承。

**方案一：Context Manager（`with` 语句）**

```python
from contextlib import contextmanager

@contextmanager
def db_transaction(connection):
    """with 语句本身就是 Template Method：
       __enter__ = setUp hook
       with 块内的代码 = 变化的步骤
       __exit__ = tearDown hook
    """
    connection.begin()
    try:
        yield connection        # 把控制权交给 with 块（变化的步骤）
        connection.commit()
    except Exception:
        connection.rollback()   # 出错时回滚
        raise

# 调用方只写"变化的部分"
with db_transaction(conn) as db:
    db.insert("users", {"name": "Alice"})   # ← 变化的步骤
```

**方案二：高阶函数（函数作为 hook 传入）**

```python
from typing import Callable, TypeVar
T = TypeVar("T")

def with_retry(
    operation: Callable[[], T],    # hook：变化的步骤
    max_attempts: int = 3,
    delay: float = 1.0,
) -> T:
    """模板方法：重试骨架固定，具体操作由调用方传入"""
    import time
    last_error: Exception | None = None
    for attempt in range(1, max_attempts + 1):
        try:
            return operation()          # 调用 hook
        except Exception as e:
            last_error = e
            if attempt < max_attempts:
                time.sleep(delay)
    raise RuntimeError(f"操作在 {max_attempts} 次尝试后仍失败") from last_error

# 调用方只提供"变化的步骤"，不需要继承任何类
result = with_retry(lambda: fetch_remote_data(url))
```

---

## 两种哲学

| 维度 | Java | Python |
|------|------|--------|
| 实现方式 | `abstract class` + `@Override` | `abc.ABC` + `@abstractmethod` |
| Mixin 支持 | 单继承限制，需 `interface default` 方法 | 多继承，mixin class 自由组合 |
| Hook 方法风格 | 空方法体（可选 hook）或 `abstract`（必须 hook） | `raise NotImplementedError` 或 `@abstractmethod` |
| 替代方案 | 基本无（继承是主流手段） | context manager、decorator、高阶函数 |
| 类型检查 | 编译期强制，`@Override` 签名核验 | mypy 静态检查 `abc.abstractmethod` |
| 框架惯用 | Spring `AbstractController`、JPA `AbstractDao` | Django CBV `View.dispatch()`、pytest fixture |

### Strategy vs Template Method

这两个模式解决同一个问题——"让算法的某部分可变"——但机制完全相反：

- **Template Method = 继承**：子类重写父类的步骤。变化的逻辑在编译期就绑定好了。
- **Strategy = 组合**：把可变行为封装成对象，运行时注入。行为可以随时替换。

```
Template Method（静态，编译期）：
    AbstractClass ← ConcreteClass（继承关系，is-a）

Strategy（动态，运行时）：
    Context ──has-a──▶ Strategy interface
                            ▲        ▲
                      ConcreteA  ConcreteB
```

**选择指南**：
- 变化的是"算法中的一个步骤"，且步骤多、骨架复杂 → Template Method
- 变化的是"整个可插拔的行为"，且需要运行时切换 → Strategy
- Java 因为强类型和单继承的限制，Template Method 非常普遍；Python 因为鸭子类型和高阶函数，往往不需要继承就能实现等效效果。

---

## 动手练习

**03.1 Java**：创建 `AbstractReportGenerator`，模板方法 `generate()` 依次调用 `collectData()`、`formatData()`、`output()`。实现 `CsvReportGenerator` 和 `JsonReportGenerator` 两个子类，分别生成 CSV 和 JSON 格式报告。

**03.2 Python**：用 `abc.ABC` 完成同样的报告生成器；然后将它重构为高阶函数版本——接受 `format_fn: Callable[[list], str]` 作为参数，消除继承。比较两种实现的代码量和灵活性。

**03.3 跨语言思考**：什么时候 Template Method 优于 Strategy？
> 参考答案：当算法有多个固定步骤共享同一个骨架，且变化的是"多处细节"而非"一个整体行为"时，Template Method 更合适。Strategy 通常只替换一个行为单元；Template Method 可以同时管理多个 hook 点，并保证它们的调用顺序不变。

---

## 回顾与连接

- **Template Method vs Strategy（Ch02）**：继承的孪生兄弟——相同的目标（让算法某部分可变），不同的机制（继承 vs 组合）。
- **Factory Method（Ch06）** 本质上就是 Template Method 应用于对象创建：`createProduct()` 是"变化的步骤"，`factoryMethod()` 是骨架——先看懂 Template Method，再学 Factory Method 会豁然开朗。
- **Adapter（Ch04，下一章）** 通过包装已有类来适配接口；Template Method 通过继承基类来扩展接口。两者都在解决"接口不匹配"，但方向相反：Adapter 向外适配，Template Method 向内填充。
