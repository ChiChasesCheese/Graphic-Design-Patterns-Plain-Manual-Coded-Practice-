# Chapter 18: Abstract Factory

> 类型: 创建 | 难度: ★★★ | 原书: `src/ch08_AbstractFactory/` | 前置: Ch06 (Factory Method)

---

## 模式速览

Abstract Factory 解决的问题比 Factory Method 更宏大：不是创建**一个**产品，而是创建**一族**相互配套的产品。工厂接口声明创建全套产品的方法，每个具体工厂实现一整套风格一致的产品系列。客户端只依赖抽象工厂接口，永远不直接 `new` 任何具体产品。

```
         «interface»
         AbstractFactory
   ┌─────────────────────────┐
   │ + createLink(...)       │
   │ + createTray(...)       │
   │ + createPage(...)       │
   └──────────┬──────────────┘
              │ implements
      ┌────────┴────────┐
      │                 │
  ListFactory      TableFactory       ← 具体工厂：每个工厂生产一套风格
      │                 │
  ┌───▼───┐         ┌───▼───┐
  │List   │         │Table  │
  │Link   │         │Link   │         «abstract»
  │Tray   │         │Tray   │    ←──   Item
  │Page   │         │Page   │          ├── Link
  └───────┘         └───────┘          ├── Tray
                                        └── Page
```

**核心思想**：把"用哪套产品"这个决策集中到工厂对象上，一旦选定工厂，后续创建的所有产品天然保持风格一致。客户端代码无需任何修改，切换 `ListFactory` 为 `TableFactory` 即可得到完全不同风格的输出。

> **一句话记忆**：Abstract Factory = 一族 Factory Method 的集合体。工厂方法解决"创建哪种产品"，抽象工厂解决"创建哪套相互配套的产品族"。

---

## 本章新语言特性

### Java：`sealed interface` 作为工厂契约 + `record` 作为产品

原书用抽象类构建产品层次。Java 17+ 的 `sealed` 接口让编译器**显式枚举**整套产品族，配合 `record` 给出不可变的具体产品实现，用 `switch` 模式匹配做穷举渲染。

| 特性 | 在 Abstract Factory 中的角色 |
|------|------------------------------|
| `sealed interface AbstractFactory permits ...` | 工厂接口：限定合法的具体工厂，关闭扩展 |
| `sealed interface Widget permits Button, TextField` | 产品接口：列出该家族的所有产品类型 |
| `record` 实现产品接口 | 不可变产品：自动获得 `equals`、`hashCode`、`toString` |
| `switch` 模式匹配 + `sealed` | 渲染/序列化逻辑：编译器保证覆盖全部产品类型 |

```java
// ── 产品族：UI 组件 ─────────────────────────────────────────
sealed interface Button  permits LightButton,  DarkButton  { String render(); }
sealed interface TextBox permits LightTextBox, DarkTextBox { String render(); }

// Light 主题产品（record 自动实现不可变性）
record LightButton (String label) implements Button  { public String render() { return "[  " + label + "  ]"; } }
record LightTextBox(String hint)  implements TextBox { public String render() { return "____" + hint + "____"; } }

// Dark 主题产品
record DarkButton (String label) implements Button  { public String render() { return "▌▌ " + label + " ▌▌"; } }
record DarkTextBox(String hint)  implements TextBox { public String render() { return "██" + hint + "██"; } }

// ── 抽象工厂：sealed 限定只有两种主题工厂 ───────────────────
sealed interface ThemeFactory permits LightTheme, DarkTheme {
    Button  createButton(String label);
    TextBox createTextBox(String hint);
}

// 具体工厂：每个工厂生产风格一致的一整套组件
record LightTheme() implements ThemeFactory {
    public Button  createButton(String label) { return new LightButton(label); }
    public TextBox createTextBox(String hint)  { return new LightTextBox(hint);  }
}

record DarkTheme() implements ThemeFactory {
    public Button  createButton(String label) { return new DarkButton(label); }
    public TextBox createTextBox(String hint)  { return new DarkTextBox(hint);  }
}

// ── 客户端：只依赖抽象，切换工厂无需改代码 ─────────────────
ThemeFactory factory = new DarkTheme();   // 换成 LightTheme() 即可切换风格
Button  btn = factory.createButton("提交");
TextBox box = factory.createTextBox("请输入用户名");

// switch 模式匹配：sealed 保证穷举，无需 default
String output = switch (btn) {
    case LightButton b -> "浅色: " + b.render();
    case DarkButton  b -> "深色: " + b.render();
};
System.out.println(output);   // 深色: ▌▌ 提交 ▌▌
```

