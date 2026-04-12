package ch03_TemplateMethod;

public class Main {
    public static void main(String[] args) {
        AbstractDisplay chDis = new CharDisplay('c');
        AbstractDisplay sDis  = new StringDisplay("String");
        AbstractDisplay s2Dis = new StringDisplay("Another");

        chDis.display();
        sDis.display();
        s2Dis.display();

        // ── 多态的体现 ────────────────────────────────────────────────────
        // 声明类型是 AbstractDisplay，调用方不知道具体类型
        // display() 骨架相同，open/print/close 行为各异
        AbstractDisplay[] displays = { chDis, sDis, s2Dis };
        for (var d : displays) {
            d.display();
        }

        // ── sealed + switch pattern matching（Java 21+）───────────────────
        // sealed 的最大收益：switch 可以穷举所有子类
        // 漏掉任意一个 case 编译器直接报错，比 if-instanceof 链安全得多
        for (var d : displays) {
            String label = switch (d) {
                case CharDisplay   c -> "char display:   " + c.getClass().getSimpleName();
                case StringDisplay s -> "string display: " + s.getClass().getSimpleName();
            };
            System.out.println(label);
        }

        // ── 函数式替代方案（扩展阅读）────────────────────────────────────
        // Template Method 本质是"把变化的部分传进来"
        // Java 8+ 可以用 lambda 达到同样效果，不需要继承：
        //
        // static void display(Runnable open, Runnable print, Runnable close) {
        //     open.run();
        //     IntStream.range(0, 5).forEach(_ -> print.run());
        //     close.run();
        // }
        //
        // display(
        //     () -> System.out.print("|"),
        //     () -> System.out.print('c'),
        //     () -> { System.out.print("|"); System.out.println(); }
        // );
        //
        // 优点：不需要创建子类，灵活
        // 缺点：没有类型名，可读性和可测试性不如继承版
    }
}
