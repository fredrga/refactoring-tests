package com.tidal.refactoring.playlist;

import com.tidal.refactoring.playlist.dao.PlaylistDaoBean;
import com.tidal.refactoring.playlist.data.PlayList;
import com.tidal.refactoring.playlist.data.PlayListTrack;
import com.tidal.refactoring.playlist.data.Track;
import com.tidal.refactoring.playlist.exception.PlaylistException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import java.util.HashSet;
import java.util.List;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class PlaylistBusinessBeanTest {

    @Mock
    private PlaylistDaoBean playlistDaoBeanMock;

    @InjectMocks
    PlaylistBusinessBean playlistBusinessBean;

    @BeforeMethod
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void unexpectedExceptionShouldBeThrownAsGenericError() {
        when(playlistDaoBeanMock.getPlaylistByUUID(anyString())).thenThrow(new IllegalStateException("test"));

        assertThatThrownBy(
                () -> playlistBusinessBean.addTracks("uuid", singletonList(new Track()), 1))
                .isInstanceOf(PlaylistException.class)
                .hasMessage("Generic error");
    }

    @Test
    public void exceedingMaxShouldNotBeAllowed() {
        PlayList fullPlaylist = mock(PlayList.class);
        when(fullPlaylist.getNrOfTracks()).thenReturn(PlaylistBusinessBean.MAX_NUMBER);
        when(playlistDaoBeanMock.getPlaylistByUUID(anyString())).thenReturn(fullPlaylist);

        assertThatThrownBy(
                () -> playlistBusinessBean.addTracks("uuid", singletonList(new Track()), 1))
                .isInstanceOf(PlaylistException.class)
                .hasMessage("Playlist cannot have more than " + PlaylistBusinessBean.MAX_NUMBER + " tracks");
    }

    @Test
    public void incorrectIndexShouldBeRejected() {
        PlayList fullPlaylist = mock(PlayList.class);
        when(fullPlaylist.getNrOfTracks()).thenReturn(3);
        when(playlistDaoBeanMock.getPlaylistByUUID(anyString())).thenReturn(fullPlaylist);

        assertThatThrownBy(
                () -> playlistBusinessBean.addTracks("uuid", singletonList(new Track()), 5))
                .isInstanceOf(PlaylistException.class)
                .hasMessage("5 in not valid index in a playlist of 3 tracks.");
    }

    @Test
    public void negativeIndexShouldBeRejected() {
        PlayList playList = mock(PlayList.class);
        when(playList.getNrOfTracks()).thenReturn(3);
        when(playlistDaoBeanMock.getPlaylistByUUID(anyString())).thenReturn(playList);

        assertThatThrownBy(
                () -> playlistBusinessBean.addTracks("uuid", singletonList(new Track()), -1))
                .isInstanceOf(PlaylistException.class)
                .hasMessage("-1 in not valid index in a playlist of 3 tracks.");
    }

    @Test
    public void addingTracksToEmptyPlaylistShouldReturnAddedTracks() {
        PlayList emptyPlaylist = mock(PlayList.class);
        when(emptyPlaylist.getNrOfTracks()).thenReturn(0);
        when(emptyPlaylist.getPlayListTracks()).thenReturn(new HashSet<>());
        when(playlistDaoBeanMock.getPlaylistByUUID(anyString())).thenReturn(emptyPlaylist);

        List<PlayListTrack> addedTracks = playlistBusinessBean.addTracks(
                "uuid", asList(
                        createTrack("title1", 1.42f, 314, 1),
                        createTrack("title2", 1.43f, 315, 2)),
                0);

        assertThat(addedTracks)
                .hasSize(2)
                .extracting("index", "track.id")
                .containsOnly(
                        tuple(0, 1),
                        tuple(1, 2));
    }

    @Test
    public void addingTracksToExistingPlaylistShouldReturnAddedTracks() {
        PlayList playList = mock(PlayList.class);
        when(playList.getNrOfTracks()).thenReturn(2);
        when(playList.getPlayListTracks()).thenReturn(new HashSet<>(asList(
                createTrack(0, createTrack("title1", 1.42f, 314, 1)),
                createTrack(1, createTrack("title2", 1.43f, 315, 2)))));
        when(playlistDaoBeanMock.getPlaylistByUUID(anyString())).thenReturn(playList);

        List<PlayListTrack> addedTracks = playlistBusinessBean.addTracks(
                "uuid", asList(
                        createTrack("title1", 1.42f, 314, 1),
                        createTrack("title2", 1.43f, 315, 2)),
                1);

        assertThat(addedTracks)
                .hasSize(2)
                .extracting("index", "track.id")
                .containsOnly(
                        tuple(1, 1),
                        tuple(2, 2));
    }

    @Test
    public void addingTracksToPlaylistShouldWriteAllPlaylistTrackFields() {
        PlayList emptyPlaylist = mock(PlayList.class);
        when(emptyPlaylist.getId()).thenReturn(1);
        when(emptyPlaylist.getNrOfTracks()).thenReturn(0);
        when(emptyPlaylist.getPlayListTracks()).thenReturn(new HashSet<>());
        when(playlistDaoBeanMock.getPlaylistByUUID(anyString())).thenReturn(emptyPlaylist);

        List<PlayListTrack> addedTracks = playlistBusinessBean.addTracks(
                "uuid", singletonList(createTrack("title1", 1.42f, 314, 1)), 0);

        assertThat(addedTracks)
                .hasSize(1)
                .extracting("id", "playlist.id", "index", "track.id")
                .containsExactly(tuple(null, 1, 0,  1)); // TODO: id should not be null!
    }

    // TODO: more tests should be written when the Dao is updated with methods for saving updates!


    private Track createTrack(String title, float duration, int artistId, int id) {
        Track track = new Track();
        track.setTitle(title);
        track.setDuration(duration);
        track.setArtistId(artistId);
        track.setId(id);
        return track;
    }

    private PlayListTrack createTrack(int index, Track track) {
        PlayListTrack playListTrack = new PlayListTrack();
        playListTrack.setIndex(index);
        playListTrack.setTrack(track);
        return playListTrack;
    }
}