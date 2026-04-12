package ch07_Builder;

public class TextBuilder implements Builder {
    // StringBuilder 是 StringBuffer 的非线程安全版本，单线程下首选，性能更好
    private final StringBuilder sb = new StringBuilder();

    @Override
    public void makeTitle(String title) {
        sb.append("========\n");
        sb.append(title).append('\n');
    }

    @Override
    public void makeString(String str) {
        sb.append("------\n");
        sb.append(str).append('\n');
    }

    @Override
    public void makeItems(String[] items) {
        // stream + forEach 在这里没有转换逻辑，增强 for 更直接
        for (var item : items) {
            sb.append(" ").append(item).append('\n');
        }
    }

    @Override
    public void close() {
        sb.append("*********\n");
    }

    public String getResult() {
        return sb.toString();
    }
}