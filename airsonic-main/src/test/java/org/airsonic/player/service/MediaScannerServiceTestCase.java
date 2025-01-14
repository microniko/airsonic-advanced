package org.airsonic.player.service;

import com.codahale.metrics.ConsoleReporter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;
import com.google.common.io.Resources;
import org.airsonic.player.TestCaseUtils;
import org.airsonic.player.api.ScanningTestUtils;
import org.airsonic.player.dao.*;
import org.airsonic.player.domain.Album;
import org.airsonic.player.domain.Artist;
import org.airsonic.player.domain.MediaFile;
import org.airsonic.player.domain.MusicFolder;
import org.airsonic.player.domain.MusicFolder.Type;
import org.airsonic.player.util.HomeRule;
import org.airsonic.player.util.MusicFolderTestData;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * A unit test class to test the MediaScannerService.
 * <p>
 * This class uses the Spring application context configuration present in the
 * /org/airsonic/player/service/mediaScannerServiceTestCase/ directory.
 * <p>
 * The media library is found in the /MEDIAS directory.
 * It is composed of 2 musicFolders (Music and Music2) and several little weight audio files.
 * <p>
 * At runtime, the subsonic_home dir is set to target/test-classes/org/airsonic/player/service/mediaScannerServiceTestCase.
 * An empty database is created on the fly.
 */
@RunWith(SpringRunner.class)
@TestPropertySource(properties = "airsonic.cue.enabled=true")
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
public class MediaScannerServiceTestCase {

    @ClassRule
    public static final HomeRule airsonicRule = new HomeRule();

    private final MetricRegistry metrics = new MetricRegistry();

    @Autowired
    private MediaScannerService mediaScannerService;

    @Autowired
    private MediaFileDao mediaFileDao;

    @Autowired
    private MusicFolderDao musicFolderDao;

    @Autowired
    private MediaFileService mediaFileService;

    @Autowired
    private ArtistDao artistDao;

    @Autowired
    private AlbumDao albumDao;

    @Autowired
    private MediaFolderService mediaFolderService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @SpyBean
    private SettingsService settingsService;

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    private UUID cleanupId = null;

    @Before
    public void setup() {
        jdbcTemplate.execute("DELETE FROM media_file");
        jdbcTemplate.execute("DELETE FROM album");
        jdbcTemplate.execute("DELETE FROM artist");
        TestCaseUtils.waitForScanFinish(mediaScannerService);
    }

    @After
    public void cleanup() {
        if (cleanupId != null) {
            ScanningTestUtils.after(cleanupId, mediaFolderService);
            cleanupId = null;
        }
    }

    /**
     * Tests the MediaScannerService by scanning the test media library into an empty database.
     */
    @Test
    public void testScanLibrary() {
        Timer globalTimer = metrics.timer(MetricRegistry.name(MediaScannerServiceTestCase.class, "Timer.global"));

        Timer.Context globalTimerContext = globalTimer.time();
        List<MusicFolder> testFolders = MusicFolderTestData.getTestMusicFolders();
        cleanupId = ScanningTestUtils.before(testFolders, mediaFolderService, mediaScannerService);
        globalTimerContext.stop();

        // Music Folder Music must have 3 children
        List<MediaFile> listeMusicChildren = mediaFileDao.getChildrenOf("", testFolders.get(0).getId(), false);
        Assert.assertEquals(3, listeMusicChildren.size());
        // Music Folder Music2 must have 1 children
        List<MediaFile> listeMusic2Children = mediaFileDao.getChildrenOf("", testFolders.get(1).getId(), false);
        Assert.assertEquals(1, listeMusic2Children.size());

        System.out.println("--- List of all artists ---");
        System.out.println("artistName#albumCount");
        List<Artist> allArtists = artistDao.getAlphabetialArtists(0, Integer.MAX_VALUE, testFolders);
        allArtists.forEach(artist -> System.out.println(artist.getName() + "#" + artist.getAlbumCount()));
        System.out.println("--- *********************** ---");

        System.out.println("--- List of all albums ---");
        System.out.println("name#artist");
        List<Album> allAlbums = albumDao.getAlphabeticalAlbums(0, Integer.MAX_VALUE, true, true, testFolders);
        allAlbums.forEach(album -> System.out.println(album.getName() + "#" + album.getArtist()));
        Assert.assertEquals(5, allAlbums.size());
        System.out.println("--- *********************** ---");

        List<MediaFile> listeSongs = mediaFileDao.getSongsByGenre("Baroque Instrumental", 0, Integer.MAX_VALUE, testFolders);
        Assert.assertEquals(2, listeSongs.size());

        // display out metrics report
        ConsoleReporter reporter = ConsoleReporter.forRegistry(metrics)
                .convertRatesTo(TimeUnit.SECONDS)
                .convertDurationsTo(TimeUnit.MILLISECONDS)
                .build();
        reporter.report();

        System.out.print("End");
    }

