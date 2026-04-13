# Chapter 20: Memento

> 类型: 行为 | 难度: ★★★ | 原书: `src/ch18_Memento/` | 前置: Ch13 (Command)

---

## 模式速览

**问题**: 你需要给一个对象实现"撤销（undo）"功能。最直觉的做法是直接把对象的内部字段暴露给外部，让外部保存和恢复。但这样做破坏了封装——外部代码开始依赖内部实现细节，一旦字段改名或类型变化，所有保存/恢复逻辑都要跟着改。Memento 模式的解决方案是：让 Originator 自己负责"打包内部状态"，生成一个不透明的快照对象（Memento），外部只管收存这个快照，需要时再交还给 Originator 恢复——外部永远看不到快照里有什么，封装不破。

```
  Originator                  Memento
  ┌─────────────────┐         ┌──────────────────────┐
  │ - state: S      │  创建   │ + state: S (private) │
  │ createMemento() │────────▶│                      │
  │ restore(m)      │◀────────│  仅 Originator 可读  │
  └─────────────────┘  恢复   └──────────────────────┘
           ▲                            ▲
           │                            │ 持有（不读取）
           │                   Caretaker
           │                   ┌─────────────────────┐
           │                   │ - history: Deque<M> │
           └───────────────────│ save() / undo()     │
                               └─────────────────────┘
```

**三个角色**:
- `Originator` — 有内部状态的对象，负责生成和消费 Memento；唯一知道 Memento 里装了什么
- `Memento` — 状态快照，对 Caretaker 是不透明的黑盒
- `Caretaker` — 历史管理者，只知道"存哪个快照"，不知道快照内容

**封装的两个层次**: Originator 拥有"宽接口"（可以读写 Memento 的内容），Caretaker 只拥有"窄接口"（只能持有和归还 Memento，不能读其内容）。这个双接口设计是 Memento 模式封装保护的核心。

---

## 本章新语言特性

| 特性 | Java | Python |
|------|------|--------|
| 不可变快照 | `record`（Java 16+）天生 final 字段 | `@dataclass(frozen=True)` |
| 类型安全检查 | `instanceof` 模式匹配（Java 16+）| `match/case` 类模式（Python 3.10+）|
| 序列化快照 | `java.io.Serializable` → 字节数组 | `pickle.dumps()` / `pickle.loads()` |
| 深拷贝快照 | `Cloneable` / 手动拷贝构造 | `copy.deepcopy()` |
| 双端历史栈 | `Deque<Memento>`（`ArrayDeque`）| `collections.deque` |

### `record` — Java 的完美 Memento 载体（Java 16+）

`record` 声明自动生成：所有字段的 `final` 修饰、构造器、`equals()`、`hashCode()`、`toString()`。对 Memento 来说，**不可变**恰好是最重要的性质——快照一旦拍下就不应被修改。

```java
// 一行声明代替十几行 POJO
record GameMemento(int money, int level, List<String> items) {
    // 紧凑构造器：做防御性拷贝，防止 List 被外部修改
    GameMemento {
        items = List.copyOf(items);   // Java 9+，返回不可变副本
    }
}

// 使用：字段通过访问器读取，没有 setter
var m = new GameMemento(100, 3, List.of("剑", "盾"));
System.out.println(m.money());  // 100
System.out.println(m.level());  // 3
// m.money = 999;  // 编译错误：record 字段是 final 的
```

### `instanceof` 模式匹配（Java 16+）

当 Caretaker 管理多种类型的 Memento 时（不同游戏存档格式、不同版本快照），模式匹配让类型检查和变量绑定合并为一行：

```java
// 旧写法：先 instanceof，再强转，两步都可能出错
if (snapshot instanceof GameMemento) {
    GameMemento gm = (GameMemento) snapshot;  // 强转可能多余的冗余
    System.out.println(gm.level());
}

// Java 16+ 模式匹配：检查 + 绑定一步到位
if (snapshot instanceof GameMemento gm) {
    System.out.println(gm.level());  // gm 在此作用域内类型确定，无需强转
}

// Java 21+ record 模式：直接解构字段
if (snapshot instanceof GameMemento(var money, var level, var items)) {
    System.out.println("存档金钱: " + money + ", 等级: " + level);
}
```

### Python `match/case` — 结构模式匹配（Python 3.10+）

