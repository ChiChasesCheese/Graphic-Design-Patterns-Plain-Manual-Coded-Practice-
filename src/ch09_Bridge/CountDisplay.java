package ch09_Bridge;

public class CountDisplay extends Display {


    public CountDisplay(DisplayImpl displayImpl) {
        super(displayImpl);
    }

    public void multiDisplay(int multi) {
        for (int i = 0; i < multi; i++) {
            print();
        }
    }
}