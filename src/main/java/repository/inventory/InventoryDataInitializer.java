package repository.inventory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Seeds local runtime inventory from tracked synthetic demo CSVs when files are absent. */
public final class InventoryDataInitializer {

    private static final Path SAMPLE_ITEMS = Path.of("data/sample/inventory/itemsDB.csv");
    private static final Path SAMPLE_BATCHES = Path.of("data/sample/inventory/batchDB.csv");
    private static final Path RUNTIME_ITEMS = Path.of("data/runtime/inventory/itemsDB.csv");
    private static final Path RUNTIME_BATCHES = Path.of("data/runtime/inventory/batchDB.csv");

    public void initializeIfMissing() throws IOException {
        copyIfMissing(SAMPLE_ITEMS, RUNTIME_ITEMS);
        copyIfMissing(SAMPLE_BATCHES, RUNTIME_BATCHES);
    }

    private static void copyIfMissing(Path source, Path destination) throws IOException {
        if (Files.exists(destination)) {
            return;
        }
        Files.createDirectories(destination.getParent());
        try {
            Files.copy(source, destination, StandardCopyOption.COPY_ATTRIBUTES);
        } catch (java.nio.file.FileAlreadyExistsException ignored) {
            // A second application launch may have initialized this file concurrently.
        }
    }
}
