package ch12_Decorator;

public class StringDisplay extends Display {

    private final String input;

    public StringDisplay(String input) {
        this.input = input;
    }
    @Override
    int getRows() {
        return 1;
    }

    @Override
    String getRowText(int i) {
        return i == 0 ? input : null;
    }

    @Override
    int getColumns() {
        return input.getBytes().length;
    }
}