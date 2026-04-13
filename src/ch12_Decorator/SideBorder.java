package ch12_Decorator;

public class SideBorder extends Border {

    private final char side;

    public SideBorder(Display display, char side) {
        super(display);
        this.side = side;
    }

    @Override
    int getRows() {
        return this.display.getRows();
    }

    @Override
    String getRowText(int i) {
        return side + this.display.getRowText(i) + side;
    }

    @Override
    int getColumns() {
        return 2 + this.display.getColumns();
    }
}