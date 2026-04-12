# Abstract Factory 模式 — 真实应用

核心：**一套接口创建一族相关对象，保证同族对象的兼容性。切换整套实现只改工厂。**

---

## 1. Java AWT/Swing — Look and Feel

教科书级别的 Abstract Factory 应用。
一套 UI 组件（Button、Checkbox、ScrollBar）有 Windows 版和 macOS 版，
切换主题只换 Factory。

```java
// 抽象工厂接口
interface UIFactory {
    Button   createButton();
    Checkbox createCheckbox();
    TextField createTextField();
}

// Windows 风格工厂
class WindowsUIFactory implements UIFactory {
    public Button    createButton()    { return new WindowsButton(); }
    public Checkbox  createCheckbox()  { return new WindowsCheckbox(); }
    public TextField createTextField() { return new WindowsTextField(); }
}

// 实际 Swing 代码：通过 UIManager 切换整套 L&F
UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
// 所有后续创建的组件都用 Nimbus 风格，代码零修改
```

---

## 2. AWS SDK v3 — Client 工厂族

AWS SDK 为每个服务提供一族客户端，可以统一配置（region、credentials、endpoint）。
切换到 LocalStack（本地模拟）只换配置，不改业务代码。

```typescript
// 抽象工厂：统一配置创建一族 AWS 客户端
function createAWSFactory(config: AWSConfig) {
    const baseConfig = {
        region: config.region,
        credentials: config.credentials,
        endpoint: config.endpoint,  // LocalStack: http://localhost:4566
    };

    return {
        s3:       () => new S3Client(baseConfig),
        dynamodb: () => new DynamoDBClient(baseConfig),
        sqs:      () => new SQSClient(baseConfig),
        sns:      () => new SNSClient(baseConfig),
    };
}

// 生产环境
const aws = createAWSFactory({ region: 'us-east-1', credentials: fromEnv() });

// 本地开发（LocalStack）：只改这一行，所有服务都切换
const aws = createAWSFactory({ region: 'us-east-1', endpoint: 'http://localhost:4566' });

const s3  = aws.s3();
const sqs = aws.sqs();
// 业务代码不变
```

---

## 3. React Native — 跨平台组件族

React Native 根据平台（iOS/Android）提供不同的原生组件实现，
对 JS 层暴露统一 API。

```typescript
// React Native 内部（简化）
// iOS 工厂
const IOSFactory = {
    createButton:   () => <IOSButton />,
    createTextInput: () => <IOSTextInput />,
    createSwitch:   () => <IOSSwitch />,
};

// Android 工厂
const AndroidFactory = {
    createButton:   () => <AndroidButton />,
    createTextInput: () => <AndroidTextInput />,
    createSwitch:   () => <AndroidSwitch />,
};

// 你写的代码（与平台无关）
import { TouchableOpacity, TextInput, Switch } from 'react-native';
// React Native 根据运行平台自动选择对应工厂，你感知不到
```

---

## 4. Python — `unittest.mock` 工厂族

测试里用 Abstract Factory 创建一族 Mock 对象，
替换整套外部依赖（数据库、缓存、消息队列）。

```python
from unittest.mock import MagicMock

def create_test_dependencies():
    """测试用工厂：创建一族 mock 对象，保证相互兼容"""
    db      = MagicMock()
    cache   = MagicMock()
    queue   = MagicMock()

    # 配置 mock 的行为，保证族内一致性
    db.find_user.return_value = User(id=1, name="Alice")
    cache.get.return_value = None  # 模拟缓存未命中

    return db, cache, queue

# 测试：一行切换整套依赖
db, cache, queue = create_test_dependencies()
service = UserService(db=db, cache=cache, queue=queue)
```

---

## 5. Terraform — Provider 抽象

Terraform 的 Provider 是 Abstract Factory 在基础设施领域的应用。
同一套资源定义（server、database、network），可以部署到 AWS、GCP 或 Azure。

```hcl
# 切换 provider = 切换整套"基础设施工厂"
provider "aws" {
  region = "us-east-1"
}
# provider "google" { project = "my-project" }  ← 切换到 GCP 只改这里

# 资源定义保持不变（抽象工厂的产品接口）
resource "aws_instance" "web" {
  ami           = "ami-0c55b159cbfafe1f0"
  instance_type = "t3.micro"
}
```

