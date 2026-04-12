package ch02_Adapter;

public class PrintBanner implements Print {
    private Banner banner;
    public PrintBanner(Banner banner) {
        this.banner = banner;
    }

    @Override
    public void printWeak() {
        System.out.println(this.banner.showWithParen());
    }

    @Override
    public void printStrong() {
        System.out.println(this.banner.showWithAster());
    }
}