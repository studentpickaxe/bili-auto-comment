package yfrp.autobili.vid;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;

public class VidPool {

    private final Set<String> vidPool = new HashSet<>();
    private final String filename;

    public VidPool(String filename) {

        this.filename = filename;

        var path = Path.of(filename);

        if (Files.notExists(path)) {
            try {
                Files.createFile(path);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public boolean removeIf(Predicate<String> filter) {
        return vidPool.removeIf(filter);
    }

    @Nullable
    public String getVidFromPool() {
        if (vidPool.isEmpty()) {
            return null;
        }

        return vidPool.stream()
                .skip(new Random().nextInt(vidPool.size()))
                .findFirst()
                .orElse(null);
    }

    public int size() {
        return vidPool.size();
    }

    public void add(String... bvids) {

        vidPool.addAll(Arrays.asList(bvids));
    }

    public void addAll(Collection<String> bvids) {

        vidPool.addAll(bvids);
    }

    public void remove(String... bvids) {

        for (var bv : bvids) {
            vidPool.removeIf(line -> line.equals(bv) || line.contains(bv));
        }
    }

    public boolean hasVid(String bvid) {
        return vidPool.stream()
                .anyMatch(line -> line.equals(bvid) || line.contains(bvid));
    }

    public void saveVideos() {

        try (FileWriter fileWriter = new FileWriter(filename);
             BufferedWriter bufferedWriter = new BufferedWriter(fileWriter)) {

            for (String bv : vidPool) {
                bufferedWriter.write(bv);
                bufferedWriter.newLine();
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void loadVideos()
            throws IOException {

        Path path = Path.of(filename);
        if (Files.notExists(path)) {
            Files.createFile(path);
            return;
        }

        try (FileReader fileReader = new FileReader(filename);
             BufferedReader bufferedReader = new BufferedReader(fileReader)) {

            vidPool.clear();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                vidPool.add(line);
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
