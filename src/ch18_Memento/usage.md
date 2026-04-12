# Memento 模式 — 真实应用

核心：**在不破坏封装的前提下，捕获对象的内部状态，以便之后恢复。**

---

## 1. Git — 每次 commit 是一个 Memento

Git 是 Memento 模式在版本控制领域的完美体现。
每个 commit 是一个状态快照，`checkout`/`reset` 是恢复操作。

```bash
# 创建 Memento（保存状态）
git commit -m "feat: add payment service"
# 每个 commit hash 是一个 Memento 的 ID

# 查看历史 Memento
git log --oneline
# a3f8c21 feat: add payment service
# b7d4e10 fix: null pointer in user service
# c1a9f32 init: project setup

# 恢复到某个 Memento
git checkout b7d4e10   # 回到某个历史状态（不修改历史）
git reset --hard b7d4e10  # 回滚到历史状态（丢弃之后的 commit）
git revert a3f8c21    # 创建新 commit 撤销某次变更（保留历史）
```

---

## 2. VS Code / 编辑器 — Undo/Redo

文本编辑器的撤销/重做是 Memento 最经典的应用场景。

```typescript
// VS Code 内部（简化）
interface EditorMemento {
    content: string;
    cursorPosition: { line: number; column: number };
    selections: Selection[];
    timestamp: number;
}

class TextEditor {
    private history: EditorMemento[] = [];
    private historyIndex = -1;
    private content = '';

    type(text: string) {
        this.saveMemento();      // 修改前保存快照
        this.content += text;
    }

    saveMemento(): void {
        // 截断 redo 历史（新操作后不能再 redo）
        this.history = this.history.slice(0, this.historyIndex + 1);
        this.history.push({ content: this.content, cursorPosition: this.cursor });
        this.historyIndex++;
    }

    undo(): void {
        if (this.historyIndex <= 0) return;
        this.historyIndex--;
        this.restore(this.history[this.historyIndex]);
    }

    redo(): void {
        if (this.historyIndex >= this.history.length - 1) return;
        this.historyIndex++;
        this.restore(this.history[this.historyIndex]);
    }
}
```

---

## 3. Redux DevTools — 时间旅行调试

Redux 把应用的每次状态变化都存为 Memento，
开发者可以在历史状态间"时间旅行"。

```typescript
// Redux 每次 dispatch 都创建一个 Memento（新 state 快照）
const store = configureStore({
    reducer: rootReducer,
    // Redux DevTools Extension 自动保存所有历史状态
    devTools: process.env.NODE_ENV !== 'production',
});

// Redux DevTools 的核心逻辑（简化）
const mementos: State[] = [];

store.subscribe(() => {
    mementos.push(store.getState());  // 保存每个状态快照
});

// 时间旅行：跳到第 N 个历史状态
function travelTo(index: number) {
    store.dispatch({ type: '@@redux/TIME_TRAVEL', state: mementos[index] });
}
```

---

## 4. Python — 游戏存档

游戏存档是 Memento 的直觉化场景，也是书里原始例子的延伸。

```python
import json
from dataclasses import dataclass, asdict
from datetime import datetime
from pathlib import Path

@dataclass
class GameState:
    level: int
    health: int
    position: tuple[float, float]
    inventory: list[str]
    score: int

class GameSaveManager:
    SAVE_DIR = Path("~/.mygame/saves").expanduser()

    def save(self, state: GameState, slot: int = 0):
        self.SAVE_DIR.mkdir(parents=True, exist_ok=True)
        save_file = self.SAVE_DIR / f"save_{slot}.json"
        data = asdict(state) | {"timestamp": datetime.now().isoformat()}
        save_file.write_text(json.dumps(data, indent=2))

    def load(self, slot: int = 0) -> GameState:
        save_file = self.SAVE_DIR / f"save_{slot}.json"
        data = json.loads(save_file.read_text())
        data.pop("timestamp")
        return GameState(**data)

# 使用
manager = GameSaveManager()
manager.save(current_state, slot=1)   # 创建 Memento
# ... 玩了一会儿，死了 ...
restored = manager.load(slot=1)       # 恢复 Memento
```

---

## 5. React — `useReducer` + 历史记录

React 里实现 undo/redo，用 `useReducer` 管理历史状态栈。

