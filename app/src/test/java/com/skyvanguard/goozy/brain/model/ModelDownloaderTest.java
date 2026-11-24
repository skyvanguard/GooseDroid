package com.skyvanguard.goozy.brain.model;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;

import static org.junit.Assert.*;

public class ModelDownloaderTest {
    private static final int MODEL_SIZE = 200_000;

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private MockWebServer server;
    private File directory;
    private byte[] content;
    private LocalModel model;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        directory = new File(folder.getRoot(), "models");
        content = new byte[MODEL_SIZE];
        for (int i = 0; i < content.length; i++) {
            content[i] = (byte) (i % 251);
        }
        model = modelOfSize(MODEL_SIZE);
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    private LocalModel modelOfSize(long size) {
        return new LocalModel("test", "Test", "", "", "model.litertlm",
                "http://127.0.0.1:" + server.getPort() + "/model.litertlm", size);
    }

    private static MockResponse bytes(int status, byte[] data, int from) {
        Buffer buffer = new Buffer();
        buffer.write(data, from, data.length - from);
        return new MockResponse().setResponseCode(status).setBody(buffer);
    }

    private static ModelDownloader.Listener ignore() {
        return (downloaded, total) -> { };
    }

    @Test
    public void download_writesTheFileAndLeavesNoPartFile() throws Exception {
        server.enqueue(bytes(200, content, 0));

        ModelDownloader.Result result =
                new ModelDownloader().download(directory, model, ignore());

        assertEquals(ModelDownloader.Result.COMPLETED, result);
        File file = new File(directory, model.fileName);
        assertArrayEquals(content, Files.readAllBytes(file.toPath()));
        assertFalse(new File(directory, model.fileName + ".part").exists());
        assertTrue(ModelDownloader.isDownloaded(directory, model));
    }

    @Test
    public void download_reportsProgressEndingAtTheTotal() throws Exception {
        byte[] big = new byte[2 * 1024 * 1024];
        LocalModel bigModel = modelOfSize(big.length);
        server.enqueue(bytes(200, big, 0));
        List<Long> progress = new ArrayList<>();

        new ModelDownloader().download(directory, bigModel,
                (downloaded, total) -> progress.add(downloaded));

        assertTrue(progress.size() >= 2);
        assertEquals(Long.valueOf(big.length), progress.get(progress.size() - 1));
        for (int i = 1; i < progress.size(); i++) {
            assertTrue(progress.get(i) >= progress.get(i - 1));
        }
    }

    @Test
    public void download_resumesFromThePartFile() throws Exception {
        int alreadyHave = 80_000;
        assertTrue(directory.mkdirs());
        try (FileOutputStream out =
                     new FileOutputStream(new File(directory, model.fileName + ".part"))) {
            out.write(content, 0, alreadyHave);
        }
        server.enqueue(bytes(206, content, alreadyHave));

        new ModelDownloader().download(directory, model, ignore());

        RecordedRequest request = server.takeRequest();
        assertEquals("bytes=" + alreadyHave + "-", request.getHeader("Range"));
        assertArrayEquals(content,
                Files.readAllBytes(new File(directory, model.fileName).toPath()));
        assertEquals(alreadyHave, ModelDownloader.getPartialBytes(directory, model) + alreadyHave);
    }

    @Test
    public void download_serverIgnoringRange_startsOver() throws Exception {
        assertTrue(directory.mkdirs());
        try (FileOutputStream out =
                     new FileOutputStream(new File(directory, model.fileName + ".part"))) {
            out.write(new byte[50_000]);
        }
        server.enqueue(bytes(200, content, 0));

        new ModelDownloader().download(directory, model, ignore());

        assertArrayEquals(content,
                Files.readAllBytes(new File(directory, model.fileName).toPath()));
    }

    @Test
    public void download_alreadyComplete_doesNotTouchTheNetwork() throws Exception {
        assertTrue(directory.mkdirs());
        try (FileOutputStream out = new FileOutputStream(new File(directory, model.fileName))) {
            out.write(content);
        }

        ModelDownloader.Result result =
                new ModelDownloader().download(directory, model, ignore());

        assertEquals(ModelDownloader.Result.COMPLETED, result);
        assertEquals(0, server.getRequestCount());
    }

    @Test
    public void download_wrongSize_failsAndDiscardsTheFile() throws Exception {
        server.enqueue(bytes(200, new byte[1234], 0));

        try {
            new ModelDownloader().download(directory, model, ignore());
            fail("debía fallar por tamaño incorrecto");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("1234"));
        }

        assertFalse(new File(directory, model.fileName).exists());
        assertFalse(new File(directory, model.fileName + ".part").exists());
        assertFalse(ModelDownloader.isDownloaded(directory, model));
    }

    @Test
    public void download_serverError_fails() {
        server.enqueue(new MockResponse().setResponseCode(404));

        try {
            new ModelDownloader().download(directory, model, ignore());
            fail("debía fallar con 404");
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("404"));
        }
    }

    @Test
    public void cancel_stopsAndKeepsThePartFileForLater() throws Exception {
        byte[] big = new byte[4 * 1024 * 1024];
        LocalModel bigModel = modelOfSize(big.length);
        server.enqueue(bytes(200, big, 0));
        ModelDownloader downloader = new ModelDownloader();

        ModelDownloader.Result result = downloader.download(directory, bigModel,
                (downloaded, total) -> downloader.cancel());

        assertEquals(ModelDownloader.Result.CANCELLED, result);
        assertFalse(new File(directory, bigModel.fileName).exists());
        long partial = ModelDownloader.getPartialBytes(directory, bigModel);
        assertTrue(partial > 0 && partial < big.length);
    }

    @Test
    public void isDownloaded_requiresTheExactSize() throws Exception {
        assertTrue(directory.mkdirs());
        try (FileOutputStream out = new FileOutputStream(new File(directory, model.fileName))) {
            out.write(content, 0, MODEL_SIZE - 1);
        }

        assertFalse(ModelDownloader.isDownloaded(directory, model));
    }

    @Test
    public void delete_removesFileAndPartFile() throws Exception {
        assertTrue(directory.mkdirs());
        File file = new File(directory, model.fileName);
        File part = new File(directory, model.fileName + ".part");
        assertTrue(file.createNewFile());
        assertTrue(part.createNewFile());

        assertTrue(ModelDownloader.delete(directory, model));

        assertFalse(file.exists());
        assertFalse(part.exists());
    }

    @Test
    public void catalog_hasUniqueIdsAndFileNames() {
        List<String> ids = new ArrayList<>();
        List<String> files = new ArrayList<>();
        for (LocalModel entry : LocalModelCatalog.getModels()) {
            assertFalse(ids.contains(entry.id));
            assertFalse(files.contains(entry.fileName));
            assertTrue(entry.downloadUrl.startsWith("https://"));
            assertTrue(entry.downloadUrl.endsWith(entry.fileName));
            assertTrue(entry.sizeBytes > 0);
            ids.add(entry.id);
            files.add(entry.fileName);
        }
        assertNotNull(LocalModelCatalog.find("lfm25_12b"));
        assertNull(LocalModelCatalog.find("no-existe"));
    }
}