`sealed` 在这里的价值：新增第三套主题（如 `HighContrastTheme`）时，所有 `switch` 渲染逻辑都会报编译错误——迫使开发者主动处理新情况，而不是默默走 `default` 分支。

### Python：`ABC` + `@abstractmethod` + `__init_subclass__` 自动注册

Python 用 `ABC` 模拟抽象工厂契约，用 `__init_subclass__` 实现**工厂自动注册**——子类一旦定义，自动加入全局注册表，无需手动维护映射。

| 特性 | 说明 |
|------|------|
| `ABC` + `@abstractmethod` | 强制子类实现所有工厂方法，否则实例化报错 |
| `__init_subclass__` | 子类定义时自动调用，天然的注册钩子 |
| `Protocol` | 结构化产品接口，无需继承即可满足 |
| `ClassVar[dict]` | 注册表存在基类上，所有子类共享 |

```python
from abc import ABC, abstractmethod
from typing import ClassVar

# ── 抽象工厂基类：带自动注册机制 ──────────────────────────────
class AbstractFactory(ABC):
    # 类级注册表：主题名 → 工厂类
    _registry: ClassVar[dict[str, type["AbstractFactory"]]] = {}

    def __init_subclass__(cls, theme: str = "", **kwargs) -> None:
        """子类定义时自动执行，无需手动注册"""
        super().__init_subclass__(**kwargs)
        if theme:
            AbstractFactory._registry[theme] = cls

    @classmethod
    def get_factory(cls, theme: str) -> "AbstractFactory":
        """按主题名获取工厂实例"""
        if theme not in cls._registry:
            raise ValueError(f"未知主题: {theme!r}，已注册: {list(cls._registry)}")
        return cls._registry[theme]()

    @abstractmethod
    def create_button(self, label: str) -> "Button": ...

    @abstractmethod
    def create_textbox(self, hint: str) -> "TextBox": ...

# ── 产品 Protocol（结构化类型，无需继承）────────────────────
from typing import Protocol

class Button(Protocol):
    def render(self) -> str: ...

class TextBox(Protocol):
    def render(self) -> str: ...

# ── 具体产品 ─────────────────────────────────────────────────
class LightButton:
    def __init__(self, label: str) -> None: self.label = label
    def render(self) -> str: return f"[  {self.label}  ]"

class DarkButton:
    def __init__(self, label: str) -> None: self.label = label
    def render(self) -> str: return f"▌▌ {self.label} ▌▌"

class LightTextBox:
    def __init__(self, hint: str) -> None: self.hint = hint
    def render(self) -> str: return f"____{self.hint}____"

class DarkTextBox:
    def __init__(self, hint: str) -> None: self.hint = hint
    def render(self) -> str: return f"██{self.hint}██"

# ── 具体工厂：theme= 触发 __init_subclass__ 自动注册 ────────
class LightThemeFactory(AbstractFactory, theme="light"):
    def create_button(self, label: str) -> LightButton:  return LightButton(label)
    def create_textbox(self, hint: str)  -> LightTextBox: return LightTextBox(hint)

class DarkThemeFactory(AbstractFactory, theme="dark"):
    def create_button(self, label: str) -> DarkButton:  return DarkButton(label)
    def create_textbox(self, hint: str)  -> DarkTextBox: return DarkTextBox(hint)

# ── 使用：按名称动态获取工厂 ─────────────────────────────────
factory = AbstractFactory.get_factory("dark")  # 换成 "light" 即切换风格
btn = factory.create_button("提交")
box = factory.create_textbox("请输入用户名")
print(btn.render())   # ▌▌ 提交 ▌▌
print(box.render())   # ██请输入用户名██
```

`__init_subclass__` 的核心价值：**把"注册"这件事从配置文件/手动 `register()` 调用，移到类定义本身**。新增一个工厂时，只需写 `class FooFactory(AbstractFactory, theme="foo")`，注册自动完成，绝不会忘记。

---

## Java 实战：JDK 与主流框架中的 Abstract Factory

### `javax.xml.parsers.DocumentBuilderFactory`——换 XML 解析器实现

这是 JDK 里最典型的 Abstract Factory 应用。`DocumentBuilderFactory` 是抽象工厂，`DocumentBuilder` 是它创建的产品。具体工厂实现由运行时类路径决定（Xerces、内置 JDK 实现等）：

