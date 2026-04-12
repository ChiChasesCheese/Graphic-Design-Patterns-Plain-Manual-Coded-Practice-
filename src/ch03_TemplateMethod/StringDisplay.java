package ch03_TemplateMethod;

public final class StringDisplay extends AbstractDisplay {

    private final String s;
    private final String line;

    public StringDisplay(String s) {
        this.s = s;
        // String.repeat()（Java 11+）替代手写循环拼接
        // 原版硬编码 9 个 dash，与字符串长度无关
        // 这里动态生成，长度与字符串内容匹配
        this.line = "-".repeat(s.length() + 2);  // +2 是两侧的 | 边框
    }

    @Override
    public void open() {
        printLine();
    }

    @Override
    public void print() {
        // String.formatted()（Java 15+）替代 String.format()
        // 效果相同，链式调用更简洁
        System.out.println("|%s|".formatted(s));
    }

    @Override
    public void close() {
        printLine();
    }

    // open() 和 close() 行为完全相同，提取私有方法避免重复
    private void printLine() {
        System.out.println(line);
    }
}
