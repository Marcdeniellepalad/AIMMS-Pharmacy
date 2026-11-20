import javax.swing.SwingUtilities;
import javax.swing.JOptionPane;

import repository.inventory.InventoryDataInitializer;
import repository.csv.CsvWriteCoordinator;
import service.DemoDataSeeder;
import ui.PharmacyAppFrame;

public final class Main {

    private Main() { }

    public static void main(String[] args) {
        boolean demoRequested = java.util.Arrays.asList(args).contains("--demo");
        boolean demoDataReady;
        try {
            new CsvWriteCoordinator().recoverPendingOperations();
            if (demoRequested) {
                demoDataReady = DemoDataSeeder.seedIfEmpty();
            } else {
                new InventoryDataInitializer().initializeIfMissing();
                demoDataReady = false;
            }
        } catch (java.io.IOException | RuntimeException ex) {
            ex.printStackTrace();
            JOptionPane.showMessageDialog(null,
                    "Could not initialize pharmacy data: " + ex.getMessage(),
                    "Startup error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        SwingUtilities.invokeLater(() -> {
            PharmacyAppFrame app = new PharmacyAppFrame(demoDataReady);
            app.setVisible(true);
        });
    }
}
