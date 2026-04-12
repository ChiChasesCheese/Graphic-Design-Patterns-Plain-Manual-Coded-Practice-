package ch03_TemplateMethod;

// sealed 的子类必须是 final、sealed 或 non-sealed 三者之一
// final：不允许再被继承（叶子节点）
public final class CharDisplay extends AbstractDisplay {

    private final char ch;

    public CharDisplay(char ch) {
        this.ch = ch;
    }

    // open/close 输出相同的边框字符，提取私有方法，改样式只改一处
    private void printBorder() {
        System.out.print("|");
    }

    @Override
    public void open() {
        printBorder();
    }

    @Override
    public void print() {
        System.out.print(ch);
    }

    @Override
    public void close() {
        printBorder();
        System.out.println();  // 换行
    }
}
