package ch05_Singleton;

public class Singleton {
    private static final Singleton singleton = new Singleton();  // final：不可重新赋值

    private Singleton() {
        // 防止反射攻击：如果已有实例，拒绝再次创建
        if (singleton != null) {
            throw new IllegalStateException("Use Singleton.getInstance()");
        }
        System.out.println("Instance created");
    }

    public static Singleton getInstance() {
        return singleton;
    }

    // 防止序列化破坏单例：反序列化时返回已有实例而非新建
    protected Object readResolve() {
        return singleton;
    }
}