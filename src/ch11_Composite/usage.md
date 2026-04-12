# Composite 模式 — 真实应用

核心：**树形结构中，叶节点和容器节点对外暴露相同接口，调用方无需区分。**

---

## 1. React — 组件树

React 最核心的思想就是 Composite。
`<Button>` 是叶节点，`<Form>` 是容器，但对 React 渲染引擎来说都是"组件"。

```typescript
// 叶节点
function Button({ label, onClick }) {
    return <button onClick={onClick}>{label}</button>;
}

// 容器节点（包含其他组件）
function Form({ children, onSubmit }) {
    return <form onSubmit={onSubmit}>{children}</form>;
}

// 组合：无论嵌套多深，接口一致
function CheckoutPage() {
    return (
        <Form onSubmit={handleSubmit}>          {/* 容器 */}
            <AddressSection>                    {/* 容器 */}
                <Input name="street" />         {/* 叶 */}
                <Input name="city" />           {/* 叶 */}
            </AddressSection>
            <PaymentSection>                    {/* 容器 */}
                <CreditCardInput />             {/* 叶 */}
            </PaymentSection>
            <Button label="Place Order" />      {/* 叶 */}
        </Form>
    );
}
// React 递归渲染整棵树，不区分叶节点和容器节点
```

---

## 2. 文件系统 — Linux VFS

Linux 虚拟文件系统（VFS）是 Composite：
文件（叶）和目录（容器）都实现 `inode` 接口，
`ls`、`cp`、`chmod` 命令对两者一视同仁。

```bash
# 对文件和目录使用相同命令（Composite 的体现）
chmod 755 file.txt         # 叶节点
chmod -R 755 /var/www/     # 容器节点（-R 递归）

# find 命令遍历树形结构，不区分文件和目录
find /var/log -name "*.log" -mtime +7 -delete
```

```java
// Java NIO 的 Path 接口——文件和目录统一接口
Path path = Paths.get("/var/www");
Files.walk(path)                          // 递归遍历树
     .filter(Files::isRegularFile)
     .forEach(System.out::println);
```

---

## 3. HTML DOM — 树形文档结构

浏览器 DOM 是 Composite：`Element`（容器）和 `TextNode`（叶）
都是 `Node` 的子类，`querySelectorAll`、`addEventListener` 对两者统一。

```javascript
// DOM 操作不区分叶节点和容器节点
document.querySelectorAll('.item').forEach(node => {
    node.addEventListener('click', handler);  // 叶和容器都能绑事件
    node.classList.add('active');             // 叶和容器都能加 class
});

// 递归处理任意深度的 DOM 树
function getTextContent(node) {
    if (node.nodeType === Node.TEXT_NODE) return node.textContent;  // 叶
    return Array.from(node.childNodes)
                .map(getTextContent)  // 递归处理容器
                .join('');
}
```

---

## 4. Java — `java.awt` 组件树 / GraphQL Schema

GraphQL schema 的类型系统是 Composite：
Scalar（叶）和 Object Type（容器）都是 `GraphQLType`，
schema 递归验证和解析。

```typescript
// GraphQL schema（简化）
const UserType = new GraphQLObjectType({    // 容器
    name: 'User',
    fields: {
        id:    { type: GraphQLID },         // 叶（Scalar）
        name:  { type: GraphQLString },     // 叶
        posts: {                            // 容器（嵌套对象）
            type: new GraphQLList(PostType),
            resolve: (user) => getPostsByUser(user.id),
        },
    },
});
// GraphQL 引擎递归遍历 schema 树，验证 query，叶和容器统一处理
```

---

## 5. Python — `pathlib.Path`

Python 3 的 `pathlib` 统一了文件和目录操作。

```python
from pathlib import Path

root = Path("/var/www/html")

# 递归遍历，叶（文件）和容器（目录）统一接口
for path in root.rglob("*.html"):
    print(path)           # 不管嵌套多深，接口一致

# 组合路径（像构建树一样）
config = Path("/etc") / "nginx" / "nginx.conf"  # 运算符重载，链式组合
print(config.exists())
print(config.read_text())
```