    @Test
    public void testSpecialCharactersInFilename() throws Exception {
        String directoryName = "Muff1nman\u2019s \uFF0FMusic";
        String fileName = "Muff1nman\u2019s\uFF0FPiano.mp3";
        Path artistDir = temporaryFolder.newFolder(directoryName).toPath();
        Path musicFile = artistDir.resolve(fileName);
        Files.copy(Paths.get(Resources.getResource("MEDIAS/piano.mp3").toURI()), musicFile);

        MusicFolder musicFolder = new MusicFolder(1, temporaryFolder.getRoot().toPath(), "MusicSpecial", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        cleanupId = ScanningTestUtils.before(Arrays.asList(musicFolder), mediaFolderService, mediaScannerService);
        MediaFile mediaFile = mediaFileService.getMediaFile(musicFile);
        assertEquals(mediaFile.getRelativePath(), temporaryFolder.getRoot().toPath().relativize(musicFile));
        assertThat(mediaFile.getFolderId()).isEqualTo(musicFolder.getId());
        MediaFile relativeMediaFile = mediaFileService.getMediaFile(temporaryFolder.getRoot().toPath().relativize(musicFile), musicFolder);
        assertEquals(relativeMediaFile.getRelativePath(), mediaFile.getRelativePath());
    }

    @Test
    public void testNeverScanned() {
        mediaScannerService.neverScanned();
    }

    @Test
    public void testMusicBrainzReleaseIdTag() {

        // Add the "Music3" folder to the database
        Path musicFolderFile = MusicFolderTestData.resolveMusic3FolderPath();
        MusicFolder musicFolder = new MusicFolder(1, musicFolderFile, "Music3", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        cleanupId = ScanningTestUtils.before(Arrays.asList(musicFolder), mediaFolderService, mediaScannerService);

        // Retrieve the "Music3" folder from the database to make
        // sure that we don't accidentally operate on other folders
        // from previous tests.
        musicFolder = musicFolderDao.getMusicFolderForPath(musicFolder.getPath().toString());
        List<MusicFolder> folders = new ArrayList<>();
        folders.add(musicFolder);

        // Test that the artist is correctly imported
        List<Artist> allArtists = artistDao.getAlphabetialArtists(0, Integer.MAX_VALUE, folders);
        Assert.assertEquals(1, allArtists.size());
        Artist artist = allArtists.get(0);
        Assert.assertEquals("TestMusic3Artist", artist.getName());
        Assert.assertEquals(1, artist.getAlbumCount());

        // Test that the album is correctly imported, along with its MusicBrainz release ID
        List<Album> allAlbums = albumDao.getAlphabeticalAlbums(0, Integer.MAX_VALUE, true, true, folders);
        Assert.assertEquals(1, allAlbums.size());
        Album album = allAlbums.get(0);
        Assert.assertEquals("TestAlbum", album.getName());
        Assert.assertEquals("TestMusic3Artist", album.getArtist());
        Assert.assertEquals(1, album.getSongCount());
        Assert.assertEquals("0820752d-1043-4572-ab36-2df3b5cc15fa", album.getMusicBrainzReleaseId());
        Assert.assertEquals("TestAlbum", album.getPath());

        // Test that the music file is correctly imported, along with its MusicBrainz release ID and recording ID
        List<MediaFile> albumFiles = mediaFileDao.getChildrenOf(allAlbums.get(0).getPath(), allAlbums.get(0).getFolderId(), false);
        Assert.assertEquals(1, albumFiles.size());
        MediaFile file = albumFiles.get(0);
        Assert.assertEquals("Aria", file.getTitle());
        Assert.assertEquals("flac", file.getFormat());
        Assert.assertEquals("TestAlbum", file.getAlbumName());
        Assert.assertEquals("TestMusic3Artist", file.getArtist());
        Assert.assertEquals("TestMusic3Artist", file.getAlbumArtist());
        Assert.assertEquals(1, (long)file.getTrackNumber());
        Assert.assertEquals(2001, (long)file.getYear());
        Assert.assertEquals(album.getPath(), file.getParentPath());
        Assert.assertEquals(Paths.get(album.getPath()).resolve("01 - Aria.flac").toString(), file.getPath());
        Assert.assertEquals("0820752d-1043-4572-ab36-2df3b5cc15fa", file.getMusicBrainzReleaseId());
        Assert.assertEquals("831586f4-56f9-4785-ac91-447ae20af633", file.getMusicBrainzRecordingId());
        assertEquals(-1.0d, file.getStartPosition(), 0.0d);
    }

    @Test
    public void testMusicCue() {

        // Add the "cue" folder to the database
        Path musicFolderFile = MusicFolderTestData.resolveMusicCueFolderPath();
        MusicFolder musicFolder = new MusicFolder(1, musicFolderFile, "Cue", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        cleanupId = ScanningTestUtils.before(Arrays.asList(musicFolder), mediaFolderService, mediaScannerService);

        // Retrieve the "Cue" folder from the database to make
        // sure that we don't accidentally operate on other folders
        // from previous tests.
        musicFolder = musicFolderDao.getMusicFolderForPath(musicFolder.getPath().toString());
        List<MusicFolder> folders = new ArrayList<>();
        folders.add(musicFolder);

        // Test that the artist is correctly imported
        List<Artist> allArtists = artistDao.getAlphabetialArtists(0, Integer.MAX_VALUE, folders);
        assertEquals(1, allArtists.size());
        Artist artist = allArtists.get(0);
        assertEquals("TestCueArtist", artist.getName());
        assertEquals(1, artist.getAlbumCount());


        // Test that the album is correctly imported
        List<Album> allAlbums = albumDao.getAlphabeticalAlbums(0, Integer.MAX_VALUE, true, true, folders);
        assertEquals(1, allAlbums.size());
        Album album = allAlbums.get(0);
        assertEquals("AirsonicTest", album.getName());
        assertEquals("TestCueArtist", album.getArtist());
        assertEquals(2, album.getSongCount());

        // Test that the music file is correctly imported
        List<MediaFile> albumFiles = mediaFileDao.getChildrenOf(allAlbums.get(0).getPath(), allAlbums.get(0).getFolderId(), false);
        Assert.assertEquals(3, albumFiles.size());
        MediaFile file = albumFiles.get(0);
        assertEquals("airsonic-test", file.getTitle());
        assertEquals("wav", file.getFormat());
        assertNull(file.getAlbumName());
        assertNull(file.getArtist());
        assertNull(file.getAlbumArtist());
        assertNull(file.getTrackNumber());
        assertNull(file.getYear());
        assertEquals(album.getPath(), file.getParentPath());
        assertEquals(Paths.get(album.getPath()).resolve("airsonic-test.wav").toString(), file.getPath());
        assertTrue(file.getIndexPath().contains("airsonic-test.cue"));
        assertEquals(-1.0d, file.getStartPosition(), 0.0d);

        MediaFile track1 = albumFiles.get(1);
        assertEquals("Handel", track1.getTitle());
        assertEquals("wav", track1.getFormat());
        assertEquals(track1.getAlbumName(), "AirsonicTest");
        assertEquals("Beecham", track1.getArtist());
        assertEquals("TestCueArtist", track1.getAlbumArtist());
        assertEquals(1L, (long)track1.getTrackNumber());
        assertNull(track1.getYear());
        assertEquals(album.getPath(), track1.getParentPath());
        assertEquals(Paths.get(album.getPath()).resolve("airsonic-test.wav").toString(), track1.getPath());
        assertNull(track1.getIndexPath());
        assertEquals(0.0d, track1.getStartPosition(), 0.0d);
    }

    @Test
    public void testMusicCueWithDisableCueIndexing() {

        when(settingsService.getEnableCueIndexing()).thenReturn(false);

        // Add the "cue" folder to the database
        Path musicFolderFile = MusicFolderTestData.resolveMusicDisableCueFolderPath();
        MusicFolder musicFolder = new MusicFolder(1, musicFolderFile, "CueDisabled", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        cleanupId = ScanningTestUtils.before(Arrays.asList(musicFolder), mediaFolderService, mediaScannerService);

        // Retrieve the "Cue" folder from the database to make
        // sure that we don't accidentally operate on other folders
        // from previous tests.
        musicFolder = musicFolderDao.getMusicFolderForPath(musicFolder.getPath().toString());
        List<MusicFolder> folders = new ArrayList<>();
        folders.add(musicFolder);

        // Test that the artist is correctly imported
        List<Artist> allArtists = artistDao.getAlphabetialArtists(0, Integer.MAX_VALUE, folders);
        assertEquals(0, allArtists.size());

        // Test that the album is correctly imported
        List<Album> allAlbums = albumDao.getAlphabeticalAlbums(0, Integer.MAX_VALUE, true, true, folders);
        assertEquals(0, allAlbums.size());

        // Test that the music file is correctly imported
        List<MediaFile> albumFiles = mediaFileDao.getChildrenOf("", musicFolder.getId(), false);
        Assert.assertEquals(1, albumFiles.size());
        MediaFile file = albumFiles.get(0);
        assertEquals("airsonic-test", file.getTitle());
        assertEquals("wav", file.getFormat());
        assertNull(file.getAlbumName());
        assertNull(file.getArtist());
        assertNull(file.getAlbumArtist());
        assertNull(file.getTrackNumber());
        assertNull(file.getYear());
        assertEquals("", file.getParentPath());
        assertEquals("airsonic-test.wav", file.getPath());
        assertNull(file.getIndexPath());
        assertEquals(-1.0d, file.getStartPosition(), 0.0d);

    }



    @Test
    public void testMusicInvalidCueWithLengthError() {

        // Add the "cue" folder to the database
        Path musicFolderFile = MusicFolderTestData.resolveMusicInvalidCue2FolderPath();
        MusicFolder musicFolder = new MusicFolder(1, musicFolderFile, "InvalidCue2", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        cleanupId = ScanningTestUtils.before(Arrays.asList(musicFolder), mediaFolderService, mediaScannerService);

        // Retrieve the "Cue" folder from the database to make
        // sure that we don't accidentally operate on other folders
        // from previous tests.
        musicFolder = musicFolderDao.getMusicFolderForPath(musicFolder.getPath().toString());
        List<MusicFolder> folders = new ArrayList<>();
        folders.add(musicFolder);

        // Test that the artist is correctly imported
        List<Artist> allArtists = artistDao.getAlphabetialArtists(0, Integer.MAX_VALUE, folders);
        assertEquals(0, allArtists.size());


        // Test that the album is correctly imported
        List<Album> allAlbums = albumDao.getAlphabeticalAlbums(0, Integer.MAX_VALUE, true, true, folders);
        assertEquals(0, allAlbums.size());

        // Test that the music file is correctly imported
        List<MediaFile> albumFiles = mediaFileDao.getChildrenOf("", folders.get(0).getId(), false);
        Assert.assertEquals(1, albumFiles.size());
        MediaFile file = albumFiles.get(0);
        assertEquals("airsonic-test", file.getTitle());
        assertEquals("wav", file.getFormat());
        assertNull(file.getAlbumName());
        assertNull(file.getArtist());
        assertNull(file.getAlbumArtist());
        assertNull(file.getTrackNumber());
        assertNull(file.getYear());
        assertEquals("", file.getParentPath());
        assertEquals("airsonic-test.wav", file.getPath());
        assertNull(file.getIndexPath());
        assertEquals(-1.0d, file.getStartPosition(), 0.0d);
    }


    @Test
    public void testMusicInvalidCueWithWarning() {

        // Add the "cue" folder to the database
        Path musicFolderFile = MusicFolderTestData.resolveMusicInvalidCue3FolderPath();
        MusicFolder musicFolder = new MusicFolder(1, musicFolderFile, "InvalidCue3", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        cleanupId = ScanningTestUtils.before(Arrays.asList(musicFolder), mediaFolderService, mediaScannerService);

        // Retrieve the "Cue" folder from the database to make
        // sure that we don't accidentally operate on other folders
        // from previous tests.
        musicFolder = musicFolderDao.getMusicFolderForPath(musicFolder.getPath().toString());
        List<MusicFolder> folders = new ArrayList<>();
        folders.add(musicFolder);

        // Test that the artist is correctly imported
        List<Artist> allArtists = artistDao.getAlphabetialArtists(0, Integer.MAX_VALUE, folders);
        assertEquals(0, allArtists.size());


        // Test that the album is correctly imported
        List<Album> allAlbums = albumDao.getAlphabeticalAlbums(0, Integer.MAX_VALUE, true, true, folders);
        assertEquals(0, allAlbums.size());

        // Test that the music file is correctly imported
        List<MediaFile> albumFiles = mediaFileDao.getChildrenOf("", folders.get(0).getId(), false);
        Assert.assertEquals(1, albumFiles.size());
        MediaFile file = albumFiles.get(0);
        assertEquals("airsonic-test", file.getTitle());
        assertEquals("wav", file.getFormat());
        assertNull(file.getAlbumName());
        assertNull(file.getArtist());
        assertNull(file.getAlbumArtist());
        assertNull(file.getTrackNumber());
        assertNull(file.getYear());
        assertEquals("", file.getParentPath());
        assertEquals("airsonic-test.wav", file.getPath());
        assertNull(file.getIndexPath());
        assertEquals(-1.0d, file.getStartPosition(), 0.0d);
    }




    @Test
    public void testMusicWithCommmaFolderAndDuplicateBasenameAudio() {

        // Add the "Music4" folder to the database
        Path musicFolderFile = MusicFolderTestData.resolveMusic4FolderPath();
        MusicFolder musicFolder = new MusicFolder(1, musicFolderFile, "Music4", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        cleanupId = ScanningTestUtils.before(Arrays.asList(musicFolder), mediaFolderService, mediaScannerService);

        // Retrieve the "Music4" folder from the database to make
        // sure that we don't accidentally operate on other folders
        // from previous tests.
        musicFolder = musicFolderDao.getMusicFolderForPath(musicFolder.getPath().toString());
        List<MusicFolder> folders = new ArrayList<>();
        folders.add(musicFolder);

        List<MediaFile> listMusicChildren = mediaFileDao.getChildrenOf("", musicFolder.getId(), false);
        Assert.assertEquals(2, listMusicChildren.size());

        List<MediaFile> listDuplicateBaseNameFiles = mediaFileDao.getChildrenOf("a", musicFolder.getId(), false);
        Assert.assertEquals(2, listDuplicateBaseNameFiles.size());


    }

    @Test
    public void testMpcAudioTest() {

        // Add the "MusicMpc" folder to the database
        Path musicFolderFile = MusicFolderTestData.resolveMusicMpcFolderPath();
        MusicFolder musicFolder = new MusicFolder(1, musicFolderFile, "mpc", Type.MEDIA, true, Instant.now().truncatedTo(ChronoUnit.MICROS));
        cleanupId = ScanningTestUtils.before(Arrays.asList(musicFolder), mediaFolderService, mediaScannerService);

        // Retrieve the "Music4" folder from the database to make
        // sure that we don't accidentally operate on other folders
        // from previous tests.
        musicFolder = musicFolderDao.getMusicFolderForPath(musicFolder.getPath().toString());
        List<MusicFolder> folders = new ArrayList<>();
        folders.add(musicFolder);

        List<MediaFile> listMusicChildren = mediaFileDao.getChildrenOf("", musicFolder.getId(), false);
        Assert.assertEquals(1, listMusicChildren.size());

        assertTrue(listMusicChildren.get(0).getDuration() > 0.0);
    }
}
