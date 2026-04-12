package ch02_Adapter;

public class Main {
    public static void main(String[] args) {
        var banner = new Banner("Hello");
        var printBanner = new PrintBanner(banner);

        printBanner.printStrong();
        printBanner.printWeak();
    }
}