```java
// 客户端只看到抽象工厂，不关心底层是哪个 XML 库
DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
factory.setNamespaceAware(true);   // 配置工厂（影响所有产品）
factory.setValidating(false);

// 工厂创建产品：DocumentBuilder（解析器）
DocumentBuilder builder = factory.newDocumentBuilder();
Document doc = builder.parse(new InputSource(new StringReader("<root/>")));

// 替换为其他实现（如 Saxon）：只需修改系统属性，客户端代码不动
// System.setProperty("javax.xml.parsers.DocumentBuilderFactory",
//                    "net.sf.saxon.dom.DocumentBuilderFactoryImpl");
```

`DocumentBuilderFactory` 还创建配套的 `TransformerFactory`（XSL 转换器），两者来自同一供应商，保证兼容性——这正是 Abstract Factory"产品族天然配套"的价值。

### `java.sql.DriverManager`——数据库访问的产品族

JDBC 是 Abstract Factory 的经典教科书案例。每个数据库驱动提供一整套配套产品：

```java
// 具体工厂：由 JDBC URL 决定（mysql:// → MySQL 驱动，postgresql:// → PG 驱动）
Connection conn = DriverManager.getConnection(
    "jdbc:postgresql://localhost/mydb", "user", "pass"
);

// 工厂生产一族产品——全部来自同一个驱动，天然配套
Statement  stmt = conn.createStatement();           // 产品 A
ResultSet  rs   = stmt.executeQuery("SELECT 1");    // 产品 B（由 A 生产）
PreparedStatement ps = conn.prepareStatement(       // 产品 C
    "SELECT * FROM users WHERE id = ?"
);

// 切换数据库：只改连接 URL，产品族自动换成 MySQL 家族
// Connection conn = DriverManager.getConnection("jdbc:mysql://...", ...);
// 后续代码完全不变：stmt、rs、ps 仍然这样使用
```

`Connection` 本身是工厂，`Statement`、`PreparedStatement`、`ResultSet` 是它生产的产品族。MySQL 的 `Connection` 生产 MySQL 专属的 `Statement`，PostgreSQL 的生产 PG 专属的——两者不能混用，但各自内部完全一致。

### Swing `LookAndFeel`——UI 组件的一致主题

`LookAndFeel` 把整套 UI 组件的外观封装在一个工厂里：

```java
// 切换整套 UI 风格：一行代码换工厂，所有组件跟着换
UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
// 或者：UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());

// 工厂在幕后创建配套产品族：
// ButtonUI、TextFieldUI、ScrollBarUI、BorderUI...
// 客户端只写普通 Swing 代码，风格自动随工厂变化
JButton btn = new JButton("确定");     // 内部调用 LookAndFeel.createUI(...)
JTextField tf = new JTextField(20);    // 同一工厂 → 风格一致
JScrollPane sp = new JScrollPane(tf);  // 滚动条也同风格
```

Swing 的设计是 Abstract Factory 的完整实现：`LookAndFeel` 是抽象工厂，`ButtonUI`/`TextFieldUI`/`ScrollBarUI` 是产品接口，`NimbusButtonUI` 等是具体产品。

### 用 `sealed` + `record` 重写教科书示例

原书（`src/ch08_AbstractFactory/`）生产 HTML 页面，包含 Link、Tray、Page 三种产品。下面用现代 Java 重写，展示产品族的类型安全：

