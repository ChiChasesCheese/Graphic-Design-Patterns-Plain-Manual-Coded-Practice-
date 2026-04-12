# Chain of Responsibility 模式 — 真实应用

核心：**请求沿着处理链传递，每个处理者决定自己处理还是传给下一个，发送方不知道由谁处理。**

---

## 1. Express.js / Koa — 中间件链

Node.js 最典型的应用，每个中间件是链上的一个节点，`next()` 传递给下一个。

```javascript
// Express 中间件链
app.use((req, res, next) => {
    console.log(`${req.method} ${req.path}`);
    next();  // 传给下一个中间件
});

app.use((req, res, next) => {
    const token = req.headers.authorization;
    if (!token) return res.status(401).json({ error: 'Unauthorized' });
    req.user = verifyToken(token);
    next();  // 认证通过，传给下一个
});

app.use((req, res, next) => {
    if (!req.user.hasPermission(req.path)) {
        return res.status(403).json({ error: 'Forbidden' });
    }
    next();
});

app.get('/api/data', (req, res) => {
    res.json(getData());  // 只有通过所有中间件才到这里
});
```

---

## 2. Spring Security — 过滤器链

Spring Security 的核心架构是一条 `SecurityFilterChain`，
每个 Filter 处理一种安全关注点。

```java
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 每个 .xxx() 是链上的一个节点
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session ->
                session.sessionCreationPolicy(STATELESS))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/public/**").permitAll()
                .requestMatchers("/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated()
            );
        return http.build();
    }
}

// 请求进来：CorsFilter → CsrfFilter → JwtAuthFilter → AuthorizationFilter → ...
// 每个 Filter 决定放行（chain.doFilter）还是拦截（直接返回）
```

---

## 3. Python — Django 中间件

Django 的请求/响应处理是标准的 Chain of Responsibility。

```python
# settings.py
MIDDLEWARE = [
    'django.middleware.security.SecurityMiddleware',     # 节点1
    'django.contrib.sessions.middleware.SessionMiddleware', # 节点2
    'django.middleware.csrf.CsrfViewMiddleware',        # 节点3
    'django.contrib.auth.middleware.AuthenticationMiddleware', # 节点4
    'myapp.middleware.RateLimitMiddleware',              # 自定义节点
]

# 自定义中间件
class RateLimitMiddleware:
    def __init__(self, get_response):
        self.get_response = get_response  # 下一个处理者

    def __call__(self, request):
        if self.is_rate_limited(request):
            return HttpResponse("Too Many Requests", status=429)
        return self.get_response(request)  # 传给下一个
```

---

## 4. Java — Servlet Filter Chain

Java EE / Jakarta EE 的 Servlet Filter 是 CoR 的标准实现，
Tomcat、Jetty、Spring Boot 都基于此。

```java
@Component
public class RequestLoggingFilter implements Filter {

    @Override
    public void doFilter(ServletRequest request,
                         ServletResponse response,
                         FilterChain chain)  // ← 链的引用
            throws IOException, ServletException {

        long start = System.currentTimeMillis();
        HttpServletRequest req = (HttpServletRequest) request;

        try {
            chain.doFilter(request, response);  // 传给链中下一个 Filter
        } finally {
            long elapsed = System.currentTimeMillis() - start;
            log.info("{} {} {}ms", req.getMethod(), req.getRequestURI(), elapsed);
        }
    }
}
```

---

## 5. Go — 中间件链（函数式）

Go 的 HTTP 中间件用函数包装表达 CoR，与 Decorator 模式高度重叠。

```go
type Middleware func(http.Handler) http.Handler

// 限流中间件
func RateLimit(limit int) Middleware {
    limiter := rate.NewLimiter(rate.Limit(limit), limit)
    return func(next http.Handler) http.Handler {
        return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
            if !limiter.Allow() {
                http.Error(w, "Too Many Requests", http.StatusTooManyRequests)
                return  // 不调用 next = 在此处理，不传递
            }
            next.ServeHTTP(w, r)  // 传给下一个
        })
    }
}

// 构建处理链
handler := Chain(
    myHandler,
    Logger(),
    Auth(),
    RateLimit(100),
)
```

---

## CoR vs Decorator 的区别

| | Chain of Responsibility | Decorator |
|--|------------------------|-----------|
| 目的 | 找到合适的**处理者** | **叠加功能** |
| 传递 | 可以在链中某处**停止** | 每层都会执行 |
| 典型场景 | 权限检查、过滤器 | 缓存、日志、压缩 |

