package com.skyvanguard.goozy.brain;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.Assert.*;

public class BrainMemoryTest {

    @Rule
    public TemporaryFolder folder = new TemporaryFolder();

    private File file;
    private BrainMemory memory;

    @Before
    public void setUp() throws Exception {
        file = new File(folder.getRoot(), "brain/memory.json");
        memory = new BrainMemory(file);
    }

    @Test
    public void newMemory_isEmpty() {
        assertTrue(memory.getFacts().isEmpty());
        assertTrue(memory.getDiary().isEmpty());
    }

    @Test
    public void remember_persistsAcrossInstances() {
        assertTrue(memory.remember("Mi humano se llama Pedro"));

        List<String> facts = new BrainMemory(file).getFacts();

        assertEquals(1, facts.size());
        assertEquals("Mi humano se llama Pedro", facts.get(0));
    }

    @Test
    public void remember_duplicateMovesToMostRecentWithoutDuplicating() {
        memory.remember("Le gusta el mate");
        memory.remember("Trabaja de noche");
        memory.remember("  le gusta el MATE. ");

        List<String> facts = memory.getFacts();

        assertEquals(2, facts.size());
        assertEquals("Trabaja de noche", facts.get(0));
        assertEquals("le gusta el MATE.", facts.get(1));
    }

    @Test
    public void remember_sameFactInOtherWords_replacesTheOldOne() {
        memory.remember("A Pedro le gusta el tereré");
        memory.remember("Trabaja de noche");
        memory.remember("Pedro me gusta el tereré");

        List<String> facts = memory.getFacts();

        assertEquals(2, facts.size());
        assertEquals("Pedro me gusta el tereré", facts.get(1));
    }

    @Test
    public void isSameFact_distinguishesDifferentFacts() {
        assertTrue(BrainMemory.isSameFact("Le gusta el mate", "le gusta el MATE."));
        assertFalse(BrainMemory.isSameFact("Le gusta el mate", "Le gusta el tereré"));
        assertFalse(BrainMemory.isSameFact("Tiene 3 gatos", "Tiene 30 gatos"));
        assertFalse(BrainMemory.isSameFact("Mi humano se llama Pedro", "Pedro trabaja de noche"));
        assertFalse(BrainMemory.isSameFact("Pedro bebió tereré antes de dormir",
                "A Pedro le gusta el tereré"));
    }

    @Test
    public void remember_blankOrNull_isIgnored() {
        assertFalse(memory.remember(null));
        assertFalse(memory.remember("   "));
        assertTrue(memory.getFacts().isEmpty());
    }

    @Test
    public void remember_beyondCapacity_forgetsTheOldest() {
        for (int i = 0; i < BrainMemory.MAX_FACTS + 5; i++) {
            memory.remember("dato número " + i);
        }

        List<String> facts = memory.getFacts();

        assertEquals(BrainMemory.MAX_FACTS, facts.size());
        assertEquals("dato número 5", facts.get(0));
        assertEquals("dato número " + (BrainMemory.MAX_FACTS + 4), facts.get(facts.size() - 1));
    }

    @Test
    public void forget_removesTheFact() {
        memory.remember("uno");
        memory.remember("dos");

        assertTrue(memory.forget("uno"));
        assertFalse(memory.forget("no existe"));

        assertEquals(1, new BrainMemory(file).getFacts().size());
    }

    @Test
    public void writeDiary_oneEntryPerDay() {
        memory.writeDiary("2026-09-27", "Primer borrador");
        memory.writeDiary("2026-09-27", "Versión final");
        memory.writeDiary("2026-09-28", "Otro día");

        List<BrainMemory.DiaryEntry> diary = new BrainMemory(file).getDiary();

        assertEquals(2, diary.size());
        assertEquals("Versión final", diary.get(0).text);
        assertEquals("2026-09-28", diary.get(1).date);
        assertTrue(memory.hasDiaryEntryFor("2026-09-27"));
        assertFalse(memory.hasDiaryEntryFor("2026-09-29"));
    }

    @Test
    public void writeDiary_blankText_isIgnored() {
        assertFalse(memory.writeDiary("2026-09-27", "  "));
        assertFalse(memory.writeDiary(null, "texto"));
        assertTrue(memory.getDiary().isEmpty());
    }

    @Test
    public void corruptFile_startsEmptyAndRecoversOnNextSave() throws Exception {
        assertTrue(file.getParentFile().mkdirs());
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write("{esto no es json".getBytes(StandardCharsets.UTF_8));
        }

        BrainMemory recovered = new BrainMemory(file);

        assertTrue(recovered.getFacts().isEmpty());
        assertTrue(recovered.remember("dato nuevo"));
        assertEquals(1, new BrainMemory(file).getFacts().size());
    }

    @Test
    public void clear_removesEverything() {
        memory.remember("uno");
        memory.writeDiary("2026-09-27", "hoy");

        assertTrue(memory.clear());

        BrainMemory reloaded = new BrainMemory(file);
        assertTrue(reloaded.getFacts().isEmpty());
        assertTrue(reloaded.getDiary().isEmpty());
    }

    @Test
    public void save_leavesNoTemporaryFile() {
        memory.remember("uno");

        assertFalse(new File(file.getPath() + ".tmp").exists());
        assertTrue(file.exists());
    }
}