---

## Factory Method vs Abstract Factory

| | Factory Method | Abstract Factory |
|--|----------------|-----------------|
| 创建 | 一种产品 | 一**族**相关产品 |
| 关注点 | 如何创建一个对象 | 保证一族对象兼容 |
| 切换 | 换一个实现 | 换整套实现 |
| 例子 | `DriverManager.getConnection()` | AWS SDK 客户端族 |

---

## Python 生态

Python 用 `typing.Protocol` 实现结构化的抽象工厂，不需要继承 ABC，只要"形状"匹配即可。

```python
from typing import Protocol

# 1. Protocol 定义产品族接口（无需 ABC）
class Button(Protocol):
    def render(self) -> str: ...
    def on_click(self, handler) -> None: ...

class Dialog(Protocol):
    def show(self, title: str, message: str) -> None: ...

class UIFactory(Protocol):
    """抽象工厂 Protocol"""
    def create_button(self) -> Button: ...
    def create_dialog(self) -> Dialog: ...

# 具体工厂：Web 风格
class WebButton:
    def render(self) -> str:
        return '<button class="web-btn">Click</button>'
    def on_click(self, handler) -> None:
        print(f"Web button click: {handler}")

class WebDialog:
    def show(self, title: str, message: str) -> None:
        print(f'<dialog><h1>{title}</h1><p>{message}</p></dialog>')

class WebUIFactory:
    def create_button(self) -> WebButton:
        return WebButton()
    def create_dialog(self) -> WebDialog:
        return WebDialog()

# 具体工厂：CLI 风格
class CLIButton:
    def render(self) -> str:
        return "[ Click ]"
    def on_click(self, handler) -> None:
        input("Press Enter...")
        handler()

class CLIDialog:
    def show(self, title: str, message: str) -> None:
        print(f"=== {title} ===\n{message}\n" + "=" * (len(title) + 8))

class CLIUIFactory:
    def create_button(self) -> CLIButton:
        return CLIButton()
    def create_dialog(self) -> CLIDialog:
        return CLIDialog()

# 客户端：只依赖 Protocol，不知道具体实现
def render_app(factory: UIFactory) -> None:   # 类型提示是 Protocol，非 ABC
    btn = factory.create_button()
    dlg = factory.create_dialog()
    print(btn.render())
    dlg.show("Welcome", "Hello from Abstract Factory!")

render_app(WebUIFactory())    # Web 风格
render_app(CLIUIFactory())    # CLI 风格（不修改任何客户端代码）

# 2. 环境变量驱动的工厂选择（生产实践）
import os

def get_ui_factory() -> UIFactory:
    env = os.getenv("UI_MODE", "web")
    factories = {
        "web": WebUIFactory,
        "cli": CLIUIFactory,
    }
    cls = factories.get(env)
    if cls is None:
        raise ValueError(f"Unknown UI_MODE: {env}")
    return cls()

# 3. pytest fixtures — 测试中的抽象工厂
# pytest 的 fixture 本质上是抽象工厂：根据环境提供不同的依赖实现
#
# @pytest.fixture
# def db_factory(request):
#     if request.param == "sqlite":
#         yield SQLiteFactory()
#     elif request.param == "postgres":
#         yield PostgresFactory()
#
# @pytest.mark.parametrize("db_factory", ["sqlite", "postgres"], indirect=True)
# def test_crud(db_factory):
#     db = db_factory.create_connection()
#     ...
```

> **Python 洞察**：`Protocol` + 鸭子类型让抽象工厂无需显式继承——
> 只要工厂类有正确的方法，mypy/pyright 就会接受它。
> 这种方式更符合 Python 的"隐式接口"风格，也更容易测试（mock 只需要有对应方法）。

---

## 关键洞察

> Abstract Factory 的价值在于**族内一致性**。
> 如果你只是需要"创建对象时不暴露具体类"，Factory Method 就够了。
> 当你需要确保"一批对象来自同一套实现、相互兼容"时，才需要 Abstract Factory。