```python
from dataclasses import dataclass

@dataclass(frozen=True)   # frozen=True 使所有字段不可变，等价于 record
class GameMemento:
    money: int
    level: int
    items: tuple[str, ...]   # 用 tuple 而非 list，天生不可变

# match/case 类模式：按字段值分支
def describe_save(snapshot: GameMemento) -> str:
    match snapshot:
        case GameMemento(level=lvl) if lvl >= 10:
            return f"高等级存档 (等级 {lvl})"   # guard 条件
        case GameMemento(money=0, level=1):
            return "新游戏存档"
        case GameMemento(level=lvl):
            return f"普通存档 (等级 {lvl})"
```

---

## Java 实战: `record` + `Deque` 构建 undo 系统

### 完整示例：文字冒险游戏的存档与回滚

```java
package ch20_Memento;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.ArrayList;

// ─── Memento：不可变状态快照，Caretaker 只能持有，不能解读 ───
record GameMemento(int money, int level, List<String> items) {
    // 紧凑构造器：防御性拷贝，快照内容永远不受外部影响
    GameMemento {
        items = List.copyOf(items);
    }
}

// ─── Originator：游戏状态，知道如何创建和恢复快照 ───
class Gamer {
    private int money;
    private int level;
    private final List<String> items = new ArrayList<>();

    public Gamer(int initialMoney) {
        this.money = initialMoney;
        this.level = 1;
    }

    // 宽接口：Originator 自己打包内部状态
    public GameMemento createMemento() {
        return new GameMemento(money, level, items);   // items 会在构造器里被拷贝
    }

    // 宽接口：Originator 自己从快照恢复
    public void restore(GameMemento m) {
        this.money = m.money();
        this.level = m.level();
        this.items.clear();
        this.items.addAll(m.items());
    }

    // 模拟一轮游戏事件
    public void playRound() {
        var random = Math.random();
        if (random < 0.4) {
            // 好运：赚钱升级
            money += 100;
            level += 1;
            System.out.printf("  好运！金钱 +100 → %d，等级 → %d%n", money, level);
        } else if (random < 0.7) {
            // 普通：获得道具
            var loot = "道具" + (items.size() + 1);
            items.add(loot);
            System.out.printf("  获得 %s，共 %d 件道具%n", loot, items.size());
        } else {
            // 坏运：损失金钱
            money = Math.max(0, money - 50);
            System.out.printf("  倒霉！金钱 -50 → %d%n", money);
        }
    }

    @Override
    public String toString() {
        return String.format("[金钱=%d, 等级=%d, 道具=%s]", money, level, items);
    }
}

// ─── Caretaker：历史管理器，只管存取，不读内容 ───
class GameCaretaker {
    // 双端队列作为历史栈：push 压栈，pop 弹栈，O(1) 操作
    private final Deque<GameMemento> history = new ArrayDeque<>();
    private static final int MAX_HISTORY = 10;   // 防止无限增长

    // 窄接口：Caretaker 只做存储
    public void save(GameMemento memento) {
        history.push(memento);
        if (history.size() > MAX_HISTORY) {
            // 移除最旧的快照（双端队列的尾部）
            history.pollLast();
        }
    }

    // 窄接口：弹出上一个快照，让 Originator 去恢复
    public GameMemento undo() {
        if (history.isEmpty()) {
            throw new IllegalStateException("没有可撤销的历史记录");
        }
        return history.pop();
    }

    public int historySize() {
        return history.size();
    }
}

// ─── Main：把三个角色串联起来 ───
class Main {
    public static void main(String[] args) {
        var gamer = new Gamer(500);
        var caretaker = new GameCaretaker();

        System.out.println("=== 开始游戏 ===");
        System.out.println("初始状态: " + gamer);

        for (int round = 1; round <= 6; round++) {
            System.out.printf("%n--- 第 %d 轮 ---%n", round);

            // 每 2 轮存档一次
            if (round % 2 == 1) {
                caretaker.save(gamer.createMemento());
                System.out.println("  [存档] " + gamer);
            }

            gamer.playRound();
            System.out.println("  当前状态: " + gamer);
        }

        // 演示 undo：用 instanceof 模式匹配检查快照内容
        System.out.println("\n=== 撤销到上一存档 ===");
        var snapshot = caretaker.undo();

        // Java 21+ record 模式解构
        if (snapshot instanceof GameMemento(var money, var level, var items)) {
            System.out.printf("恢复到: 金钱=%d, 等级=%d, 道具数=%d%n",
                              money, level, items.size());
        }

        gamer.restore(snapshot);
        System.out.println("恢复后状态: " + gamer);
    }
}
```

### `Serializable` — 序列化为字节数组的 Memento

当游戏需要存盘到文件（跨进程、跨重启持久化）时，Java 序列化把整个对象图转为字节数组，天然成为"可持久化的 Memento"：

