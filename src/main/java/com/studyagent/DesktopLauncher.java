package com.studyagent;

import com.studyagent.ai.GeminiService;
import com.studyagent.repository.*;
import com.studyagent.scheduler.*;
import com.studyagent.ui.*;
import javax.swing.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Arrays;

/** Native GUI entry point: no console scanner, and background services stop with the window. */
public final class DesktopLauncher {
    private DesktopLauncher(){}
    public static void main(String[] args)throws Exception{
        Path logs=AppPaths.resolve("data","logs");Files.createDirectories(logs);
        PrintStream log=new PrintStream(Files.newOutputStream(logs.resolve("desktop.log"),StandardOpenOption.CREATE,StandardOpenOption.APPEND),true,StandardCharsets.UTF_8);
        System.setOut(log);System.setErr(log);
        Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
            error.printStackTrace();SwingUtilities.invokeLater(()->JOptionPane.showMessageDialog(null,"Không thể hoàn tất: "+error.getMessage()+"\nNhật ký: "+logs.resolve("desktop.log"),"Study Agent",JOptionPane.ERROR_MESSAGE));
        });
        if(Arrays.asList(args).contains("--smoke-test")){try{smokeTest();}catch(Throwable error){error.printStackTrace();System.exit(1);}return;}
        DatabaseManager.initializeDatabase();
        var repository=new StudyRepository();var ai=new GeminiService(ConfigLoader.getGeminiApiKey());var optimizer=new StudyOptimizer(ai);
        SwingUtilities.invokeLater(()->{
            var window=new StudyDesktopUI(ai,repository,optimizer);
            window.showWindow();
        });
    }
    private static void smokeTest()throws Exception{
        DatabaseManager.initializeDatabase();
        try(var c=DatabaseManager.getConnection();var s=c.createStatement();var r=s.executeQuery("SELECT 1")){if(!r.next()||r.getInt(1)!=1)throw new IllegalStateException("SQLite unavailable");}
        try(var document=new org.apache.poi.xwpf.usermodel.XWPFDocument();var buffer=new ByteArrayOutputStream()){
            document.createParagraph().createRun().setText("Native Word reader check");document.write(buffer);
            if(!com.studyagent.parser.DocumentParser.extractTextFromDocx(buffer.toByteArray()).contains("Native Word reader check"))throw new IllegalStateException("Word reader unavailable");
        }
        try(var document=new org.apache.pdfbox.pdmodel.PDDocument();var buffer=new ByteArrayOutputStream()){
            var page=new org.apache.pdfbox.pdmodel.PDPage();document.addPage(page);
            try(var stream=new org.apache.pdfbox.pdmodel.PDPageContentStream(document,page)){
                stream.beginText();stream.setFont(new org.apache.pdfbox.pdmodel.font.PDType1Font(org.apache.pdfbox.pdmodel.font.Standard14Fonts.FontName.HELVETICA),12);stream.newLineAtOffset(30,700);stream.showText("Native PDF reader check");stream.endText();
            }
            document.save(buffer);if(!com.studyagent.parser.DocumentParser.extractTextFromPdf(buffer.toByteArray()).contains("Native PDF reader check"))throw new IllegalStateException("PDF reader unavailable");
        }
        SwingUtilities.invokeAndWait(()->{
            var ai=new GeminiService("");var frame=new StudyDesktopUI(ai,new StudyRepository(),new StudyOptimizer(ai));
            try{
                var root=frame.getContentPane();root.setSize(1440,900);layout(root);layout(root);
                var picture=new BufferedImage(1440,900,BufferedImage.TYPE_INT_RGB);var graphics=picture.createGraphics();root.printAll(graphics);graphics.dispose();
                if(!javax.imageio.ImageIO.write(picture,"png",AppPaths.resolve("data","logs","native-smoke.png").toFile()))throw new IllegalStateException("Image encoder unavailable");
            }catch(IOException e){throw new UncheckedIOException(e);}finally{frame.dispose();}
        });
        Files.writeString(AppPaths.resolve("data","logs","native-smoke.ok"),"SQLite, desktop UI, PDF, Word and image rendering OK\nJava "+System.getProperty("java.version")+"\nHome "+AppPaths.root(),StandardCharsets.UTF_8);
    }
    private static void layout(java.awt.Container c){c.doLayout();for(var child:c.getComponents())if(child instanceof java.awt.Container p)layout(p);}
}