```java
// ── 产品族接口（sealed 限定合法子类）────────────────────────
sealed interface HtmlLink  permits ListLink,  TableLink  { String toHtml(); }
sealed interface HtmlTray  permits ListTray,  TableTray  { String toHtml(); }
sealed interface HtmlPage  permits ListPage,  TablePage  { String toHtml(); }

// ── List 风格产品族 ──────────────────────────────────────────
record ListLink(String caption, String url) implements HtmlLink {
    public String toHtml() {
        return "<li><a href=\"%s\">%s</a></li>".formatted(url, caption);
    }
}

record ListTray(String caption, List<HtmlLink> links) implements HtmlTray {
    public String toHtml() {
        var sb = new StringBuilder("<ul><li>").append(caption).append("<ul>");
        links.forEach(l -> sb.append(l.toHtml()));
        return sb.append("</ul></li></ul>").toString();
    }
}

record ListPage(String title, List<HtmlTray> trays) implements HtmlPage {
    public String toHtml() {
        var sb = new StringBuilder("<html><body><h1>").append(title).append("</h1>");
        trays.forEach(t -> sb.append(t.toHtml()));
        return sb.append("</body></html>").toString();
    }
}

// ── 抽象工厂（sealed 限定只有两种工厂）──────────────────────
sealed interface PageFactory permits ListPageFactory, TablePageFactory {
    HtmlLink createLink(String caption, String url);
    HtmlTray createTray(String caption, List<HtmlLink> links);
    HtmlPage createPage(String title, List<HtmlTray> trays);
}

// ── 具体工厂 ─────────────────────────────────────────────────
record ListPageFactory() implements PageFactory {
    public HtmlLink createLink(String c, String u)            { return new ListLink(c, u); }
    public HtmlTray createTray(String c, List<HtmlLink> ls)   { return new ListTray(c, ls); }
    public HtmlPage createPage(String t, List<HtmlTray> ts)   { return new ListPage(t, ts); }
}

// ── 使用 ─────────────────────────────────────────────────────
PageFactory factory = new ListPageFactory();  // 换成 TablePageFactory() 即切换风格

HtmlLink google = factory.createLink("Google", "https://google.com");
HtmlLink yahoo  = factory.createLink("Yahoo",  "https://yahoo.com");
HtmlTray search = factory.createTray("搜索引擎", List.of(google, yahoo));
HtmlPage page   = factory.createPage("我的主页", List.of(search));

System.out.println(page.toHtml());
```

---

## Python 实战：生态中的 Abstract Factory

### `json` / `ujson` / `orjson`——同一接口，不同实现族

Python 的 JSON 库遵循相同接口（`loads`、`dumps`），是 Abstract Factory 在模块层面的体现：

```python
import importlib
from typing import Protocol, Any

# ── 产品协议：JSON 编解码器接口 ──────────────────────────────
class JsonCodec(Protocol):
    def loads(self, data: str | bytes) -> Any: ...
    def dumps(self, obj: Any, **kwargs: Any) -> str | bytes: ...

# ── 工厂函数：按名称选择 JSON 库（构成一个产品族）────────────
def get_json_factory(backend: str = "json") -> JsonCodec:
    """
    返回指定后端的 JSON 模块作为工厂。
    - "json"   → 标准库，纯 Python，通用性最好
    - "ujson"  → C 扩展，速度约 3-5×，但不支持自定义编码器
    - "orjson" → Rust 实现，速度约 10×，返回 bytes 而非 str
    """
    try:
        return importlib.import_module(backend)  # type: ignore[return-value]
    except ImportError:
        raise ImportError(f"未安装 {backend!r}，请 pip install {backend}")

# ── 使用：切换工厂不影响业务代码 ─────────────────────────────
codec = get_json_factory("json")   # 开发环境用标准库
# codec = get_json_factory("orjson")  # 生产环境换高性能实现

data  = codec.dumps({"name": "张三", "age": 30})
obj   = codec.loads(data)
print(obj["name"])   # 张三
```

每个 JSON 模块内部，`loads` 和 `dumps` 是一族配套产品——同一个库的编码器和解码器共享内部实现（字符集处理、数字精度策略），混用不同库的 `dumps`/`loads` 可能出现微妙差异，这正是"产品族应来自同一工厂"原则的现实体现。

### Django 数据库后端——完整的产品族切换

Django 的数据库后端是 Abstract Factory 最完整的工业级实现。每个后端（`sqlite3`、`postgresql`、`mysql`）提供一整套配套对象：

```python
# settings.py：一处配置决定整个产品族
DATABASES = {
    "default": {
        "ENGINE": "django.db.backends.postgresql",  # 抽象工厂的选择
        "NAME": "mydb",
        "USER": "postgres",
    }
}

# Django 内部（简化示意）：工厂如何被加载
from django.db import connections

conn = connections["default"]          # 获取工厂（Connection 对象）
cursor = conn.cursor()                 # 产品 A：Cursor（查询执行器）
introspection = conn.introspection    # 产品 B：数据库内省工具
creation = conn.creation              # 产品 C：DDL 生成器
ops = conn.ops                        # 产品 D：数据库特定操作（如日期函数）
```

每个后端包含的产品族：