```java
import java.io.*;

// Memento 实现 Serializable：标记接口，无需实现任何方法
record GameMemento(int money, int level, List<String> items)
        implements Serializable {
    private static final long serialVersionUID = 1L;   // 版本控制

    GameMemento {
        items = List.copyOf(items);
    }
}

// 序列化：对象 → 字节数组（可写入文件/数据库/网络）
public static byte[] serialize(GameMemento m) throws IOException {
    try (var baos = new ByteArrayOutputStream();
         var oos  = new ObjectOutputStream(baos)) {
        oos.writeObject(m);
        return baos.toByteArray();
    }
}

// 反序列化：字节数组 → 对象
public static GameMemento deserialize(byte[] data)
        throws IOException, ClassNotFoundException {
    try (var bais = new ByteArrayInputStream(data);
         var ois  = new ObjectInputStream(bais)) {
        return (GameMemento) ois.readObject();
    }
}

// Caretaker 改为存 byte[]：跨会话持久化
class PersistentCaretaker {
    private final Deque<byte[]> history = new ArrayDeque<>();

    public void save(GameMemento m) throws IOException {
        history.push(serialize(m));   // 存字节，不存对象引用
    }

    public GameMemento undo() throws IOException, ClassNotFoundException {
        return deserialize(history.pop());
    }
}
```

**为什么用字节数组而非直接存对象引用？** 因为存引用意味着 Memento 和 Originator 共享可变状态——如果 Originator 后续修改了内部的 `List`，"快照"也跟着变了。字节数组是真正的深拷贝，快照内容与 Originator 彻底解耦。

---

## Python 实战: `pickle` + `dataclasses` + `copy`

### 四种快照策略对比

```python
import pickle
import copy
import json
from dataclasses import dataclass, asdict, field

@dataclass
class EditorState:
    """文字编辑器状态：Originator 的内部状态"""
    content: str
    cursor_pos: int
    selection: tuple[int, int] | None = None

    def word_count(self) -> int:
        return len(self.content.split())
```

**策略一：`pickle` — 最完整，任意 Python 对象**

```python
# 序列化：任何 Python 对象（包括自定义类）→ bytes
snapshot: bytes = pickle.dumps(state)

# 反序列化：bytes → 对象，恢复原始类型和值
restored: EditorState = pickle.loads(snapshot)

# 优点：支持任意对象，一行搞定；适合进程内历史或本地文件
# 缺点：bytes 不跨语言，不可读，反序列化不可信来源有安全风险
```

**策略二：`dataclasses.asdict` + `json` — 可读，跨语言**

```python
# 序列化：dataclass → dict → JSON 字符串
snapshot_dict: dict = asdict(state)
snapshot_json: str  = json.dumps(snapshot_dict, ensure_ascii=False)
# 输出: {"content": "hello", "cursor_pos": 5, "selection": null}

# 反序列化：JSON 字符串 → dict → dataclass（需手写解包）
data = json.loads(snapshot_json)
restored = EditorState(**data)

# 优点：人类可读，可存数据库，跨语言兼容；适合云端同步、调试
# 缺点：只支持 JSON 兼容类型（str/int/list/dict），tuple 会变 list
```

**策略三：`copy.deepcopy` — 最简单，进程内使用**

```python
# 深拷贝：递归复制对象及其所有引用，完全独立
snapshot: EditorState = copy.deepcopy(state)

# 恢复：直接替换引用
state = copy.deepcopy(snapshot)

# 优点：一行，无需任何序列化约定；适合快速原型
# 缺点：慢（递归遍历整个对象图），不能持久化到文件或网络
```

**策略四：`frozen dataclass` — 类型安全的不可变快照**

```python
@dataclass(frozen=True)   # frozen=True：所有字段生成 __hash__，禁止赋值
class EditorMemento:
    content: str
    cursor_pos: int
    selection: tuple[int, int] | None = None
    # 用 tuple 而非 list：tuple 天生 hashable，frozen dataclass 可以放入 set/dict

# 尝试修改会抛 FrozenInstanceError
m = EditorMemento("hello", 5)
# m.cursor_pos = 10   # FrozenInstanceError: cannot assign to field 'cursor_pos'
```

### 完整 undo 系统示例

