package ch07_Builder;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;

public class HTMLBuilder implements Builder {
    private String filename;
    private PrintWriter pw;

    @Override
    public void makeTitle(String title) {
        filename = title + ".html";
        try {
            pw = new PrintWriter(new FileWriter(filename));
        } catch (IOException e) {
            // 在 Builder 步骤方法里无法用 try-with-resources：
            // pw 需要跨方法存活，所以只能手动管理，close() 必须被调用
            throw new RuntimeException(e);
        }
        pw.println("<html><head><title>" + title + "</title></head><body>");
        pw.println("<h1>" + title + "</h1>");
    }

    @Override
    public void makeString(String str) {
        pw.println("<p>" + str + "</p>");
    }

    @Override
    public void makeItems(String[] items) {
        pw.println("<ul>");
        for (var item : items) {
            pw.println("<li>" + item + "</li>");
        }
        pw.println("</ul>");
    }

    @Override
    public void close() {
        pw.println("</body></html>");
        pw.close();  // flush + 关闭文件，必须调用，否则内容不会写入磁盘
    }

    public String getResult() {
        return filename;
    }
}