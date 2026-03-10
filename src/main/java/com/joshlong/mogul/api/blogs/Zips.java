
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.util.FileCopyUtils;
import org.springframework.util.StreamUtils;
import org.yaml.snakeyaml.Yaml;

Logger log = LoggerFactory.getLogger(getClass());


enum ZipTypes {
    ZIP, TGZ;

    static ZipTypes type(File file) {
        Assert.notNull(file, "file is null");
        Assert.state(file.exists(), "the file [" + file.getAbsolutePath() + "] does not exist");
        var n = file.getName().toLowerCase(Locale.ROOT);
        if (n.endsWith(".tgz") || (n).endsWith(".tar.gz"))
            return ZipTypes.TGZ;
        if (n.endsWith(".zip"))
            return ZipTypes.ZIP;
        throw new IllegalArgumentException("unknown file type [" + file.getName() + "]");
    }
}

void main() {

    var zipExtractor = (ArchiveExtractor) this::extractZip;
    var tarExtractor = (ArchiveExtractor) this::extractTgz;
    var extractors = Map.of(ZipTypes.ZIP, zipExtractor, ZipTypes.TGZ, tarExtractor);
    var file = new File(new File(System.getenv("HOME"), "Desktop"), "in.tgz");
    Assert.state(file.exists(), "the file [" + file.getAbsolutePath() + "] does not exist");
    var destination = new File(file.getParentFile(), "extracted");
    Assert.state(destination.exists() || destination.mkdirs(), "the directory" +
            " [" + destination.getAbsolutePath() + "] does not exist");
    var archiveExtractor = extractors.get(ZipTypes.type(file));
    try (var fin = new FileInputStream(file)) {
        this.run(fin, archiveExtractor, destination);
    } //
    catch (Throwable throwable) {
        log.warn("failed to ingest blog posts",
                throwable);
    }
}


record ZipFile(String fileName, byte[] content) {
}

interface ArchiveExtractor {

    void extract(InputStream stream, Consumer<ZipFile> zipFileConsumer) throws Exception;
}

void run(InputStream inputStream, ArchiveExtractor archiveExtractor,
         File destination) throws Exception {
    log.info("being asked to run and extract out to {}", destination.getAbsolutePath());
    archiveExtractor.extract(inputStream, zipFile -> {
        log.info("extracted {}", zipFile.fileName());
    });
}

void extractTgz(InputStream stream, Consumer<ZipFile> entryConsumer) throws Exception {
    try (var gzi = new GzipCompressorInputStream(stream);
         var tar = new TarArchiveInputStream(gzi)) {
        var entry = (TarArchiveEntry) null;
        while ((entry = tar.getNextEntry()) != null) {
            try (var byteArrayOutputStream = new ByteArrayOutputStream()) {
                StreamUtils.copy(tar, byteArrayOutputStream);
                entryConsumer.accept(new ZipFile(
                        entry.getName(),
                        byteArrayOutputStream.toByteArray()));
            }
        }
    }
}

void extractZip(InputStream stream, Consumer<ZipFile> entryConsumer) throws IOException {
    try (var zis = new ZipInputStream(stream)) {
        var entry = (ZipEntry) null;
        while ((entry = zis.getNextEntry()) != null) {
            try (var baos = new ByteArrayOutputStream();) {
                StreamUtils.copy(zis, baos);
                zis.closeEntry();
                entryConsumer.accept(new ZipFile(
                        entry.getName(),
                        baos.toByteArray()));
            }
        }
    }
}


static class FrontMatter {

    private static Map<String, Object> parse(String content) {
        if (!content.startsWith("---")) return Map.of();

        var end = content.indexOf("---", 3);
        if (end == -1) return Map.of();

        var yaml = content.substring(3, end).trim();
        return new Yaml().load(yaml);
    }

    private static String body(String content) {
        if (!content.startsWith("---")) return content;
        var end = content.indexOf("---", 3);
        return end == -1 ? content : content.substring(end + 3).trim();
    }
}

boolean isMarkdownFile(String path) {
    return path.toLowerCase(Locale.ROOT).endsWith(".md");
}

boolean isMarkdownFile(Path path) {
    return isMarkdownFile(path.toFile().getName());
}

private void process(Path markdown) throws Exception {
    this.log.info("processing {}", markdown.toFile().getAbsolutePath());
    var content = Files.readString(markdown);
    var frontMatter = FrontMatter.parse(content);
    var body = FrontMatter.body(content);
    IO.println("frontMatter = " + frontMatter);
    IO.println("body = " + body);

}
