package repository.csv;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Serializes local multi-file CSV updates and rolls back interrupted operations on startup. */
public final class CsvWriteCoordinator {
    private static final Path RUNTIME = Path.of("data/runtime").toAbsolutePath().normalize();
    private static final Path JOURNALS = RUNTIME.resolve(".operations");
    private static final Path LOCK_FILE = RUNTIME.resolve(".writer.lock");
    private final CsvFileManager csv = new CsvFileManager();

    public record Update(Path target, List<String> headers, List<Map<String, String>> rows) { }

    public void recoverPendingOperations() {
        withLock(() -> {
            if (Files.notExists(JOURNALS)) return;
            try (var directories = Files.list(JOURNALS)) {
                for (Path directory : directories.filter(Files::isDirectory).toList()) recover(directory);
            } catch (IOException ex) {
                throw new IllegalStateException("Could not inspect CSV recovery records.", ex);
            }
        });
    }

    public void commit(List<Update> updates) {
        if (updates == null || updates.isEmpty()) throw new IllegalArgumentException("At least one CSV update is required.");
        withLock(() -> commitLocked(updates));
    }

    private void commitLocked(List<Update> updates) {
        Path operation = JOURNALS.resolve("op-" + System.currentTimeMillis() + "-" + java.util.UUID.randomUUID());
        List<Path> targets = new ArrayList<>();
        try {
            Files.createDirectories(operation);
            for (int i = 0; i < updates.size(); i++) {
                Update update = updates.get(i);
                Path target = update.target().toAbsolutePath().normalize();
                if (!target.startsWith(RUNTIME)) throw new IllegalArgumentException("CSV writes must stay under data/runtime.");
                Path staged = operation.resolve("new-" + i + ".csv");
                Path backup = operation.resolve("old-" + i + ".csv");
                csv.writeRecords(staged, update.headers(), update.rows());
                if (Files.exists(target)) Files.copy(target, backup, StandardCopyOption.REPLACE_EXISTING);
                targets.add(target);
            }
            Properties manifest = new Properties();
            manifest.setProperty("state", "PREPARED");
            manifest.setProperty("count", Integer.toString(updates.size()));
            for (int i = 0; i < updates.size(); i++) {
                manifest.setProperty("target." + i, targets.get(i).toString());
                manifest.setProperty("existed." + i, Boolean.toString(Files.exists(operation.resolve("old-" + i + ".csv"))));
            }
            saveManifest(operation, manifest);
            manifest.setProperty("state", "COMMITTING");
            saveManifest(operation, manifest);
            for (int i = 0; i < updates.size(); i++) {
                Path target = targets.get(i);
                Files.createDirectories(target.getParent());
                move(operation.resolve("new-" + i + ".csv"), target);
            }
            manifest.setProperty("state", "COMMITTED");
            saveManifest(operation, manifest);
            deleteTree(operation);
        } catch (Exception ex) {
            try { recover(operation); } catch (RuntimeException recovery) { ex.addSuppressed(recovery); }
            throw new IllegalStateException("CSV operation failed; recovery was attempted.", ex);
        }
    }

    private static void recover(Path operation) {
        Path file = operation.resolve("manifest.properties");
        if (Files.notExists(file)) { deleteTree(operation); return; }
        Properties manifest = new Properties();
        try (var input = Files.newInputStream(file)) { manifest.load(input); }
        catch (IOException ex) { throw new IllegalStateException("CSV recovery manifest cannot be read: " + operation, ex); }
        if ("COMMITTED".equals(manifest.getProperty("state"))) { deleteTree(operation); return; }
        int count = Integer.parseInt(manifest.getProperty("count", "0"));
        for (int i = count - 1; i >= 0; i--) {
            Path target = Path.of(manifest.getProperty("target." + i)).toAbsolutePath().normalize();
            if (!target.startsWith(RUNTIME)) throw new IllegalStateException("Unsafe target in CSV recovery manifest.");
            Path backup = operation.resolve("old-" + i + ".csv");
            try {
                if (Boolean.parseBoolean(manifest.getProperty("existed." + i)) && Files.exists(backup)) {
                    Files.createDirectories(target.getParent());
                    Files.copy(backup, target, StandardCopyOption.REPLACE_EXISTING);
                } else if (!Boolean.parseBoolean(manifest.getProperty("existed." + i))) Files.deleteIfExists(target);
            } catch (IOException ex) { throw new IllegalStateException("Could not restore CSV file " + target, ex); }
        }
        deleteTree(operation);
    }

    private static void saveManifest(Path operation, Properties properties) throws IOException {
        Path temporary = operation.resolve("manifest.tmp");
        try (var output = Files.newOutputStream(temporary, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            properties.store(output, "AIMMs CSV operation recovery");
        }
        move(temporary, operation.resolve("manifest.properties"));
    }

    private static synchronized void withLock(Runnable action) {
        try {
            Files.createDirectories(RUNTIME);
            try (FileChannel channel = FileChannel.open(LOCK_FILE, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
                 FileLock ignored = channel.lock()) { action.run(); }
        } catch (IOException ex) { throw new IllegalStateException("Could not acquire the local CSV writer lock.", ex); }
    }

    private static void move(Path from, Path to) throws IOException {
        try { Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (java.nio.file.AtomicMoveNotSupportedException ex) { Files.move(from, to, StandardCopyOption.REPLACE_EXISTING); }
    }

    private static void deleteTree(Path directory) {
        if (Files.notExists(directory)) return;
        try (var paths = Files.walk(directory)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try { Files.deleteIfExists(path); } catch (IOException ex) { throw new java.io.UncheckedIOException(ex); }
            });
        } catch (IOException | java.io.UncheckedIOException ex) {
            throw new IllegalStateException("Could not clean CSV recovery files: " + directory, ex);
        }
    }
}