```python
# 每个后端目录下的结构（以 postgresql 为例）
# django/db/backends/postgresql/
#   __init__.py
#   base.py          → DatabaseWrapper（抽象工厂的具体实现）
#   client.py        → DatabaseClient（产品：交互式客户端）
#   creation.py      → DatabaseCreation（产品：创建/删除数据库）
#   features.py      → DatabaseFeatures（产品：功能特性声明）
#   introspection.py → DatabaseIntrospection（产品：表结构查询）
#   operations.py    → DatabaseOperations（产品：SQL 方言适配）
#   schema.py        → DatabaseSchemaEditor（产品：ALTER TABLE 等）
```

切换 `ENGINE` 从 `postgresql` 改为 `sqlite3`，整套产品族自动替换。ORM 生成的 SQL（日期函数、分页语法、布尔表示）随之变化，业务层代码完全不改。

### `__init_subclass__` 自动注册的完整模式

```python
from abc import ABC, abstractmethod
from typing import ClassVar

class StorageFactory(ABC):
    """文件存储抽象工厂：本地 / S3 / GCS 等后端"""
    _backends: ClassVar[dict[str, type["StorageFactory"]]] = {}

    def __init_subclass__(cls, backend: str = "", **kwargs: object) -> None:
        super().__init_subclass__(**kwargs)
        if backend:
            StorageFactory._backends[backend] = cls
            print(f"已注册存储后端: {backend!r}")  # 导入时自动打印

    @classmethod
    def create(cls, backend: str, **config: object) -> "StorageFactory":
        if backend not in cls._backends:
            available = list(cls._backends)
            raise KeyError(f"后端 {backend!r} 未注册，可用: {available}")
        return cls._backends[backend](**config)  # type: ignore[arg-type]

    @abstractmethod
    def open(self, path: str) -> object: ...      # 产品 A：文件句柄

    @abstractmethod
    def stat(self, path: str) -> dict: ...        # 产品 B：文件元信息

# ── 注册时机：模块导入即自动完成 ─────────────────────────────
class LocalStorage(StorageFactory, backend="local"):
    def __init__(self, root: str = "/tmp") -> None:
        self.root = root
    def open(self, path: str) -> object:
        return open(f"{self.root}/{path}", "rb")
    def stat(self, path: str) -> dict:
        import os
        st = os.stat(f"{self.root}/{path}")
        return {"size": st.st_size, "mtime": st.st_mtime}

class S3Storage(StorageFactory, backend="s3"):
    def __init__(self, bucket: str) -> None:
        self.bucket = bucket
    def open(self, path: str) -> object:
        import boto3
        s3 = boto3.client("s3")
        return s3.get_object(Bucket=self.bucket, Key=path)["Body"]
    def stat(self, path: str) -> dict:
        import boto3
        s3 = boto3.client("s3")
        resp = s3.head_object(Bucket=self.bucket, Key=path)
        return {"size": resp["ContentLength"], "mtime": resp["LastModified"]}

# ── 使用：配置驱动，无需 if/else ─────────────────────────────
storage = StorageFactory.create("local", root="/var/data")
handle  = storage.open("config.json")   # 产品 A
info    = storage.stat("config.json")   # 产品 B
```

---

## 两种哲学

| 维度 | Java | Python |
|------|------|--------|
| 工厂接口 | `sealed interface` 限定合法工厂，编译期封闭 | `ABC` 约束接口，可无限扩展 |
| 产品接口 | `sealed interface` 枚举产品类型，`switch` 穷举 | `Protocol` 结构类型，鸭子类型 |
| 注册机制 | `Class.forName()`（原书）或工厂枚举 | `__init_subclass__` 自动注册 |
| 增加新工厂 | 必须加入 `permits` 列表，所有 `switch` 报错提示 | 只需继承并传 `backend=` 关键字 |
| 产品不变性 | `record` 天然不可变，线程安全 | `dataclass(frozen=True)` 或 `NamedTuple` |
| 错误时机 | 编译期（`sealed` + `switch`） | 运行期（`isinstance` + `Protocol`） |

**核心分歧**：Java 的 `sealed` 把产品族的边界写入类型系统——增加新产品时，编译器帮你找出所有忘记更新的地方。Python 的 `Protocol` 不要求继承关系，任何实现了正确方法的对象都可以充当产品——灵活但需要测试覆盖来代替静态检查。

两者没有高下之分。Java 的做法适合产品族**稳定但实现多变**的场景（如 JDBC、XML 解析）；Python 的做法适合**快速迭代、动态加载插件**的场景（如 Django 后端、存储适配器）。

