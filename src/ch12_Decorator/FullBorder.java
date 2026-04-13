package ch12_Decorator;

public class FullBorder extends Border {

    protected FullBorder(Display display) {
        super(display);
    }

    @Override
    int getRows() {
        return 2 + display.getRows();
    }

    @Override
    String getRowText(int i) {
        if (i == 0 || i == display.getRows() + 1) {
            return "|" + "-".repeat(getColumns()) + "|";
        }
        return "|" + display.getRowText(i - 1) + "|";
    }

    @Override
    int getColumns() {
        return 2 + display.getColumns();
    }
}