```python
from __future__ import annotations
import pickle
from collections import deque
from dataclasses import dataclass, field

# ─── Memento：frozen dataclass = 不可变快照 ───
@dataclass(frozen=True)
class EditorMemento:
    content: str
    cursor_pos: int

# ─── Originator：文字编辑器 ───
class TextEditor:
    def __init__(self) -> None:
        self._content: str = ""
        self._cursor: int  = 0

    # 宽接口：打包状态（Originator 自己知道打包什么）
    def save(self) -> EditorMemento:
        return EditorMemento(self._content, self._cursor)

    # 宽接口：从快照恢复
    def restore(self, m: EditorMemento) -> None:
        self._content = m.content
        self._cursor  = m.cursor_pos

    def type(self, text: str) -> None:
        """在光标位置插入文字"""
        self._content = (
            self._content[:self._cursor] + text + self._content[self._cursor:]
        )
        self._cursor += len(text)

    def delete(self, count: int = 1) -> None:
        """向前删除 count 个字符"""
        start = max(0, self._cursor - count)
        self._content = self._content[:start] + self._content[self._cursor:]
        self._cursor  = start

    def __repr__(self) -> str:
        # 用 | 标记光标位置，方便调试
        c = self._content
        pos = self._cursor
        return f'"{c[:pos]}|{c[pos:]}"  (光标={pos})'

# ─── Caretaker：历史管理器 ───
class EditorHistory:
    def __init__(self, max_size: int = 20) -> None:
        # deque 设置 maxlen 后自动丢弃最旧的记录，防止内存无限增长
        self._stack: deque[EditorMemento] = deque(maxlen=max_size)

    def push(self, m: EditorMemento) -> None:
        self._stack.append(m)

    def pop(self) -> EditorMemento:
        if not self._stack:
            raise IndexError("没有可撤销的历史")
        return self._stack.pop()

    def __len__(self) -> int:
        return len(self._stack)

# ─── 使用示例 ───
def demo() -> None:
    editor  = TextEditor()
    history = EditorHistory()

    def checkpoint() -> None:
        """存档当前状态"""
        history.push(editor.save())

    checkpoint()
    editor.type("Hello")
    print("输入后:", editor)

    checkpoint()
    editor.type(", World")
    print("继续输入:", editor)

    checkpoint()
    editor.delete(5)     # 删除 "World"
    print("删除后:", editor)

    # undo 两次
    editor.restore(history.pop())
    print("撤销 1 次:", editor)

    editor.restore(history.pop())
    print("撤销 2 次:", editor)

demo()
```

### `match/case` 检查快照版本

当系统升级、Memento 格式演变时，结构模式匹配让版本分支清晰：

```python
@dataclass(frozen=True)
class MementoV1:
    content: str

@dataclass(frozen=True)
class MementoV2:
    content: str
    cursor_pos: int

def restore_any_version(editor: TextEditor, snapshot: object) -> None:
    """兼容多版本快照的恢复逻辑"""
    match snapshot:
        case MementoV2(content=c, cursor_pos=pos):
            # 最新版本：直接恢复所有字段
            editor.restore(MementoV2(c, pos))
            print(f"从 V2 快照恢复，光标={pos}")

        case MementoV1(content=c):
            # 旧版本：光标默认放到末尾
            editor.restore(MementoV2(c, len(c)))
            print("从 V1 快照迁移，光标移到末尾")

        case _:
            raise TypeError(f"未知快照类型: {type(snapshot)}")
```

---

## 两种哲学

| 维度 | Java | Python |
|------|------|--------|
| 快照载体 | `record` — 编译器强制不可变，类型固定 | `@dataclass(frozen=True)` — 运行时检查，更灵活 |
| 序列化 | `Serializable` — 强类型字节协议，有 `serialVersionUID` 版本控制 | `pickle` — 任意对象，无版本机制；`json` — 跨语言可读 |
| 封装保护 | package-private 内部类（Caretaker 在其他包则看不到字段）| 约定 `_` 前缀（无语言级强制，靠纪律） |
| 类型检查 | `instanceof` 模式匹配，编译期捕获类型错误 | `match/case` 结构匹配，运行时；`mypy` 补静态检查 |
| 深拷贝方式 | `record` 紧凑构造器手动 `List.copyOf()` | `copy.deepcopy()` 一行，或 `pickle` 序列化往返 |
| 历史栈结构 | `ArrayDeque<Memento>` — 泛型类型安全 | `collections.deque` — 动态类型，`maxlen` 防泄漏 |

**Java 的 `record` 为什么是天然 Memento？**

GoF 原书对 Memento 有一个核心要求：快照对象在创建之后不能被修改——否则"历史"就失去意义了。`record` 的所有字段都是 `final` 的，语言层面保证了这一点，不依赖开发者的自律。同时 `record` 自动生成 `equals()` 和 `hashCode()`，让"检查两个快照是否相同"变得正确且廉价。

**Python 的字典快照什么时候最合适？**

