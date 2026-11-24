package com.studyagent.ui;

import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.io.File;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;

/** Imports Explorer file drops through the same workflow as the file chooser. */
final class FileDropHandler extends TransferHandler {
    private final JButton action;
    private final Set<String> extensions;
    private final int maxFiles;
    private final Consumer<File[]> importer;

    FileDropHandler(JButton action, Set<String> extensions, int maxFiles, Consumer<File[]> importer) {
        this.action=action; this.extensions=extensions; this.maxFiles=maxFiles; this.importer=importer;
    }

    static void install(JComponent component, FileDropHandler handler) {
        component.setTransferHandler(handler);
        for(Component child:component.getComponents())
            if(child instanceof JComponent jc) install(jc,handler);
    }

    @Override public boolean canImport(TransferSupport support) {
        return action.isEnabled() && support.isDataFlavorSupported(DataFlavor.javaFileListFlavor);
    }

    String validationError(List<?> files) {
        if(files.isEmpty()) return "Hãy thả ít nhất một file.";
        if(files.size()>maxFiles) return "Hãy thả từng giáo trình để chọn môn học cho mỗi tài liệu.";
        for(Object item:files) {
            if(!(item instanceof File file) || !file.isFile()) return "Chỉ nhận file tài liệu, không nhận thư mục.";
            String name=file.getName().toLowerCase(Locale.ROOT);
            int dot=name.lastIndexOf('.');
            if(dot<0 || !extensions.contains(name.substring(dot+1)))
                return "Định dạng không hỗ trợ: "+file.getName()+".\nĐịnh dạng được nhận: "+String.join(", ",new java.util.TreeSet<>(extensions))+".";
        }
        return null;
    }

    @Override public boolean importData(TransferSupport support) {
        if(!canImport(support)) return false;
        try {
            List<?> files=(List<?>)support.getTransferable().getTransferData(DataFlavor.javaFileListFlavor);
            String error=validationError(files);
            if(error!=null) {
                SwingUtilities.invokeLater(()->JOptionPane.showMessageDialog(action,error,"Chưa thể nhập file",JOptionPane.WARNING_MESSAGE));
                return false;
            }
            File[] selected=files.toArray(File[]::new);
            // Finish the native drop before opening a subject dialog or starting a worker.
            SwingUtilities.invokeLater(()->{if(action.isEnabled()) importer.accept(selected);});
            return true;
        } catch(Exception ex) {
            SwingUtilities.invokeLater(()->JOptionPane.showMessageDialog(action,"Không đọc được file kéo thả. Hãy thử nút chọn file.","Chưa thể nhập file",JOptionPane.WARNING_MESSAGE));
            return false;
        }
    }
}
