# Python 3.10-3.13 新特性速查

本书用到的 Python 现代特性，按版本排列。

## Python 3.8 (2019) — 基础

| 特性 | 语法 | 首次出现章节 |
|------|------|-------------|
| 海象运算符 | `if (n := len(s)) > 10:` | Ch15 CoR |
| `functools.cache` | `@cache` | Ch05 Singleton |
| `typing.Protocol` | `class Drawable(Protocol): def draw(self): ...` | Ch02 Strategy |

## Python 3.9 (2020)

| 特性 | 语法 | 首次出现章节 |
|------|------|-------------|
| `dict \| dict` 合并 | `a \| b` | — |
| 内置泛型 | `list[int]` 替代 `List[int]` | Ch01 Iterator |

## Python 3.10 (2021)

| 特性 | 语法 | 首次出现章节 |
|------|------|-------------|
| `match`/`case` | `match expr: case Num(v): ...` | Ch12 Composite |
| 类型联合 `X \| Y` | `int \| str` 替代 `Union[int, str]` | Ch12 Composite |
| `dataclass(kw_only=True)` | 强制关键字参数 | Ch10 Builder |
| `dataclass(slots=True)` | 自动生成 `__slots__` | Ch10 Builder |
| `ParamSpec` | 类型安全装饰器 | Ch09 Decorator |

## Python 3.11 (2022)

| 特性 | 语法 | 首次出现章节 |
|------|------|-------------|
| `ExceptionGroup` | `except* ValueError:` | — |
| `Self` 类型 | `def copy(self) -> Self:` | Ch11 Prototype |
| `StrEnum` | `class Color(StrEnum): RED = auto()` | Ch14 State |

## Python 3.12 (2023)

| 特性 | 语法 | 首次出现章节 |
|------|------|-------------|
| `type` 别名 | `type Point = tuple[int, int]` | Ch12 Composite |
| `Path.walk()` | `for root, dirs, files in Path('.').walk():` | Ch22 Visitor |
| 改进的 f-string | 嵌套引号: `f"{'hello'}"` | Ch01 Iterator |

## Python 3.13 (2024)

| 特性 | 语法 | 首次出现章节 |
|------|------|-------------|
| Free-threaded 模式 | `python -X nogil` | Ch21 Mediator |

---

## 核心 Protocol 一览

```python
# Iterator 协议 (Ch01)
class Iterator(Protocol):
    def __iter__(self) -> Self: ...
    def __next__(self) -> T: ...

# Context Manager 协议 (Ch04)
class ContextManager(Protocol):
    def __enter__(self) -> Self: ...
    def __exit__(self, exc_type, exc_val, exc_tb) -> bool | None: ...

# Callable 协议 (Ch02, Ch13)
class Command(Protocol):
    def __call__(self) -> None: ...

# Descriptor 协议 (Ch16)
class Descriptor(Protocol):
    def __get__(self, obj, objtype=None): ...
    def __set__(self, obj, value): ...
```

## 常用组合技

```python
# dataclass + match = 代数数据类型 + 模式匹配 (Ch12, Ch22, Ch23)
from dataclasses import dataclass

@dataclass
class Num:
    value: float

@dataclass
class Add:
    left: 'Expr'
    right: 'Expr'

type Expr = Num | Add

def evaluate(expr: Expr) -> float:
    match expr:
        case Num(value=v):
            return v
        case Add(left=l, right=r):
            return evaluate(l) + evaluate(r)
```