当被快照的状态结构会随着需求演变（比如新增字段），用 `dict` / `json` 存储比固定 `dataclass` 更灵活——旧快照只是少几个 key，新代码读取时用 `.get(key, default)` 兼容。对于需要跨进程、跨机器传输的快照，`json` 序列化也是唯一实用选择。对于纯进程内的快速 undo，`copy.deepcopy()` 一行解决，不需要过度设计。

---

## 动手练习

### 20.1 Java — 带分支的存档系统

实现一个支持"多分支存档"的 `BranchCaretaker`，使以下代码能工作：

```java
var gamer = new Gamer(500);
var caretaker = new BranchCaretaker();

caretaker.save("主线", gamer.createMemento());   // 存到名为"主线"的分支
gamer.playRound();
caretaker.save("支线", gamer.createMemento());   // 存到名为"支线"的分支

gamer.restore(caretaker.load("主线"));  // 恢复主线存档
// 要求：
// 1. 每个分支是一个独立的 Deque<GameMemento>
// 2. load(branch) 弹出该分支最新存档；分支不存在或为空时抛 NoSuchElementException
// 3. Caretaker 只用 Map<String, Deque<GameMemento>> 存储，不添加其他状态
```

### 20.2 Python — 基于 json 的持久化存档

为 `TextEditor` 实现一个 `JsonCaretaker`，把历史快照以 JSON 格式写入文件，程序重启后能继续 undo：

```python
class JsonCaretaker:
    def __init__(self, path: str) -> None:
        self.path = path
        # 启动时从文件加载历史（文件不存在则初始化为空）

    def push(self, m: EditorMemento) -> None:
        # 追加快照到文件（保持所有历史）
        ...

    def pop(self) -> EditorMemento:
        # 弹出最新快照，更新文件
        ...

# 要求：
# 1. 文件格式：JSON 数组，每个元素是一个快照的 dict
# 2. 程序 A 存档后退出，程序 B 启动后能 undo 到程序 A 留下的历史
# 3. pop() 在历史为空时抛 IndexError
```

### 20.3 跨语言思考题

1. **封装的代价**: Java 可以用 `package-private` 类（不写 `public`）让 Caretaker 无法访问 Memento 内部字段；Python 只能用 `_` 前缀约定。在团队协作中，哪种方式更可靠？什么场景下 Python 的约定已经足够？

2. **内存与历史深度**: 如果每次按键都存一个快照（Word 的实际做法），用户敲了 10000 个字符，内存里有 10000 个快照，每个快照包含完整文本——内存会线性增长。真实的撤销系统如何优化？（提示：差量存储，只存"变化了什么"而非"完整状态是什么"——这与 Command 模式有什么关系？）

3. **Memento vs Command**: Command 模式的 undo 是执行"逆操作"，Memento 是恢复"旧快照"。对于"删除一段文字"这个操作，用哪种方式实现 undo 更好？各自的优劣是什么？

4. **版本兼容**: 你发布了一个应用，用户本地存有 `MementoV1` 格式的快照文件。你更新了应用，Memento 新增了字段变成 `MementoV2`。如何让新版应用能正确读取旧版存档？分别给出 Java (`Serializable`) 和 Python (`json`) 的解决思路。

---

## 回顾与连接

**本章建立的概念**:
- Memento 的本质是"状态外化"——把本应留在对象内部的状态，以受控方式暂时外借给 Caretaker，用完再还回来，全程不破坏封装
- "宽接口 vs 窄接口"双接口设计：对知情者（Originator）开放完整访问，对无关者（Caretaker）只开放持有权
- `record`（Java）和 `frozen dataclass`（Python）都是把"不可变"从纪律变成语言保证的工具

**与其他章节的关联**:

| 章节 | 关联方式 |
|------|----------|
| **Ch13 Command** | Command + Memento = 完整 undo/redo 系统：Command 记录"做了什么"，Memento 记录"做之前是什么"；两者互补而非重复 |
| **Ch11 Prototype** | Prototype 用 `clone()` 复制活跃对象（拿来继续使用），Memento 用快照保存历史状态（拿来回退）；相似机制，不同目的 |
| **Ch14 State** | State 模式管理"当前状态是哪个"，Memento 管理"历史状态快照的集合"；二者常配合——State 的状态对象天然适合作为 Memento |
| **Ch08 Observer** | Caretaker 可以监听 Originator 的变更事件，自动触发存档，而非让 Originator 主动调用 `save()` |

> **核心洞察**: Memento 模式的精妙之处不在于"保存状态"（任何变量都能保存状态），而在于**在不破坏封装的前提下**保存状态。它划定了一条清晰的界限：Originator 负责"打包和解包"，Caretaker 只负责"保管"，各司其职，互不越权。这个原则在任何需要持久化、回滚、审计日志的系统中都普遍适用。
