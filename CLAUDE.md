# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

本项目是《图解设计模式》（结城浩）的学习仓库，用 Java 实现全部 23 种 GoF 设计模式，配有多语言实际应用说明文档。

## 环境

- **Java**: 26（本地运行版本），IntelliJ 配置为 `JDK_X`（最新 Preview 特性）
- **构建**: 无构建工具（Maven/Gradle），直接用 IntelliJ IDEA 编译运行
- **源码根目录**: `src/`（IntelliJ module source root）

## 编译与运行

```bash
# 编译单个章节（在项目根目录执行）
javac --enable-preview --release 26 -d out src/ch05_Singleton/*.java

# 运行
java --enable-preview -cp out ch05_Singleton.Main

# 有子包的章节（如 ch04, ch08）
javac --enable-preview --release 26 -d out src/ch04_FactoryMethod/framework/*.java src/ch04_FactoryMethod/idcard/*.java src/ch04_FactoryMethod/Main.java
java --enable-preview -cp out ch04_FactoryMethod.Main
```

实际开发中直接在 IntelliJ 里点击运行即可，Preview 已在项目设置中全局开启。

## 目录结构

```
src/
  ch01_Iterator/        ← 每章一个 package，名称 = chNN_PatternName
  ch04_FactoryMethod/
    framework/          ← 部分章节有子包（抽象层）
    idcard/             ← 子包（具体实现层）
  ...
  ch23_Interpreter/
reference/
  official/            ← git submodule，教科书官方源码（只读参考）
```

## 代码约定

**Skeleton 文件**：新建骨架文件时只写一行 `package PackageName;`，不添加任何类声明、方法 stub 或注释。用户自己实现所有代码。

**Java 特性**：鼓励使用 Java 21+ 新特性：
- `record` 替代简单 POJO
- `sealed` 类约束子类范围
- `var` 局部类型推断
- `switch` 模式匹配（Java 21+）
- `_` 未命名变量需 Java 22+，当前环境 Java 23 可用
- `import static java.lang.IO.println` 用于 Main 类输出（Java 26 正式移入 `java.lang`）

**包命名**：package 名与目录名一致，如 `package ch05_Singleton;`。子包如 `package ch04_FactoryMethod.framework;`。

## 文档约定

每个章节目录下有 `usage.md`，结构固定：
- 5 个真实生产/开源框架应用示例（Java、TypeScript、Python 等多语言）
- 最后一节 `## Python 生态`，专注 Python 语法特性、标准库、第三方库
- 末尾 `## 关键洞察` 总结该模式的核心价值

部分章节有额外 `.md` 文档（如 `LSP.md`、`AbstractClass.md`、`AccessModifiers.md`），放在对应章节目录下，不合并。

## 参考资料

`reference/official/` 是教科书官方代码的 git submodule，章节目录**没有** `ch` 前缀（如 `Iterator/` 而非 `ch01_Iterator/`）。查阅官方实现时在此处对照。
