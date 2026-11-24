package com.studyagent.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import javax.swing.*;
import java.awt.datatransfer.*;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class FileDropHandlerTest {
    @TempDir Path temp;

    @Test void validatesBeforeImportingAnyFile() throws Exception {
        var handler=new FileDropHandler(new JButton(),Set.of("pdf","docx"),1,files->fail());
        File pdf=Files.createFile(temp.resolve("Giáo trình.PDF")).toFile();
        assertNull(handler.validationError(List.of(pdf)));
        assertNotNull(handler.validationError(List.of(pdf,pdf)));
        assertNotNull(handler.validationError(List.of(temp.toFile())));
        assertNotNull(handler.validationError(List.of(Files.createFile(temp.resolve("test.exe")).toFile())));
    }

    @Test void fileDropReachesImporterAndBusyBlocksIt() throws Exception {
        File pdf=Files.createFile(temp.resolve("TKB.pdf")).toFile();
        AtomicReference<File[]> imported=new AtomicReference<>();
        JButton action=new JButton();
        var handler=new FileDropHandler(action,Set.of("pdf"),10,imported::set);
        Transferable payload=new Transferable(){
            public DataFlavor[] getTransferDataFlavors(){return new DataFlavor[]{DataFlavor.javaFileListFlavor};}
            public boolean isDataFlavorSupported(DataFlavor flavor){return flavor.equals(DataFlavor.javaFileListFlavor);}
            public Object getTransferData(DataFlavor flavor){return List.of(pdf);}
        };
        var support=new TransferHandler.TransferSupport(action,payload);
        SwingUtilities.invokeAndWait(()->assertTrue(handler.importData(support)));
        SwingUtilities.invokeAndWait(()->{});
        assertArrayEquals(new File[]{pdf},imported.get());
        action.setEnabled(false);
        assertFalse(handler.canImport(support));
        assertFalse(handler.importData(support));
    }
}