```typescript
interface HistoryState<T> {
    past: T[];
    present: T;
    future: T[];
}

function historyReducer<T>(state: HistoryState<T>, action: Action): HistoryState<T> {
    switch (action.type) {
        case 'UPDATE':
            return {
                past: [...state.past, state.present],  // 当前状态入栈（Memento）
                present: action.payload,
                future: [],
            };
        case 'UNDO':
            if (state.past.length === 0) return state;
            const [previous, ...rest] = [...state.past].reverse();
            return {
                past: rest.reverse(),
                present: previous,                     // 恢复 Memento
                future: [state.present, ...state.future],
            };
        case 'REDO':
            if (state.future.length === 0) return state;
            const [next, ...remainingFuture] = state.future;
            return {
                past: [...state.past, state.present],
                present: next,
                future: remainingFuture,
            };
    }
}
```

---

## Python 生态

Python 的 `pickle`、`copy`、`dataclasses` 为 Memento 提供了多种状态序列化方案。

```python
import copy
import pickle
from dataclasses import dataclass, asdict
from typing import Any

# 1. dataclasses + asdict — 轻量级 Memento（不可变快照）
@dataclass
class EditorState:
    content: str
    cursor: int
    selection: tuple[int, int] | None = None

@dataclass
class TextEditor:
    _state: EditorState
    _history: list[EditorState]

    def __init__(self, content: str = ""):
        self._state = EditorState(content=content, cursor=0)
        self._history = []

    def type(self, text: str) -> None:
        self._save()                          # 修改前保存快照
        pos = self._state.cursor
        new_content = self._state.content[:pos] + text + self._state.content[pos:]
        self._state = EditorState(
            content=new_content,
            cursor=pos + len(text),
            selection=None,
        )

    def delete(self, n: int = 1) -> None:
        self._save()
        pos = self._state.cursor
        new_content = self._state.content[:pos - n] + self._state.content[pos:]
        self._state = EditorState(content=new_content, cursor=max(0, pos - n))

    def undo(self) -> bool:
        if not self._history:
            return False
        self._state = self._history.pop()    # 恢复快照
        return True

    def _save(self) -> None:
        self._history.append(copy.copy(self._state))   # 浅拷贝即可（dataclass 是不可变快照）

    @property
    def content(self) -> str:
        return self._state.content

editor = TextEditor()
editor.type("Hello")
editor.type(", World")
print(editor.content)   # Hello, World
editor.undo()
print(editor.content)   # Hello
editor.undo()
print(editor.content)   # (empty)

# 2. pickle — 完整对象序列化（适合复杂状态）
class GameState:
    def __init__(self):
        self.level = 1
        self.score = 0
        self.inventory = []
        self.position = (0, 0)

    def save(self, path: str) -> None:
        """保存存档（Memento 序列化到磁盘）"""
        with open(path, "wb") as f:
            pickle.dump(self, f)

    @classmethod
    def load(cls, path: str) -> "GameState":
        """加载存档（恢复 Memento）"""
        with open(path, "rb") as f:
            return pickle.load(f)

    def __getstate__(self) -> dict:
        """自定义序列化内容（排除不需要保存的字段）"""
        state = self.__dict__.copy()
        # 排除运行时缓存、连接等不可序列化的字段
        state.pop("_runtime_cache", None)
        return state

# 3. shelve — 持久化键值存储（命名 Memento）
import shelve

def save_checkpoint(name: str, state: dict) -> None:
    with shelve.open("checkpoints") as db:
        db[name] = state   # 自动 pickle 序列化

def load_checkpoint(name: str) -> dict | None:
    with shelve.open("checkpoints") as db:
        return db.get(name)

# 4. JSON 快照 — 可读性最强（适合配置和简单状态）
import json
from dataclasses import dataclass, asdict

@dataclass
class AppConfig:
    theme: str = "dark"
    font_size: int = 14
    plugins: list[str] = None

    def snapshot(self) -> str:
        """导出为 JSON Memento"""
        return json.dumps(asdict(self), indent=2)

    @classmethod
    def from_snapshot(cls, snapshot: str) -> "AppConfig":
        """从 JSON Memento 恢复"""
        return cls(**json.loads(snapshot))

config = AppConfig(theme="light", font_size=16, plugins=["git", "python"])
snapshot = config.snapshot()
restored = AppConfig.from_snapshot(snapshot)
```

> **Python 洞察**：`pickle` 是最强大的 Python Memento——可以序列化几乎任何对象，
> 但不安全（不要 unpickle 不信任的数据）。
> `dataclasses.asdict` + `json` 是生产中更常用的选择：可读、可调试、跨语言兼容。

---

## 关键洞察

> Memento 的本质是**快照（snapshot）**。
> Git commit、游戏存档、编辑器 undo、Redux 时间旅行——都是同一个思想。
> 现代实现通常结合**不可变数据结构**（Immutable.js、Immer）提升效率：
> 不是深拷贝整个状态，而是只记录变化的部分（structural sharing）。
