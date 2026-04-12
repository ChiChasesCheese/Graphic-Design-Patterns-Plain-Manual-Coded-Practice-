# Strategy 模式 — 真实应用

核心：**把算法封装成可替换的对象，运行时动态切换，消除 if-else 算法分支。**

---

## 1. Java — `Comparator`

Java 标准库里最常用的 Strategy。排序算法固定，比较策略可替换。

```java
List<User> users = getUsers();

// 策略1：按姓名排序
users.sort(Comparator.comparing(User::getName));

// 策略2：按注册时间倒序
users.sort(Comparator.comparing(User::getCreatedAt).reversed());

// 策略3：多字段组合
users.sort(Comparator.comparing(User::getDepartment)
                     .thenComparing(User::getSalary, Comparator.reverseOrder()));

// sort() 算法不变，只换 Comparator（策略）
// Java 8+ lambda 让 Strategy 模式写起来极其简洁
```

---

## 2. Passport.js — 认证策略

Node.js 最流行的认证库，每种登录方式是一个 Strategy，可以自由组合。

```javascript
const passport = require('passport');
const LocalStrategy   = require('passport-local');
const JwtStrategy     = require('passport-jwt');
const GoogleStrategy  = require('passport-google-oauth20');

// 注册不同策略（可同时存在多个）
passport.use('local', new LocalStrategy(async (username, password, done) => {
    const user = await User.findOne({ username });
    if (!user || !user.verifyPassword(password)) return done(null, false);
    return done(null, user);
}));

passport.use('jwt', new JwtStrategy(jwtOptions, async (payload, done) => {
    const user = await User.findById(payload.sub);
    return done(null, user || false);
}));

// 路由层：选择哪种策略
router.post('/login', passport.authenticate('local'));
router.get('/profile', passport.authenticate('jwt', { session: false }));
router.get('/auth/google', passport.authenticate('google', { scope: ['email'] }));
```

---

## 3. Python — scikit-learn 估计器接口

scikit-learn 所有模型遵循相同接口（`fit` / `predict`），
切换算法只换类名，流水线代码不变。

```python
from sklearn.linear_model import LogisticRegression
from sklearn.ensemble import RandomForestClassifier, GradientBoostingClassifier
from sklearn.svm import SVC

# 所有模型是可互换的策略
strategies = {
    'logistic':  LogisticRegression(),
    'forest':    RandomForestClassifier(n_estimators=100),
    'gradient':  GradientBoostingClassifier(),
    'svm':       SVC(kernel='rbf'),
}

# 训练和评估代码完全不变
for name, model in strategies.items():
    model.fit(X_train, y_train)          # 统一接口
    score = model.score(X_test, y_test)  # 统一接口
    print(f"{name}: {score:.3f}")
```

---

## 4. TypeScript — 支付策略

电商系统经典场景：多种支付方式，订单处理流程不变。

```typescript
interface PaymentStrategy {
    charge(amount: number, currency: string): Promise<PaymentResult>;
    refund(transactionId: string, amount: number): Promise<void>;
}

class StripeStrategy implements PaymentStrategy {
    async charge(amount, currency) {
        return stripe.paymentIntents.create({ amount, currency });
    }
    async refund(txId, amount) { ... }
}

class PayPalStrategy implements PaymentStrategy {
    async charge(amount, currency) { ... }
    async refund(txId, amount) { ... }
}

// OrderService 只依赖接口，不依赖具体实现
class OrderService {
    constructor(private payment: PaymentStrategy) {}

    async checkout(order: Order) {
        // 无论用哪种支付方式，这里的代码完全一样
        const result = await this.payment.charge(order.total, order.currency);
        await this.saveTransaction(result);
    }
}

// 运行时注入策略（也可以根据用户选择动态切换）
const service = new OrderService(new StripeStrategy());
```

---

## 5. Go — `http.RoundTripper`

Go 的 HTTP 客户端用 `RoundTripper` 接口表达"如何发送请求"的策略，
可以替换为带缓存、带重试、带日志的实现。

```go
// 默认策略：直接发 HTTP 请求
client := &http.Client{}

// 自定义策略：添加重试逻辑
type RetryTransport struct {
    Base    http.RoundTripper
    Retries int
}

func (t *RetryTransport) RoundTrip(req *http.Request) (*http.Response, error) {
    for i := 0; i < t.Retries; i++ {
        resp, err := t.Base.RoundTrip(req)
        if err == nil && resp.StatusCode < 500 {
            return resp, nil
        }
        time.Sleep(time.Duration(i+1) * time.Second)
    }
    return t.Base.RoundTrip(req)
}

// 注入策略
client := &http.Client{
    Transport: &RetryTransport{Base: http.DefaultTransport, Retries: 3},
}
```

