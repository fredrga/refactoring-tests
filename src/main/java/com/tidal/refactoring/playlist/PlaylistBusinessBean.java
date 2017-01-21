package com.tidal.refactoring.playlist;

import com.google.inject.Inject;
import com.tidal.refactoring.playlist.dao.PlaylistDaoBean;
import com.tidal.refactoring.playlist.data.PlayList;
import com.tidal.refactoring.playlist.data.PlayListTrack;
import com.tidal.refactoring.playlist.data.Track;
import com.tidal.refactoring.playlist.exception.PlaylistException;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static java.util.stream.Collectors.toList;

public class PlaylistBusinessBean {

    static final int MAX_NUMBER = 500;

    private PlaylistDaoBean playlistDaoBean;

    @Inject
    private PlaylistBusinessBean(PlaylistDaoBean playlistDaoBean) {
        this.playlistDaoBean = playlistDaoBean;
    }

    /**
     * Add tracks to the index.
     */
    List<PlayListTrack> addTracks(String uuid, List<Track> tracksToAdd, int toIndex)
            throws PlaylistException {

        try {

            PlayList playList = playlistDaoBean.getPlaylistByUUID(uuid);

            //We do not allow > 500 tracks in new playlists
            if (playList.getNrOfTracks() + tracksToAdd.size() > MAX_NUMBER) {
                throw new PlaylistException("Playlist cannot have more than " + 500 + " tracks");
            }

            if (!validateIndexes(toIndex, playList.getNrOfTracks())) {
                throw new PlaylistException(
                        String.format("%s in not valid index in a playlist of %s tracks.",
                                toIndex, playList.getNrOfTracks()));
            }

            // Convert all new tracks to playlist tracks
            List<PlayListTrack> added = createPlayListTracks(playList, toIndex, tracksToAdd);

            // Increment all indices of old tracks after the new inserted tracks
            playList.getPlayListTracks().forEach(track -> {
                if (track.getIndex() >= toIndex) {
                    track.setIndex(track.getIndex() + added.size());
                }
            });

            // Add new tracks the existing ones, and mark the playlist as updated
            playList.getPlayListTracks().addAll(added);
            playList.setNrOfTracks(playList.getPlayListTracks().size());
            playList.setLastUpdated(new Date());

            // TODO: I assume that the Dao should be called to store the new and updated tracks, and the playlist
            // itself, but I haven't updated that as it wasn't specified.
            return added;

        } catch (PlaylistException e) {
            throw e;

        } catch (Exception e) {
            e.printStackTrace(); // Should be replaced with a proper logging framework
            throw new PlaylistException("Generic error");
        }
    }

    private List<PlayListTrack> createPlayListTracks(PlayList playList, int startIndex, List<Track> tracks) {
        AtomicInteger newIndex = new AtomicInteger(startIndex);
        return tracks.stream()
                .map(track -> {
                    // PlayListTrack should have a reasonable constructor, or a builder
                    PlayListTrack playListTrack = new PlayListTrack();
                    playListTrack.setId(null); // What should this be?
                    playListTrack.setTrackPlaylist(playList);
                    playListTrack.setIndex(newIndex.getAndIncrement());
                    playListTrack.setDateAdded(new Date()); // Should use JSR-310 type instead of Date
                    playListTrack.setTrackId(track.getId());
                    playListTrack.setTrack(track); // Do we need this as trackId is also stored in the playlist track?
                    addTrackDurationToPlaylist(playList, track);
                    return playListTrack;
                })
                .collect(toList());
    }

    /**
     * Remove the tracks from the playlist located at the sent indexes
     */
    List<PlayListTrack> removeTracks(String uuid, List<Integer> indices) throws PlaylistException {
        PlayList playList = playlistDaoBean.getPlaylistByUUID(uuid); // Assumes exception is thrown by Dao if playlist does not exist

        List<PlayListTrack> toBeRemoved = playList.getPlayListTracks().stream()
                .filter(track -> indices.contains(track.getIndex()))
                .collect(toList());

        playList.getPlayListTracks().removeAll(toBeRemoved);

        playList.getPlayListTracks().forEach(track -> decrementIndexAfterRemoval(track, indices));

        playList.setLastUpdated(new Date());
        playList.setNrOfTracks(playList.getPlayListTracks().size());
        subtractDurationFromPlaylist(
                playList,
                toBeRemoved.stream()
                        .map(PlayListTrack::getTrack)
                        .collect(toList()));

        return toBeRemoved;
    }

    private boolean validateIndexes(int toIndex, int length) {
        return toIndex >= 0 && toIndex <= length;
    }

    private void addTrackDurationToPlaylist(PlayList playList, Track track) {
        playList.setDuration(playList.getDuration() + track.getDuration());
    }

    private void subtractDurationFromPlaylist(PlayList playList, Collection<Track> tracks) {
        float durationOfRemoved = (float) tracks.stream()
                .mapToDouble(Track::getDuration)
                .sum();
        playList.setDuration(playList.getDuration() - durationOfRemoved);
    }

    private void decrementIndexAfterRemoval(PlayListTrack playListTrack, List<Integer> removedIndices) {
        // Decrement the given track's index by how many removed tracks have lower indices
        playListTrack.setIndex(playListTrack.getIndex() - (int) removedIndices.stream()
                .filter(removedIndex -> removedIndex < playListTrack.getIndex())
                .count());
    }
}
