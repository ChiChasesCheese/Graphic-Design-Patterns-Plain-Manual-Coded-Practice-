package ch02_Adapter;

public class Banner {
    private String input;
    public Banner(String input) {
        this.input = input;
    }
    public String showWithParen() {
        return "\"" + this.input + "\"";
    }

    public String showWithAster() {
        return "*" + this.input + "*";
    }
}