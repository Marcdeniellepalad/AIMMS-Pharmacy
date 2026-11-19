import javax.swing.SwingUtilities;

import service.DemoDataSeeder;
import ui.PharmacyAppFrame;

public final class Main {

    private Main() { }

    public static void main(String[] args) {
        boolean demoRequested = java.util.Arrays.asList(args).contains("--demo");
        boolean demoDataReady = demoRequested && DemoDataSeeder.seedIfEmpty();
        SwingUtilities.invokeLater(() -> {
            PharmacyAppFrame app = new PharmacyAppFrame(demoDataReady);
            app.setVisible(true);
        });
    }
}