---

## Python 生态

Python 的魔术方法（`__len__`、`__iter__`、`__contains__`）让 Composite 节点能融入语言特性，像内置容器一样使用。

```python
from __future__ import annotations
from abc import ABC, abstractmethod
from typing import Iterator

# 1. 文件系统 Composite（带魔术方法）
class FileSystemNode(ABC):
    def __init__(self, name: str):
        self.name = name

    @abstractmethod
    def size(self) -> int: ...

    @abstractmethod
    def __iter__(self) -> Iterator["FileSystemNode"]: ...

    @abstractmethod
    def __len__(self) -> int: ...

    def __repr__(self) -> str:
        return f"{type(self).__name__}({self.name!r})"

class File(FileSystemNode):
    def __init__(self, name: str, size_bytes: int):
        super().__init__(name)
        self._size = size_bytes

    def size(self) -> int:
        return self._size

    def __iter__(self) -> Iterator[FileSystemNode]:
        return iter([])          # 叶节点：空迭代器

    def __len__(self) -> int:
        return 0                 # 叶节点：没有子节点

class Directory(FileSystemNode):
    def __init__(self, name: str):
        super().__init__(name)
        self._children: list[FileSystemNode] = []

    def add(self, node: FileSystemNode) -> "Directory":
        self._children.append(node)
        return self              # 返回 self，支持链式添加

    def size(self) -> int:
        return sum(child.size() for child in self._children)   # 递归汇总

    def __iter__(self) -> Iterator[FileSystemNode]:
        return iter(self._children)

    def __len__(self) -> int:
        return len(self._children)

    def __contains__(self, name: str) -> bool:
        return any(child.name == name for child in self._children)

# 使用：叶节点和容器节点统一处理
root = (
    Directory("project")
    .add(File("README.md", 2048))
    .add(
        Directory("src")
        .add(File("main.py", 4096))
        .add(File("utils.py", 1024))
    )
    .add(Directory("tests").add(File("test_main.py", 2048)))
)

print(root.size())          # 9216（所有文件大小之和）
print(len(root))            # 3（直接子节点数）
print("src" in root)        # True（__contains__ 让 in 操作符可用）

# for 循环遍历直接子节点（__iter__ 让 for 可用）
for child in root:
    print(f"{child.name}: {child.size()} bytes")

# 2. pathlib.Path — 标准库的 Composite 实现
from pathlib import Path

# Path 对象既可以是文件（叶）也可以是目录（容器），接口统一
p = Path(".")
for item in p.iterdir():          # 遍历目录（Composite 的 iterate）
    if item.is_dir():
        total = sum(f.stat().st_size for f in item.rglob("*") if f.is_file())
        print(f"DIR  {item.name}: {total} bytes")
    else:
        print(f"FILE {item.name}: {item.stat().st_size} bytes")

# 3. 递归打印树（利用 __iter__ 统一遍历）
def print_tree(node: FileSystemNode, indent: int = 0) -> None:
    prefix = "  " * indent
    print(f"{prefix}{node.name} ({node.size()} B)")
    for child in node:           # __iter__ 让 for 对叶节点也安全（空迭代器）
        print_tree(child, indent + 1)

print_tree(root)
```

> **Python 洞察**：实现 `__iter__` + `__len__` + `__contains__` 让 Composite 节点
> 与 Python 内置的 `for`、`len()`、`in` 无缝集成。
> `pathlib.Path` 是标准库中最典型的 Composite——文件和目录共享同一接口。

---

## 关键洞察

> Composite 模式让你写出**递归结构的代码**而不需要针对叶/容器分支。
> 识别信号：当你发现 `if isinstance(node, Leaf)` 和 `if isinstance(node, Container)`
> 这样的判断遍布代码，就是该用 Composite 统一接口的时候了。