---

## 原书代码回顾（`src/ch08_AbstractFactory/`）

原书用抽象类风格实现，生产不同风格的 HTML 页面：

```
factory/
  Factory.java   ← 抽象工厂：静态方法 getFactory(classname) + 三个抽象工厂方法
  Item.java      ← 产品基类：持有 caption，声明 makeHTML()
  Link.java      ← 抽象产品 A：继承 Item，持有 url
  Tray.java      ← 抽象产品 B：继承 Item，持有 List<Item>
  Page.java      ← 抽象产品 C：持有 title、author、List<Item>，负责写 HTML 文件
listfactory/
  ListFactory.java  ← 具体工厂 1：生产 <ul><li> 风格的产品族
  ListLink.java     ← 具体产品：<li><a href=...>
  ListTray.java     ← 具体产品：<ul> 嵌套
  ListPage.java     ← 具体产品：完整 HTML 页面
tablefactory/
  TableFactory.java ← 具体工厂 2：生产 <table> 风格的产品族
  TableLink.java
  TableTray.java
  TablePage.java
Main.java         ← 通过命令行参数选择工厂，客户端代码不变
```

`Factory.getFactory()` 用反射按类名创建工厂实例——这是一种运行时工厂选择机制，等价于本章 Python 示例中的注册表查找。原书的精妙之处：`Main.java` 里没有一个 `import listfactory.*` 或 `import tablefactory.*`，完全依赖 `factory` 包的抽象，体现了 Abstract Factory 的核心承诺：**客户端代码不依赖任何具体产品**。

---

## 动手练习

### 18.1 Java：数据库模拟的产品族

用 `sealed interface` + `record` 实现一个简化的 JDBC 模拟：

- `sealed interface DbFactory permits H2Factory, PostgresFactory`
- 每个工厂创建配套的 `Connection`（`record`）和 `Statement`（`record`）
- `Statement` 有 `execute(String sql)` 方法，不同工厂的实现打印不同前缀（`[H2]` vs `[PG]`）
- 用 `switch` 模式匹配在 `DbFactory` 上做穷举分发

### 18.2 Python：插件式报表工厂

实现一个报表生成系统，支持 `"csv"`、`"json"`、`"html"` 三种格式：

- 抽象工厂基类用 `ABC` + `__init_subclass__` 自动注册（`format=` 关键字）
- 每个工厂创建配套的 `Header`（列标题生成器）和 `Row`（行数据格式化器）两种产品
- 用 `Protocol` 定义产品接口
- 实现 `ReportFactory.create("csv")` 按名称获取工厂的类方法

### 18.3 思考题：何时用 Abstract Factory vs Factory Method？

对以下场景分析应该用哪个模式，并说明理由：

1. 一个日志系统，只需要选择"写文件"还是"写控制台"两种输出方式
2. 一个跨平台 UI 框架，Windows 和 macOS 各自需要 Button、TextField、Dialog 三种控件，风格必须统一
3. 一个测试框架，需要根据配置选择"Mock 数据库"或"真实数据库"，两者都提供相同的 CRUD 接口

---

## 回顾与连接

```
Ch03 Template Method
       │
       │  "可变步骤 = 创建产品"
       ▼
Ch06 Factory Method           ← 创建一个产品
       │
       │  "将一族 Factory Method 组合"
       ▼
Ch18 Abstract Factory         ← 本章：创建相互配套的一族产品
       │
       │  "工厂本身只需一个实例"
       ▼
Ch05 Singleton                ← 具体工厂通常是 Singleton
```

| 模式 | 核心问题 | 解法 |
|------|----------|------|
| Factory Method | 子类决定创建哪种产品 | 一个抽象工厂方法 |
| Abstract Factory | 保证创建出的产品族风格一致 | 多个工厂方法组合成接口 |
| Builder（Ch10） | 产品构建步骤复杂，需分步组装 | Director 驱动 Builder 逐步构建 |
| Prototype（Ch11） | 创建成本高，从现有对象复制更快 | `clone()` / `copy.deepcopy()` |

**Abstract Factory 的核心价值**：它在"选择用哪套技术"和"使用这套技术"之间画了一条清晰的边界。选择发生在程序启动或配置读取时（一次），使用发生在业务逻辑里（千百次）。这条边界使得整个代码库里不会散落 `if backend == "mysql"` 的判断——所有变化点收束在工厂选择的那一刻。