实际工程里两者经常混用（Express 中间件两种都是）。

---

## Python 生态

Python 用可调用对象（`__call__`）和生成器构建责任链，WSGI/ASGI 中间件是最典型的生产实践。

```python
from typing import Callable, Any

# 1. 函数链式 CoR（最简洁的 Python 实现）
Handler = Callable[[dict, Callable], dict | None]

def auth_handler(request: dict, next_handler: Callable) -> dict | None:
    token = request.get("headers", {}).get("Authorization")
    if not token:
        return {"status": 401, "body": "Unauthorized"}
    request["user"] = {"id": 1, "role": "admin"}   # 解码 token
    return next_handler(request)                    # 传递给下一个处理者

def rate_limit_handler(request: dict, next_handler: Callable) -> dict | None:
    ip = request.get("ip", "unknown")
    # 假设检查通过
    return next_handler(request)

def business_handler(request: dict, next_handler: Callable) -> dict | None:
    return {"status": 200, "body": f"Hello, {request['user']['id']}"}

# 构建链
def build_chain(*handlers: Handler) -> Callable:
    def chain(request: dict) -> dict | None:
        def make_next(remaining):
            if not remaining:
                return lambda req: None
            handler = remaining[0]
            return lambda req: handler(req, make_next(remaining[1:]))
        return make_next(list(handlers))(request)
    return chain

handle = build_chain(auth_handler, rate_limit_handler, business_handler)
print(handle({"headers": {"Authorization": "Bearer token"}, "ip": "1.2.3.4"}))
# {'status': 200, 'body': 'Hello, 1'}

# 2. WSGI 中间件（Python Web 框架的标准 CoR）
class LoggingMiddleware:
    """WSGI 中间件：包装 app，形成责任链"""
    def __init__(self, app):
        self.app = app

    def __call__(self, environ: dict, start_response: Callable) -> Any:
        method = environ.get("REQUEST_METHOD", "GET")
        path   = environ.get("PATH_INFO", "/")
        print(f"[LOG] {method} {path}")
        return self.app(environ, start_response)   # 传给下一个处理者

class AuthMiddleware:
    def __init__(self, app):
        self.app = app

    def __call__(self, environ: dict, start_response: Callable) -> Any:
        token = environ.get("HTTP_AUTHORIZATION", "")
        if not token.startswith("Bearer "):
            start_response("401 Unauthorized", [("Content-Type", "text/plain")])
            return [b"Unauthorized"]
        return self.app(environ, start_response)

def application(environ: dict, start_response: Callable) -> Any:
    start_response("200 OK", [("Content-Type", "text/plain")])
    return [b"Hello, World!"]

# 链式包装（洋葱模型）
app = LoggingMiddleware(AuthMiddleware(application))

# 3. 类型检查链 — 用 __call__ 协议
class Validator:
    def __init__(self, check: Callable[[Any], bool], message: str,
                 successor: "Validator | None" = None):
        self._check = check
        self._message = message
        self._successor = successor

    def validate(self, value: Any) -> list[str]:
        errors = []
        if not self._check(value):
            errors.append(self._message)
        if self._successor:
            errors.extend(self._successor.validate(value))
        return errors   # 收集所有错误（不短路）

not_empty = Validator(lambda v: bool(v), "Cannot be empty")
min_len   = Validator(lambda v: len(v) >= 8, "Must be at least 8 characters", not_empty)
has_upper = Validator(lambda v: any(c.isupper() for c in v), "Must contain uppercase", min_len)

print(has_upper.validate("abc"))     # ['Must contain uppercase', 'Must be at least 8 characters']
print(has_upper.validate("AbcDefGh")) # []（通过）
```

> **Python 洞察**：WSGI/ASGI 中间件是 Python Web 生态中最广泛使用的责任链。
> `Django` 的 `MIDDLEWARE` 列表、`FastAPI` 的 `add_middleware()`、
> `Starlette` 的中间件栈都是责任链——每层中间件决定是否调用 `call_next(request)`。

---

## 关键洞察

> "中间件"这个词在 Web 框架里无处不在，本质就是 Chain of Responsibility。
> 它解决的核心问题：**关注点分离**——认证、日志、限流、压缩各管各的，
> 互相不知道对方存在，通过链串在一起。
