package ch09_Bridge;

public class StringDisplayImpl extends DisplayImpl {

    private final String input;
    private final int width;

    public StringDisplayImpl(String input) {
        this.input = input;
        this.width = input.length();
    }

    @Override
    void rawOpen() {
        System.out.print("+" + "-".repeat(width) + "+\n");
    }

    @Override
    void rawPrint() {
        System.out.print("+" + input + "+\n");
    }

    @Override
    void rawClose() {
        System.out.print("+" + "~".repeat(width) + "+\n");
    }
}