---

## Python 生态

Python 的函数是一等公民，Strategy 模式最自然的实现就是**传入一个函数**，而不是创建对象。

```python
from typing import Callable, Protocol
from functools import singledispatch

# 1. 函数作为 Strategy（最 Pythonic 的方式）
def sort_by_price(items: list[dict]) -> list[dict]:
    return sorted(items, key=lambda x: x["price"])

def sort_by_name(items: list[dict]) -> list[dict]:
    return sorted(items, key=lambda x: x["name"])

def sort_by_rating(items: list[dict]) -> list[dict]:
    return sorted(items, key=lambda x: -x["rating"])

class ProductCatalog:
    def __init__(self):
        self._products = []

    def add(self, product: dict) -> None:
        self._products.append(product)

    def list(self, strategy: Callable[[list], list] = sort_by_price) -> list:
        return strategy(self._products)   # Strategy 就是一个函数

catalog = ProductCatalog()
catalog.add({"name": "Laptop", "price": 999, "rating": 4.5})
catalog.add({"name": "Mouse",  "price": 29,  "rating": 4.8})

print(catalog.list(sort_by_price))     # 按价格排序
print(catalog.list(sort_by_rating))    # 按评分排序
print(catalog.list(lambda p: sorted(p, key=lambda x: x["price"], reverse=True)))  # 匿名策略

# 2. Protocol 定义 Strategy 接口（需要类型检查时）
class CompressionStrategy(Protocol):
    def compress(self, data: bytes) -> bytes: ...
    def decompress(self, data: bytes) -> bytes: ...

class ZlibStrategy:
    def compress(self, data: bytes) -> bytes:
        import zlib
        return zlib.compress(data)

    def decompress(self, data: bytes) -> bytes:
        import zlib
        return zlib.decompress(data)

class LZ4Strategy:
    def compress(self, data: bytes) -> bytes:
        import lz4.frame   # pip install lz4
        return lz4.frame.compress(data)

    def decompress(self, data: bytes) -> bytes:
        import lz4.frame
        return lz4.frame.decompress(data)

class Storage:
    def __init__(self, strategy: CompressionStrategy):
        self._strategy = strategy

    def write(self, path: str, data: bytes) -> None:
        compressed = self._strategy.compress(data)
        with open(path, "wb") as f:
            f.write(compressed)

# 3. functools.singledispatch — 基于类型的 Strategy 分发
@singledispatch
def serialize(value) -> str:
    raise NotImplementedError(f"Cannot serialize {type(value)}")

@serialize.register(int)
@serialize.register(float)
def _(value) -> str:
    return str(value)

@serialize.register(list)
def _(value) -> str:
    return "[" + ", ".join(serialize(v) for v in value) + "]"

@serialize.register(dict)
def _(value) -> str:
    pairs = ", ".join(f"{serialize(k)}: {serialize(v)}" for k, v in value.items())
    return "{" + pairs + "}"

print(serialize(42))                    # 42
print(serialize([1, "a", [2, 3]]))      # [1, a, [2, 3]]

# 4. scikit-learn — 算法即 Strategy
# from sklearn.linear_model import LogisticRegression, Ridge
# from sklearn.ensemble import RandomForestClassifier
#
# def train(X, y, strategy):            # strategy 是任何实现了 fit/predict 的类
#     strategy.fit(X, y)
#     return strategy
#
# model = train(X_train, y_train, RandomForestClassifier(n_estimators=100))
# model = train(X_train, y_train, LogisticRegression())  # 切换算法，不改其他代码
```

> **Python 洞察**：Python 中 Strategy 模式最常见的实现就是**传函数**。
> `sorted(items, key=func)` 里的 `key` 就是一个 Strategy。
> `singledispatch` 是 Visitor 和 Strategy 的混合体：根据参数类型自动选择策略。

---

## 关键洞察

> Strategy 是消灭 `if-else` / `switch` 的最强武器。
> 当你发现代码里有"根据类型/配置选择不同算法"的分支，
> 并且这个分支会随着业务增长越来越长——这就是引入 Strategy 的信号。
> 现代语言里 lambda/闭包 是轻量级 Strategy，不必每次都定义一个类。
